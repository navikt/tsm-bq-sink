package no.nav.tsm.core.bq

import java.security.MessageDigest
import java.util.HexFormat

/**
 * Used for idents (fnr/dnr) to hash them before storing in BigQuery
 */
fun String.shaIt(): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))

    return HexFormat.of().formatHex(digest)
}
