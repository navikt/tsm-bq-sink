package no.nav.tsm.kafka

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.di.dependencies
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
            shouldSkip = {
                when (env.runtime.env) {
                    RuntimeCluster.DEV -> {
                        logger.warn("Skipping record with key ${it.key} because we are in DEV environment")
                        true
                    }
                    else -> false
                }
            }
        )
    }
}
