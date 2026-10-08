package no.nav.tsm.no.nav.tsm

import io.ktor.server.testing.*
import kotlin.test.Test

class Application {

    @Test
    fun `dummy`() = testApplication {
        // loads default configuration
        configure()
    }
}
