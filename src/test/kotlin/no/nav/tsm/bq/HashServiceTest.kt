package no.nav.tsm.bq

import io.mockk.every
import io.mockk.mockk
import no.nav.tsm.core.Environment
import no.nav.tsm.sykmelding.HashService
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class HashServiceTest {

    @Test
    fun `simple ident hash test`() {
        val environment = mockk<Environment>(relaxed = true).apply { every { hashSecret } returns "test-secret" }
        val service = HashService(environment)

        val input = "12098312309"
        val expectedHash = "17399c6cdf164d65a88aadbe872f08912a21224cc353217d1bdd7f242aa1a918"
        val actualHash = service.hashIdent(input)
        assertEquals(expectedHash, actualHash)
    }

    @Test
    fun `different secrets produce different hashes`() {
        val environment1 = mockk<Environment>(relaxed = true).apply { every { hashSecret } returns "test-secret" }
        val service1 = HashService(environment1)

        val environment2 = mockk<Environment>(relaxed = true).apply { every { hashSecret } returns "some-other-secret" }
        val service2 = HashService(environment2)


        val input = "12345678901"
        assertNotEquals(service1.hashIdent(input), service2.hashIdent(input))
    }
}
