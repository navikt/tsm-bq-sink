package no.nav.tsm.kafka

import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.InsertAllRequest
import no.nav.tsm.core.Environment
import no.nav.tsm.core.bq.SYKMELDINGER_TABLE
import no.nav.tsm.core.bq.initBigQuery
import no.nav.tsm.ktor.kafka.consumer.RecordMeta
import no.nav.tsm.sykmelding.input.core.model.Sykmelding
import no.nav.tsm.sykmelding.input.core.model.SykmeldingRecord
import no.nav.tsm.sykmelding.input.core.model.metadata.PersonIdType
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

class SinkService(
    env: Environment
) {
    private val bq: BigQuery = env.gcp.initBigQuery()

    fun insertRecordIntoBigQuery(record: SykmeldingRecord, meta: RecordMeta) {
        val request = InsertAllRequest.newBuilder(SYKMELDINGER_TABLE)
            .addRow(meta.key, record.toBigQueryRow())
            .build()

        val response = bq.insertAll(request)
        check(!response.hasErrors()) {
            "BigQuery rejected the record: ${response.insertErrors}"
        }
    }

    /**
     * TODO:
     */
    fun deleteRecordFromBigQuery(key: String) {
        // doit
    }
}

private fun SykmeldingRecord.toBigQueryRow(): Map<String, Any?> {
    val sykmelding = this.sykmelding

    return mapOf(
        "id" to sykmelding.id,
        "fom" to sykmelding.aktivitet.minOf { aktivitet -> aktivitet.fom }.toString(),
        "tom" to sykmelding.aktivitet.maxOf { aktivitet -> aktivitet.tom }.toString(),
        "type" to sykmelding.type.name,
        "generert_dato" to sykmelding.metadata.genDate.bqDateTime(),
        "nav_mottatt_dato" to sykmelding.metadata.mottattDato.bqDateTime(),
        "regel_utfall" to validation.status.name,
        "avsender_system" to sykmelding.metadata.avsenderSystem.navn,
        "avsender_version" to sykmelding.metadata.avsenderSystem.versjon,
        "regelsett_versjon" to null,
        "pasient_ident_sha256" to sykmelding.pasient.fnr.shaIt()
    ) + when (sykmelding) {
        is Sykmelding.Nasjonal -> {
            mapOf(
                "behandler_hpr_nr" to sykmelding.behandler.ids.find { it.type == PersonIdType.HPR }?.id,
                "behandler_her_id" to sykmelding.behandler.ids.find { it.type == PersonIdType.HER }?.id,
                "behandler_ident_sha256" to sykmelding.behandler.ids.find { it.type == PersonIdType.FNR || it.type == PersonIdType.DNR }?.id?.shaIt(),
                "behandler_hpr_kategori" to sykmelding.sykmelder.helsepersonellKategori.name,
            )
        }

        is Sykmelding.Utenlandsk -> emptyMap()
    }
}

/**
 * Used for idents (fnr/dnr) to hash them before storing in BigQuery
 */
private fun String.shaIt(): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))

    return HexFormat.of().formatHex(digest)
}

private fun OffsetDateTime.bqDateTime() = atZoneSameInstant(ZoneOffset.UTC)
    .truncatedTo(ChronoUnit.SECONDS)
    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
