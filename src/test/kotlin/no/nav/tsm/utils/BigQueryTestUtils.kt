package no.nav.tsm.utils

import com.google.cloud.NoCredentials
import com.google.cloud.bigquery.*
import no.nav.tsm.bq.migrations.applyMigrations
import no.nav.tsm.bq.migrations.ensureMigrationsTable
import org.testcontainers.gcloud.BigQueryEmulatorContainer

abstract class BigQueryTestContainer {
    companion object {
        val container = BigQueryEmulatorContainer("ghcr.io/goccy/bigquery-emulator:0.4.3")
        var bigQuery: BigQuery

        init {
            container.start()

            bigQuery = BigQueryOptions
                .newBuilder()
                .setProjectId(container.projectId)
                .setHost(container.emulatorHttpEndpoint)
                .setLocation(container.emulatorHttpEndpoint)
                .setCredentials(NoCredentials.getInstance())
                .build()
                .service

            // Nais creates the dataset for us, so for testing we'll need to create it manually
            bigQuery.create(DatasetInfo.newBuilder("tsm_kafka_sink").build())

            // Run the migrations
            ensureMigrationsTable(bigQuery)
            applyMigrations(bigQuery)
        }
    }

    fun getSchema(table: TableId): Schema {
        val table = requireNotNull(bigQuery.getTable(table)) { "Table $table does not exist" }
        val schema =
            requireNotNull(table.getDefinition<StandardTableDefinition>().schema) { "Table $table has no schema" }

        return schema
    }
}
