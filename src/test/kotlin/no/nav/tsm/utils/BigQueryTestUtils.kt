package no.nav.tsm.utils

import com.google.cloud.NoCredentials
import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import no.nav.tsm.core.bq.migrations.applyMigrations
import no.nav.tsm.core.bq.migrations.ensureMigrationsTable
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
            bigQuery.create(com.google.cloud.bigquery.DatasetInfo.newBuilder("tsm_kafka_sink").build())

            // Run the migrations
            ensureMigrationsTable(bigQuery)
            applyMigrations(bigQuery)
        }
    }
}
