package no.nav.tsm.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import no.nav.tsm.bq.runBigQueryMigrations
import no.nav.tsm.core.Environment

fun Application.configureDatabase() {
    val env: Environment by dependencies

    runBigQueryMigrations(env.gcp)
}
