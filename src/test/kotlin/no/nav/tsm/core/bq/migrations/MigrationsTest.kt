package no.nav.tsm.core.bq.migrations

import com.google.cloud.NoCredentials
import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import org.junit.Test
import org.testcontainers.gcloud.BigQueryEmulatorContainer


class MigrationsTest {
    companion object {
        val container = BigQueryEmulatorContainer("ghcr.io/goccy/bigquery-emulator:0.4.3")
        var service: BigQuery

        init {
            container.start()

            service = BigQueryOptions
                .newBuilder()
                .setProjectId(container.projectId)
                .setHost(container.emulatorHttpEndpoint)
                .setLocation(container.emulatorHttpEndpoint)
                .setCredentials(NoCredentials.getInstance())
                .build()
                .service

            // Nais creates the dataset for us, so for testing we'll need to create it manually
            service.create(com.google.cloud.bigquery.DatasetInfo.newBuilder("tsm_kafka_sink").build())
        }
    }

    @Test
    fun `test migrations`() {
        val options = BigQueryOptions
            .newBuilder()
            .setProjectId(container.projectId)
            .setHost(container.emulatorHttpEndpoint)
            .setLocation(container.emulatorHttpEndpoint)
            .setCredentials(NoCredentials.getInstance())
            .build()

        val bigQuery = options.getService()

        ensureMigrationsTable(bigQuery)
        applyMigrations(bigQuery)
    }
}
