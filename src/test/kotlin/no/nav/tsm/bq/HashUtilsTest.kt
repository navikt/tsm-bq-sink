package no.nav.tsm.bq

import org.junit.Test

class BigQueryUtilsTest {
    @Test
    fun `test shaIt`() {
        val input = "12345678901"
        val expectedHash = "254aa248acb47dd654ca3ea53f48c2c26d641d23d7e2e93a1ec56258df7674c4"
        val actualHash = input.shaIt()
        assert(actualHash == expectedHash) { "Expected $expectedHash but got $actualHash" }
    }
}
