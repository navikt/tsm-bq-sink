package no.nav.tsm.core

import io.ktor.server.config.*
import no.nav.tsm.ktor.nais.RuntimeCluster
import no.nav.tsm.ktor.nais.getRuntimeCluster

class Runtime(val env: RuntimeCluster, val name: String)

class GcpConfig(
    val projectId: String
)

class Environment(
    val runtime: Runtime,
    val gcp: GcpConfig,
    val hashSecret: String
)

fun initializeEnvironment(config: ApplicationConfig): Environment {
    return Environment(
        runtime = Runtime(
            env = getRuntimeCluster(),
            name = config.property("app.name").getString()
        ),
        hashSecret = config.property("app.hashSecret").getString(),
        gcp = GcpConfig(
            projectId = config.property("bigquery.projectId").getString()
        )
    )
}
