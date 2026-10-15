package no.nav.tsm.bq

import no.nav.tsm.sykmelding.input.core.model.SykmeldingModule
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class BqProperties(
    val name: String,
    val nullable: Boolean,
    val repeated: Boolean,
)

sealed interface BqTypes {
    val bqProps: BqProperties

    fun toBq(): Any?

    class String(val value: kotlin.String) : BqTypes {
        override val bqProps = BqProperties("STRING", nullable = false, repeated = false)

        class Nullable(val value: kotlin.String?) : BqTypes {
            override val bqProps = BqProperties("STRING", nullable = true, repeated = false)

            override fun toBq(): Any? = value
        }

        override fun toBq(): Any = value
    }

    class Long(val value: kotlin.Long) : BqTypes {
        override val bqProps = BqProperties("INTEGER", nullable = false, repeated = false)

        class Nullable(val value: kotlin.Long?) : BqTypes {
            override val bqProps = BqProperties("INTEGER", nullable = true, repeated = false)

            override fun toBq(): Any? = value
        }

        override fun toBq(): Any = value
    }

    class Boolean(val value: kotlin.Boolean) : BqTypes {
        override val bqProps = BqProperties("BOOLEAN", nullable = false, repeated = false)

        class Nullable(val value: kotlin.Boolean?) : BqTypes {
            override val bqProps = BqProperties("BOOLEAN", nullable = true, repeated = false)

            override fun toBq(): Any? = value
        }

        override fun toBq(): Any = value
    }


    class DateTime(val value: OffsetDateTime) : BqTypes {
        override val bqProps = BqProperties("DATETIME", nullable = false, repeated = false)

        /**
         * BigQuery does NOT want a zone identifier in the string, and it does NOT support more than
         * 6 digits of sub-second precision.
         */
        private fun OffsetDateTime.formatBqDateTime() = atZoneSameInstant(ZoneOffset.UTC)
            .truncatedTo(ChronoUnit.SECONDS)
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        override fun toBq(): Any = value.formatBqDateTime()
    }

    class Date(val value: LocalDate) : BqTypes {
        class Nullable(val value: LocalDate?) : BqTypes {
            override val bqProps = BqProperties("DATE", nullable = true, repeated = false)

            override fun toBq(): Any? = value?.toString()
        }

        override val bqProps = BqProperties("DATE", nullable = false, repeated = false)

        override fun toBq(): Any = value.toString()
    }

    class Enum(val value: kotlin.Enum<*>) : BqTypes {
        class Nullable(val value: kotlin.Enum<*>?) : BqTypes {
            override val bqProps = BqProperties("STRING", nullable = true, repeated = false)

            override fun toBq(): Any? = value?.name
        }

        override val bqProps = BqProperties("STRING", nullable = false, repeated = false)

        override fun toBq(): Any = value.name
    }

    class SubRecord(val value: BqPayload) : BqTypes {
        class Nullable(val value: BqPayload?) : BqTypes {
            override val bqProps = BqProperties("STRUCT", nullable = true, repeated = false)

            override fun toBq(): Any? = value?.toContent()
        }

        override val bqProps = BqProperties("STRUCT", nullable = false, repeated = false)

        override fun toBq(): Any = value.toContent()
    }

    class RecordArray(val value: List<BqPayload>) : BqTypes {
        override val bqProps = BqProperties("RECORD", nullable = false, repeated = true)

        class Nullable(val value: List<BqPayload>?) : BqTypes {
            override val bqProps = BqProperties("RECORD", nullable = true, repeated = true)

            override fun toBq(): Any? = value?.map { it.toContent() }
        }

        override fun toBq(): Any = value.map { it.toContent() }
    }

    class JsonArray<JsonType>(val value: List<JsonType>) : BqTypes {
        override val bqProps = BqProperties("JSON", nullable = false, repeated = true)

        class Nullable<JsonType>(val value: List<JsonType>?) : BqTypes {
            override val bqProps = BqProperties("JSON", nullable = true, repeated = true)

            override fun toBq(): List<Any?> = value?.map { mapper.writeValueAsString(it) } ?: emptyList()
        }

        override fun toBq(): List<Any> = value.map { mapper.writeValueAsString(it) }
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

    fun get(name: String): BqTypes? = fields[name]
}

private val mapper = jacksonMapperBuilder().addModule(SykmeldingModule()).build()
