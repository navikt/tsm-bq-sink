package no.nav.tsm.plugins

import com.google.cloud.bigquery.storage.v1.BigQueryWriteClient
import com.google.cloud.bigquery.storage.v1.JsonStreamWriter
import com.google.cloud.bigquery.storage.v1.TableName
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.*
import no.nav.tsm.core.Environment
import no.nav.tsm.sykmelding.SYKMELDINGER_TABLE
import no.nav.tsm.sykmelding.SYKMELDINGER_UTENLANDSK_TABLE

fun Application.configureBigQueryWriters() {
    dependencies {
        // Ktor DI closes these in reverse registration order: writers before the client.
        provide<BigQueryWriteClient> { BigQueryWriteClient.create() }

        listOf("nasjonal" to SYKMELDINGER_TABLE, "utenlandsk" to SYKMELDINGER_UTENLANDSK_TABLE)
            .forEach { (name, table) ->
                provide<JsonStreamWriter>(name) {
                    val env = resolve<Environment>()
                    val tableName = TableName.of(env.gcp.projectId, table.dataset, table.table)

                    // A table resource name selects its default stream.
                    JsonStreamWriter.newBuilder(tableName.toString(), resolve<BigQueryWriteClient>()).build()
                }
            }
    }
}
