package no.nav.tsm.kafka

import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.InsertAllRequest
import no.nav.tsm.core.Environment
import no.nav.tsm.core.bq.SYKMELDINGER_TABLE
import no.nav.tsm.core.bq.SYKMELDINGER_UTENLANDSK_TABLE
import no.nav.tsm.core.bq.initBigQuery
import no.nav.tsm.core.bq.toNasjonalRow
import no.nav.tsm.core.bq.toUtenlandskRow
import no.nav.tsm.ktor.kafka.consumer.RecordMeta
import no.nav.tsm.sykmelding.input.core.model.SykmeldingRecord

class SinkService(
    env: Environment
) {
    private val bq: BigQuery = env.gcp.initBigQuery()

    fun insertRecordIntoBigQuery(record: SykmeldingRecord, meta: RecordMeta) {
        val request = if (record is SykmeldingRecord.Utenlandsk) {
            InsertAllRequest.newBuilder(SYKMELDINGER_UTENLANDSK_TABLE)
                .addRow(meta.key, record.toUtenlandskRow())
                .build()
        } else {
             InsertAllRequest.newBuilder(SYKMELDINGER_TABLE)
                .addRow(meta.key, record.toNasjonalRow())
                .build()
        }

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

