package no.nav.tsm

import com.google.cloud.bigquery.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
import no.nav.tsm.sykmelding.SYKMELDINGER_TABLE
import no.nav.tsm.bq.migrations.nasjonalSykmeldingSchema
import no.nav.tsm.core.Environment
import no.nav.tsm.plugins.configureDependencies
import no.nav.tsm.plugins.configureMonitoring
import no.nav.tsm.sykmelding.HashService
import no.nav.tsm.sykmelding.SykmeldingBqMappersService
import no.nav.tsm.utils.digitalSykmelding
import org.junit.Ignore
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

    @Test
    @Ignore
    fun `real test please don't run`() {
        /**
         * This test will run against real BigQuery using your local outh
         */
        val bigQuery = BigQueryOptions.newBuilder()
            .setProjectId("tsm-dev-6602")
            .setLocation("europe-north1")
            .build().service

        val testTable = TableId.of("test123", SYKMELDINGER_TABLE.table)
        if (bigQuery.getTable(testTable) == null) {
            val tableConstraint = TableConstraints.newBuilder().setPrimaryKey(
                PrimaryKey.newBuilder().setColumns(listOf("id")).build()
            ).build()
            val tp = TimePartitioning
                .newBuilder(TimePartitioning.Type.MONTH)
                .setField("meta_generert_dato")
                .build()
            val ntd = StandardTableDefinition.newBuilder()
                .setSchema(nasjonalSykmeldingSchema)
                .setTimePartitioning(tp)
                .setTableConstraints(tableConstraint)
                .build()

            bigQuery.create(TableInfo.newBuilder(testTable, ntd).build())
        }

        val environment = mockk<Environment>(relaxed = true).apply { every { hashSecret } returns "test-secret" }
        val mappers = SykmeldingBqMappersService(
            hashService = HashService(environment)
        )
        val testSykmelding = digitalSykmelding("335866e1-19d9-4bff-ba24-fb764214fa9c")
        val res = bigQuery.insertAll(
            InsertAllRequest
                .newBuilder(testTable)
                .addRow(
                    testSykmelding.sykmelding.id,
                    mappers.toNasjonalBqPayload(testSykmelding).toContent()
                )
                .build()
        )

        if (res.hasErrors()) {
            println("BigQuery rejected the record: ${res.insertErrors}")
            throw RuntimeException("BigQuery rejected the record: ${res.insertErrors}")
        } else {
            println("Record inserted successfully")
        }
    }
}
