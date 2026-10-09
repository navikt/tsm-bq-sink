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
            .addRow(sykmelding.sykmelding.id, sykmelding.toNasjonalRow())
            .build()

        val response = bigQuery.insertAll(request)
        check(!response.hasErrors()) { "BigQuery rejected the record: ${response.insertErrors}" }
        val row = requireNotNull(
            bigQuery.query(
                QueryJobConfiguration.newBuilder(
                    "SELECT * FROM `${DATASET_ID}.sykmeldinger` WHERE id = '${sykmelding.sykmelding.id}' LIMIT 1"
                ).build()
            ).values.firstOrNull()
        ) { "Inserted row not found in BigQuery" }

        val table = requireNotNull(bigQuery.getTable(SYKMELDINGER_TABLE)) { "Table $SYKMELDINGER_TABLE does not exist" }
        val schema =
            requireNotNull(table.getDefinition<StandardTableDefinition>().schema) { "Table $SYKMELDINGER_TABLE has no schema" }

        schema.fields.forEach { field ->
            println("${field.name} ${field.type} (${field.mode}) = ${row.get(field.name).value}")
            if (field.mode == Field.Mode.REPEATED) {
                field.subFields.forEach { sf ->
                    println("  ${sf.name} ${field.type} (${sf.mode}) = ${row.get(field.name).value}")
                }
            }
            if (field.mode == Field.Mode.REQUIRED) {
                requireNotNull(row.get(field.name).value) { "Required field ${field.name} is null in the inserted row" }
            }
        }
    }

    @Test
    fun `verify nullability on a utenlandsk insert`() {
        // Insert one sykmelding
        val sykmelding: SykmeldingRecord.Utenlandsk = testUtenlandsk("b8e9c32c-9a1e-4a85-8e67-e9284f5c9fd6")
        val request = InsertAllRequest.newBuilder(SYKMELDINGER_UTENLANDSK_TABLE)
            .addRow(sykmelding.sykmelding.id, sykmelding.toUtenlandskRow())
            .build()

        val response = bigQuery.insertAll(request)
        check(!response.hasErrors()) { "BigQuery rejected the record: ${response.insertErrors}" }
        val row = requireNotNull(
            bigQuery.query(
                QueryJobConfiguration.newBuilder(
                    "SELECT * FROM `${DATASET_ID}.sykmeldinger_utenlandsk` WHERE id = '${sykmelding.sykmelding.id}' LIMIT 1"
                ).build()
            ).values.firstOrNull()
        ) { "Inserted row not found in BigQuery" }

        val table =
            requireNotNull(bigQuery.getTable(SYKMELDINGER_UTENLANDSK_TABLE)) { "Table $SYKMELDINGER_UTENLANDSK_TABLE does not exist" }
        val schema =
            requireNotNull(table.getDefinition<StandardTableDefinition>().schema) { "Table $SYKMELDINGER_UTENLANDSK_TABLE has no schema" }

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
            hovedDiagnose = DiagnoseInfo(
                kode = "A01",
                system = DiagnoseSystem.ICPC2,
                tekst = "Test diagnose"
            ),
            biDiagnoser = listOf(
                DiagnoseInfo(
                    kode = "B02",
                    system = DiagnoseSystem.ICD10,
                    tekst = "Bi-diagnose test"
                )
            ),
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

private fun testUtenlandsk(id: String): SykmeldingRecord.Utenlandsk = SykmeldingRecord.Utenlandsk(
    metadata = MessageMetadata.Utenlandsk(
        land = "SE",
        journalPostId = "123122131",
    ),
    sykmelding = Sykmelding.Utenlandsk(
        id = id,
        metadata = SykmeldingMeta.Legacy(
            genDate = OffsetDateTime.now(),
            mottattDato = OffsetDateTime.now(),
            avsenderSystem = AvsenderSystem(navn = "Test System", versjon = "1.0.0"),
            behandletTidspunkt = OffsetDateTime.now(),
            regelsettVersjon = null,
            strekkode = null,
        ),
        utenlandskInfo = UtenlandskInfo(
            land = "SE",
            folkeRegistertAdresseErBrakkeEllerTilsvarende = false,
            erAdresseUtland = null
        ),
        pasient = Pasient(
            fnr = "12345678901",
            kontaktinfo = emptyList(),
            navn = null,
            navKontor = null,
            navnFastlege = null,
        ),
        medisinskVurdering = MedisinskVurdering.Legacy(
            hovedDiagnose = null,
            biDiagnoser = null,
            svangerskap = false,
            skjermetForPasient = false,
            yrkesskade = null,
            annenFraversArsak = null,
            syketilfelletStartDato = null,
        ),
        aktivitet = listOf(
            Aktivitet.Gradert(
                fom = OffsetDateTime.now().minusDays(10).toLocalDate(),
                tom = OffsetDateTime.now().minusDays(5).toLocalDate(),
                grad = 69,
                reisetilskudd = false
            )
        ),
    ),
    validation = ValidationResult(
        status = RuleType.OK,
        rules = emptyList(),
        timestamp = OffsetDateTime.now()
    )


)
