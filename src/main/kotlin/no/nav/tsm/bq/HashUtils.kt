package no.nav.tsm.bq

import java.security.MessageDigest
import java.util.*

/**
 * Used for idents (fnr/dnr) to hash them before storing in BigQuery
 */
fun String.shaIt(): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))

    return HexFormat.of().formatHex(digest)
}
