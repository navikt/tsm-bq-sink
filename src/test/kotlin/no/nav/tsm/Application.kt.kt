package no.nav.tsm

import io.ktor.http.HttpStatusCode
import io.ktor.client.request.get
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.*
import no.nav.tsm.plugins.configureDependencies
import no.nav.tsm.plugins.configureMonitoring
import kotlin.test.Test
import kotlin.test.assertEquals

class Application {

    @Test
    fun `health endpoint is available`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }
        application {
            configureDependencies()
            configureMonitoring()
        }

        assertEquals(HttpStatusCode.OK, client.get("/internal/health/alive").status)
    }
}
