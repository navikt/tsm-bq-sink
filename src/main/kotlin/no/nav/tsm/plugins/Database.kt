package no.nav.tsm.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import no.nav.tsm.core.Environment
import no.nav.tsm.core.bq.runBigQueryMigrations

fun Application.configureDatabase() {
    val env: Environment by dependencies

    runBigQueryMigrations(env.gcp)
}
