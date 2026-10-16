package no.nav.tsm.kafka

import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.QueryJobConfiguration
import com.google.cloud.bigquery.QueryParameterValue
import com.google.cloud.bigquery.storage.v1.JsonStreamWriter
import io.ktor.server.plugins.di.annotations.Named
import no.nav.tsm.bq.*
import no.nav.tsm.core.Environment
import no.nav.tsm.ktor.kafka.consumer.RecordMeta
import no.nav.tsm.sykmelding.SYKMELDINGER_TABLE
import no.nav.tsm.sykmelding.SYKMELDINGER_UTENLANDSK_TABLE
import no.nav.tsm.sykmelding.SykmeldingBqMappersService
import no.nav.tsm.sykmelding.input.core.model.SykmeldingRecord
import org.json.JSONArray
import org.json.JSONObject

class SinkService(
    private val sykmeldingBqMappersService: SykmeldingBqMappersService,
    @Named("nasjonal")
    private val nasjonalWriter: JsonStreamWriter,
    @Named("utenlandsk")
    private val utenlandskWriter: JsonStreamWriter,
    env: Environment,
) {
    private val bq: BigQuery = env.gcp.initBigQuery()

    fun insertRecordIntoBigQuery(record: SykmeldingRecord, meta: RecordMeta) {
        val (writer, payload) = if (record is SykmeldingRecord.Utenlandsk) {
            utenlandskWriter to sykmeldingBqMappersService.toUtenlandskBqPayload(record, meta)
        } else {
            nasjonalWriter to sykmeldingBqMappersService.toNasjonalBqPayload(record, meta)
        }

        try {
            // Await acknowledgement before Kafka can commit this record or process its tombstone.
            val response = writer.append(JSONArray().put(JSONObject(payload.toContent()))).get()
            check(!response.hasError()) {
                "BigQuery rejected the record: ${response.error}"
            }
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw BigQueryWriteException(ex)
        } catch (ex: Exception) {
            throw BigQueryWriteException(ex)
        }
    }

    fun deleteRecordFromBigQuery(key: String) {
        listOf(SYKMELDINGER_TABLE, SYKMELDINGER_UTENLANDSK_TABLE).forEach { table ->
            bq.query(
                QueryJobConfiguration.newBuilder(
                    "DELETE FROM `${table.dataset}.${table.table}` WHERE id = @id"
                )
                    .setUseLegacySql(false)
                    .addNamedParameter("id", QueryParameterValue.string(key))
                    .build()
            )
        }
    }
}

class BigQueryWriteException(cause: Exception) : RuntimeException("BigQuery write failed", cause)
