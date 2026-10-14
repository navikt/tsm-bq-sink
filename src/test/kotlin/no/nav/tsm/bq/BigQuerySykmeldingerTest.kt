package no.nav.tsm.bq

import com.google.cloud.bigquery.*
import io.mockk.every
import io.mockk.mockk
import no.nav.tsm.core.Environment
import no.nav.tsm.sykmelding.HashService
import no.nav.tsm.sykmelding.SYKMELDINGER_TABLE
import no.nav.tsm.sykmelding.SYKMELDINGER_UTENLANDSK_TABLE
import no.nav.tsm.sykmelding.SykmeldingBqMappersService
import no.nav.tsm.sykmelding.input.core.model.SykmeldingRecord
import no.nav.tsm.utils.BigQueryTestContainer
import no.nav.tsm.utils.digitalSykmelding
import no.nav.tsm.utils.utenlandskSykmelding
import no.nav.tsm.utils.xmlSykmelding
import org.junit.Test

class BigQuerySykmeldingerTest : BigQueryTestContainer() {

    val environment = mockk<Environment>(relaxed = true).apply { every { hashSecret } returns "test-secret" }
    val mappers = SykmeldingBqMappersService(
        hashService = HashService(environment)
    )


    @Test
    fun `verify nullability on a basic insert`() {
        // Insert one sykmelding
        val sykmelding = digitalSykmelding("b8e9c32c-9a1e-4a85-8e67-e9284f5c9fd6")
        val request = InsertAllRequest.newBuilder(SYKMELDINGER_TABLE)
            .addRow(sykmelding.sykmelding.id, mappers.toNasjonalBqPayload(sykmelding).toContent())
            .build()

        val response = bigQuery.insertAll(request)
        check(!response.hasErrors()) { "BigQuery rejected the record: ${response.insertErrors}" }
        val row = requireNotNull(
            bigQuery.query(
                QueryJobConfiguration.newBuilder(
                    "SELECT * FROM `${DATASET_ID}.sykmeldinger` WHERE id = '${sykmelding.sykmelding.id}' LIMIT 1"
                ).build()
            ).values.firstOrNull()
        ) { "Inserted row not found in BigQuery" }

        val table =
            requireNotNull(bigQuery.getTable(SYKMELDINGER_TABLE)) { "Table ${SYKMELDINGER_TABLE} does not exist" }
        val schema =
            requireNotNull(table.getDefinition<StandardTableDefinition>().schema) { "Table ${SYKMELDINGER_TABLE} has no schema" }

        schema.fields.forEach { field ->
            println("${field.name} ${field.type} (${field.mode}) = ${row.get(field.name).value}")
            if (field.mode == Field.Mode.REPEATED && field.type == LegacySQLTypeName.RECORD) {
                field.subFields.forEach { sf ->
                    println("  - ${sf.name} ${field.type} (${sf.mode}) = ${row.get(field.name).value}")
                }
            }
            if (field.mode == Field.Mode.REPEATED && field.type == LegacySQLTypeName.JSON) {
                println("  - ${field.name} is a repeated JSON field")
            }

            if (field.mode == Field.Mode.REQUIRED) {
                requireNotNull(row.get(field.name).value) { "Required field ${field.name} is null in the inserted row" }
            }
        }
    }

