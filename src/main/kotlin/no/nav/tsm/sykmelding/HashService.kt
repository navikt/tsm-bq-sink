package no.nav.tsm.sykmelding

import no.nav.tsm.core.Environment
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


class HashService(environment: Environment) {
    private val secret = environment.hashSecret

    /**
     * Used for idents (fnr/dnr) to hash them with HMAC-SHA256 before storing in BigQuery.
     */
    fun hashIdent(ident: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(ident.toByteArray(Charsets.UTF_8))

        return HexFormat.of().formatHex(digest)
    }
}
