package no.nav.tsm.plugins

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import no.nav.tsm.core.Environment
import no.nav.tsm.core.migrateDatabase

fun Application.configureDatabase() {
    val env: Environment by dependencies

    migrateDatabase(env.gcp)
}
