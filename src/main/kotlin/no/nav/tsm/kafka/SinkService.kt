package no.nav.tsm.kafka

import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.InsertAllRequest
import no.nav.tsm.bq.*
import no.nav.tsm.core.Environment
import no.nav.tsm.ktor.kafka.consumer.RecordMeta
import no.nav.tsm.sykmelding.SYKMELDINGER_TABLE
import no.nav.tsm.sykmelding.SYKMELDINGER_UTENLANDSK_TABLE
import no.nav.tsm.sykmelding.SykmeldingBqMappersService
import no.nav.tsm.sykmelding.input.core.model.SykmeldingRecord

class SinkService(
    private val sykmeldingBqMappersService: SykmeldingBqMappersService,
    env: Environment,
) {
    private val bq: BigQuery = env.gcp.initBigQuery()

    fun insertRecordIntoBigQuery(record: SykmeldingRecord, meta: RecordMeta) {
        val request = if (record is SykmeldingRecord.Utenlandsk) {
            InsertAllRequest.newBuilder(SYKMELDINGER_UTENLANDSK_TABLE)
                .addRow(meta.key, sykmeldingBqMappersService.toUtenlandskBqPayload(record).toContent())
                .build()
        } else {
            InsertAllRequest.newBuilder(SYKMELDINGER_TABLE)
                .addRow(meta.key, sykmeldingBqMappersService.toNasjonalBqPayload(record).toContent())
                .build()
        }

        val sykmeldingResponse = bq.insertAll(request)
        check(!sykmeldingResponse.hasErrors()) {
            "BigQuery rejected the record: ${sykmeldingResponse.insertErrors}"
        }
    }


    fun deleteRecordFromBigQuery(key: String) {

    }
}
