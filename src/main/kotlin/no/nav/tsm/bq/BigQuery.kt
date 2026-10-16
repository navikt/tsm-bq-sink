package no.nav.tsm.bq

import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.Field
import com.google.cloud.bigquery.StandardSQLTypeName
import no.nav.tsm.bq.migrations.applyMigrations
import no.nav.tsm.bq.migrations.ensureMigrationsTable
import no.nav.tsm.core.GcpConfig

const val DATASET_ID = "tsm_kafka_sink"

fun nonNullableField(name: String, type: StandardSQLTypeName, description: String? = null): Field =
    Field.newBuilder(name, type).setMode(Field.Mode.REQUIRED)
        .setDescription(description)
        .build()

fun nullableField(name: String, type: StandardSQLTypeName, description: String? = null): Field =
    Field.newBuilder(name, type).setMode(Field.Mode.NULLABLE)
        .setDescription(description)
        .build()

fun arrayRecord(name: String, vararg subfields: Field, description: String? = null): Field =
    Field.newBuilder(name, StandardSQLTypeName.STRUCT, *subfields).setMode(Field.Mode.REPEATED)
        .setDescription(description)
        .build()

fun arrayOf(name: String, type: StandardSQLTypeName, description: String? = null): Field =
    Field.newBuilder(name, type).setMode(Field.Mode.REPEATED)
        .setDescription(description)
        .build()

fun GcpConfig.initBigQuery() = BigQueryOptions.newBuilder()
    .setProjectId(projectId)
    .setLocation("europe-north1")
    .build().service

fun runBigQueryMigrations(env: GcpConfig) {
    val bigQuery = env.initBigQuery()

    ensureMigrationsTable(bigQuery)
    applyMigrations(bigQuery)
}
