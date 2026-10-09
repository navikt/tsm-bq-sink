package no.nav.tsm.core.bq

import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.Field
import com.google.cloud.bigquery.StandardSQLTypeName
import no.nav.tsm.core.GcpConfig
import no.nav.tsm.core.bq.migrations.applyMigrations
import no.nav.tsm.core.bq.migrations.ensureMigrationsTable

const val DATASET_ID = "tsm_kafka_sink"

fun nonNullableField(name: String, type: StandardSQLTypeName): Field =
    Field.newBuilder(name, type).setMode(Field.Mode.REQUIRED).build()

fun field(name: String, type: StandardSQLTypeName): Field =
    Field.newBuilder(name, type).setMode(Field.Mode.NULLABLE).build()

fun repeated(name: String, vararg subfields: Field): Field =
    Field.newBuilder(name, StandardSQLTypeName.STRUCT, *subfields).setMode(Field.Mode.REPEATED).build()

fun GcpConfig.initBigQuery() = BigQueryOptions.newBuilder()
    .setProjectId(projectId)
    .setLocation("europe-north1")
    .build().service

fun runBigQueryMigrations(env: GcpConfig) {
    val bigQuery = env.initBigQuery()

    ensureMigrationsTable(bigQuery)
    applyMigrations(bigQuery)
}