    @Test
    fun `verify nullability on a utenlandsk insert`() {
        // Insert one sykmelding
        val sykmelding: SykmeldingRecord.Utenlandsk = utenlandskSykmelding("b8e9c32c-9a1e-4a85-8e67-e9284f5c9fd6")
        val request = InsertAllRequest.newBuilder(SYKMELDINGER_UTENLANDSK_TABLE)
            .addRow(sykmelding.sykmelding.id, mappers.toUtenlandskBqPayload(sykmelding).toContent())
            .build()

        val response = bigQuery.insertAll(request)
        check(!response.hasErrors()) { "BigQuery rejected the record: ${response.insertErrors}" }
        val row = requireNotNull(
            bigQuery.query(
                QueryJobConfiguration.newBuilder(
                    "SELECT * FROM `${DATASET_ID}.sykmeldinger_utenlandsk` WHERE id = '${sykmelding.sykmelding.id}' LIMIT 1"
                ).build()
            ).values.firstOrNull()
        ) { "Inserted row not found in BigQuery" }

        val table =
            requireNotNull(bigQuery.getTable(SYKMELDINGER_UTENLANDSK_TABLE)) { "Table ${SYKMELDINGER_UTENLANDSK_TABLE} does not exist" }
        val schema =
            requireNotNull(table.getDefinition<StandardTableDefinition>().schema) { "Table ${SYKMELDINGER_UTENLANDSK_TABLE} has no schema" }

        schema.fields.forEach { field ->
            if (field.mode == Field.Mode.REQUIRED) {
                requireNotNull(row.get(field.name).value) { "Required field ${field.name} is null in the inserted row" }
            }
        }
    }

    @Test
    fun `verify schema (digital) against BQ types`() {
        val schema = getSchema(SYKMELDINGER_TABLE)
        val mapped = mappers.toNasjonalBqPayload(digitalSykmelding("b8e9c32c-9a1e-4a85-8e67-e9284f5c9fd6"))

        val expectedMissingInDigital = listOf("meta_regelsett_versjon", "syketilfellet_start_dato")
        schema.fields.forEach {
            if (expectedMissingInDigital.contains(it.name)) {
                println("Field ${it.name} is in the schema but not in the mapped payload, skipping")
                return@forEach
            }

            val actual = requireNotNull(mapped.get(it.name)) {
                "Field ${it.name} (${it.type}, ${it.mode}) is in the schema but not in the mapped payload"
            }

            when (it.mode) {
                Field.Mode.NULLABLE -> {
                    require(actual.bqProps.nullable) { "Field ${it.name} is nullable in the schema but not in the mapped payload" }
                }

                Field.Mode.REPEATED -> {
                    require(actual.bqProps.repeated) { "Field ${it.name} is repeated in the schema but not in the mapped payload" }
                }

                else -> {}
            }
        }
    }

    @Test
    fun `verify schema (xml) against BQ types`() {
        val schema = getSchema(SYKMELDINGER_TABLE)
        val mapped = mappers.toNasjonalBqPayload(xmlSykmelding("b8e9c32c-9a1e-4a85-8e67-e9284f5c9fd6"))

        schema.fields.forEach {
            val actual = requireNotNull(mapped.get(it.name)) {
                "Field ${it.name} (${it.type}, ${it.mode}) is in the schema but not in the mapped payload"
            }

            when (it.mode) {
                Field.Mode.NULLABLE -> {
                    require(actual.bqProps.nullable) { "Field ${it.name} is nullable in the schema but not in the mapped payload" }
                }

                Field.Mode.REPEATED -> {
                    require(actual.bqProps.repeated) { "Field ${it.name} is repeated in the schema but not in the mapped payload" }
                }

                else -> {}
            }
        }
    }

    @Test
    fun `verify schema (utenlansk) against BQ types`() {
        val schema = getSchema(SYKMELDINGER_UTENLANDSK_TABLE)
        val mapped = mappers.toUtenlandskBqPayload(utenlandskSykmelding("b8e9c32c-9a1e-4a85-8e67-e9284f5c9fd6"))

        schema.fields.forEach {
            val actual = requireNotNull(mapped.get(it.name)) {
                "Field ${it.name} (${it.type}, ${it.mode}) is in the schema but not in the mapped payload"
            }

            when (it.mode) {
                Field.Mode.NULLABLE -> {
                    require(actual.bqProps.nullable) { "Field ${it.name} is nullable in the schema but not in the mapped payload" }
                }

                Field.Mode.REPEATED -> {
                    require(actual.bqProps.repeated) { "Field ${it.name} is repeated in the schema but not in the mapped payload" }
                }

                else -> {}
            }
        }
    }
}
