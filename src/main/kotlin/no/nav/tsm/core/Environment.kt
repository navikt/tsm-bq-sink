package no.nav.tsm.core

import io.ktor.server.config.ApplicationConfig

class GcpConfig(
    val projectId: String
)

class Environment(
    val gcp: GcpConfig,
)

fun initializeEnvironment(config: ApplicationConfig): Environment {
    return Environment(
        gcp = GcpConfig(
            projectId = config.property("bigquery.projectId").getString()
        )
    )
}
