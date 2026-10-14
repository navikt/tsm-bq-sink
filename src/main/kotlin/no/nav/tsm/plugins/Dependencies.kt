package no.nav.tsm.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import no.nav.tsm.sykmelding.HashService
import no.nav.tsm.core.Environment
import no.nav.tsm.core.initializeEnvironment
import no.nav.tsm.kafka.SinkService
import no.nav.tsm.sykmelding.SykmeldingBqMappersService

fun Application.configureDependencies() {
    val config = environment.config

    dependencies {
        provide<Environment> { initializeEnvironment(config) }
        provide(HashService::class)
        provide(SinkService::class)
        provide(SykmeldingBqMappersService::class)
    }
}
