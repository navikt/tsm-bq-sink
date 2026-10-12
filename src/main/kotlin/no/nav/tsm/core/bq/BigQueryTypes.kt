package no.nav.tsm.core.bq

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

sealed interface BqTypes {
    val BQ_SCHEMA_NAME: kotlin.String

    fun toBq(): Any?

    class String(val value: kotlin.String) : BqTypes {
        override val BQ_SCHEMA_NAME = "STRING"

        class Nullable(val value: kotlin.String?) : BqTypes {
            override val BQ_SCHEMA_NAME = "STRING"

            override fun toBq(): Any? = value
        }

        override fun toBq(): Any = value
    }

    class Boolean(val value: kotlin.Boolean) : BqTypes {
        override val BQ_SCHEMA_NAME = "BOOLEAN"

        class Nullable(val value: kotlin.Boolean?) : BqTypes {
            override val BQ_SCHEMA_NAME = "BOOLEAN"

            override fun toBq(): Any? = value
        }

        override fun toBq(): Any = value
    }


    class DateTime(val value: OffsetDateTime) : BqTypes {
        /**
         * BigQuery does NOT want a zone identifier in the string, and it does NOT support more than
         * 6 digits of sub-second precision.
         */
        private fun OffsetDateTime.formatBqDateTime() = atZoneSameInstant(ZoneOffset.UTC)
            .truncatedTo(ChronoUnit.SECONDS)
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        override val BQ_SCHEMA_NAME = "DATETIME"

        override fun toBq(): Any = value.formatBqDateTime()
    }

    class Date(val value: java.time.LocalDate) : BqTypes {
        class Nullable(val value: java.time.LocalDate?) : BqTypes {
            override val BQ_SCHEMA_NAME = "DATE"

            override fun toBq(): Any? = value?.toString()
        }

        override val BQ_SCHEMA_NAME = "DATE"

        override fun toBq(): Any = value.toString()
    }

    class Enum(val value: kotlin.Enum<*>) : BqTypes {
        class Nullable(val value: kotlin.Enum<*>?) : BqTypes {
            override val BQ_SCHEMA_NAME = "STRING"

            override fun toBq(): Any? = value?.name
        }

        override val BQ_SCHEMA_NAME = "STRING"

        override fun toBq(): Any = value.name
    }

    class SubRecord(val value: BqPayload) : BqTypes {
        class Nullable(val value: BqPayload?) : BqTypes {
            override val BQ_SCHEMA_NAME = "STRUCT"

            override fun toBq(): Any? = value?.toContent()
        }

        override val BQ_SCHEMA_NAME = "STRUCT"

        override fun toBq(): Any = value.toContent()
    }

    class Array(val value: List<BqPayload>) : BqTypes {
        // TODO: repeated struct??
        override val BQ_SCHEMA_NAME = "STRUCT"

        class Nullable(val value: List<BqPayload>?) : BqTypes {
            // TODO: repeated struct??
            override val BQ_SCHEMA_NAME = "STRUCT"

            override fun toBq(): Any? = value?.map { it.toContent() }
        }

        override fun toBq(): Any = value.map { it.toContent() }
    }
}

class BqPayload(vararg fields: Pair<String, BqTypes>) {
    private var fields: MutableMap<String, BqTypes> = fields.toMap().toMutableMap()

    fun toContent(): Map<String, Any?> = fields.toMap().mapValues { it.value.toBq() }

    fun add(vararg pair: Pair<String, BqTypes>): BqPayload {
        pair.forEach { (key, value) ->
            fields[key] = value
        }

        return this
    }
}
