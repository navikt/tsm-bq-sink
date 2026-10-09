package no.nav.tsm.core.bq

import com.google.cloud.bigquery.Field
import com.google.cloud.bigquery.InsertAllRequest
import com.google.cloud.bigquery.QueryJobConfiguration
import com.google.cloud.bigquery.StandardTableDefinition
import no.nav.tsm.sykmelding.input.core.model.*
import no.nav.tsm.sykmelding.input.core.model.Pasient
import no.nav.tsm.sykmelding.input.core.model.Sykmelding.Digital
import no.nav.tsm.sykmelding.input.core.model.metadata.*
import no.nav.tsm.utils.BigQueryTestContainer
import org.junit.Test
import java.time.OffsetDateTime

class BigQuerySykmeldingerTest : BigQueryTestContainer() {

    @Test
    fun `verify nullability on a basic insert`() {
        // Insert one sykmelding
        val sykmelding = testSykmelding("b8e9c32c-9a1e-4a85-8e67-e9284f5c9fd6")
        val request = InsertAllRequest.newBuilder(SYKMELDINGER_TABLE)
            .addRow(sykmelding.sykmelding.id, sykmelding.toBigQueryRow())
            .build()

        val response = bigQuery.insertAll(request)
        check(!response.hasErrors()) { "BigQuery rejected the record: ${response.insertErrors}" }
        val row = requireNotNull(bigQuery.query(
            QueryJobConfiguration.newBuilder(
                "SELECT * FROM `${DATASET_ID}.sykmeldinger` WHERE id = '${sykmelding.sykmelding.id}' LIMIT 1"
            ).build()
        ).values.firstOrNull()) { "Inserted row not found in BigQuery" }

        val table = requireNotNull(bigQuery.getTable(SYKMELDINGER_TABLE)) { "Table $SYKMELDINGER_TABLE does not exist" }
        val schema =
            requireNotNull(table.getDefinition<StandardTableDefinition>().schema) { "Table $SYKMELDINGER_TABLE has no schema" }

        schema.fields.forEach { field ->
            if (field.mode == Field.Mode.REQUIRED) {
                requireNotNull(row.get(field.name).value) { "Required field ${field.name} is null in the inserted row" }
            }
        }
    }
}

private fun testSykmelding(id: String): SykmeldingRecord = SykmeldingRecord.Digital(
    metadata = MessageMetadata.Digital(orgnummer = "123456789"),
    sykmelding = Digital(
        id = id,
        metadata = SykmeldingMeta.Digital(
            genDate = OffsetDateTime.now(),
            mottattDato = OffsetDateTime.now(),
            avsenderSystem = AvsenderSystem(navn = "Test System", versjon = "1.0.0")
        ),
        pasient = Pasient(
            fnr = "12345678901",
            kontaktinfo = emptyList(),
            navn = null,
            navKontor = null,
            navnFastlege = null,
        ),
        medisinskVurdering = MedisinskVurdering.Digital(
            hovedDiagnose = null,
            biDiagnoser = null,
            svangerskap = false,
            skjermetForPasient = false,
            yrkesskade = null,
            annenFravarsgrunn = null,
        ),
        aktivitet = listOf(
            Aktivitet.Gradert(
                fom = OffsetDateTime.now().minusDays(10).toLocalDate(),
                tom = OffsetDateTime.now().minusDays(5).toLocalDate(),
                grad = 69,
                reisetilskudd = false
            )
        ),
        behandler = Behandler(
            navn = Navn(fornavn = "Ola", mellomnavn = null, etternavn = "Nordmann"),
            adresse = null,
            ids = listOf(
                PersonId(type = PersonIdType.HPR, id = "123456"),
                PersonId(type = PersonIdType.FNR, id = "23123213131")
            ),
            kontaktinfo = emptyList(),
        ),
        sykmelder = Sykmelder(
            ids = listOf(PersonId(type = PersonIdType.HPR, id = "123456")),
            helsepersonellKategori = HelsepersonellKategori.LEGE,
        ),
        arbeidsgiver = ArbeidsgiverInfo.Ingen(),
        tilbakedatering = null,
        bistandNav = null,
        utdypendeSporsmal = null,
        prognose = null,
    ),
    validation = ValidationResult(
        status = RuleType.OK,
        rules = emptyList(),
        timestamp = OffsetDateTime.now()
    )
)
