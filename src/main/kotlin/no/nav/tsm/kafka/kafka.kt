package no.nav.tsm.kafka

import com.google.cloud.bigquery.BigQueryException
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import no.nav.tsm.core.Environment
import no.nav.tsm.ktor.kafka.consumer.KafkaConsumer
import no.nav.tsm.ktor.logger
import no.nav.tsm.ktor.nais.RuntimeCluster
import no.nav.tsm.sykmelding.input.core.model.SykmeldingModule
import no.nav.tsm.sykmelding.input.core.model.SykmeldingRecord

fun Application.configureKafka() {
    val logger = logger()
    val env: Environment by dependencies
    val service: SinkService by dependencies

    install(KafkaConsumer) {
        clientId = env.runtime.name
        groupId = "tsm-bq-sink"
        jacksonModule(SykmeldingModule())
        consume<SykmeldingRecord>(
            name = "tsm.sykmeldinger",
            onRecord = { record, meta -> service.insertRecordIntoBigQuery(record, meta) },
            onTombstone = { service.deleteRecordFromBigQuery(it.key) },
            shouldSkip = { meta, ex ->
                // Never skip failed BigQuery writes or deletes; they must be retried even in DEV.
                if (ex is BigQueryException || ex is BigQueryWriteException) return@consume false

                when (env.runtime.env) {
                    RuntimeCluster.DEV -> {
                        logger.warn("Skipping record with key ${meta.key} because we are in DEV environment", ex)
                        true
                    }
                    else -> false
                }
            }
        )
    }
}
