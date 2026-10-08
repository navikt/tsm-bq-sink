package no.nav.tsm.kafka

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.di.dependencies
import no.nav.tsm.core.Environment
import no.nav.tsm.ktor.kafka.sykmeldinger.SykmeldingerConsumer

fun Application.configureKafka() {
    val env: Environment by dependencies
    val service: SinkService by dependencies

    install(SykmeldingerConsumer) {
        clientId = env.runtime.name
        groupId = "tsm-bq-sink"
        onRecord = { record, meta -> service.insertRecordIntoBQ(record, meta) }
        onTombstone = { meta -> service.deleteRecord(meta.key) }
    }
}
