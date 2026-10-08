package no.nav.tsm.core

import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.Field
import com.google.cloud.bigquery.InsertAllRequest
import com.google.cloud.bigquery.QueryJobConfiguration
import com.google.cloud.bigquery.Schema
import com.google.cloud.bigquery.StandardSQLTypeName
import com.google.cloud.bigquery.StandardTableDefinition
import com.google.cloud.bigquery.TableId
import com.google.cloud.bigquery.TableInfo
import no.nav.tsm.ktor.logger

private val logger = logger()

private const val DATASET_ID = "tsm_kafka_sink"
private val MIGRATIONS_TABLE = TableId.of(DATASET_ID, "_migrations")

fun migrateDatabase(env: GcpConfig) {
    val bigQuery = BigQueryOptions.newBuilder()
        .setProjectId(env.projectId)
        .build().service

    val table = bigQuery.getTable(MIGRATIONS_TABLE)
    if (table == null) createMigrationsTable(bigQuery)

    val currentSchemaVersion = bigQuery.query(
        QueryJobConfiguration.newBuilder(
            "SELECT schema_version FROM `${DATASET_ID}._migrations` ORDER BY last_updated DESC LIMIT 1"
        ).build()
    ).values.firstOrNull()?.get(0)?.longValue ?: 0L

    logger.info("Current schema version: $currentSchemaVersion, TODO: run migrations")

    /*
    when (currentSchemaVersion) {
        0L -> V1_initial_schema(bigQuery)
    }
    */
}

private fun createMigrationsTable(bigQuery: BigQuery) {
    logger.info("Fresh dataset, creating migrations table")

    val schema = Schema.of(
        Field.of("schema_version", StandardSQLTypeName.INT64),
        Field.of("last_updated", StandardSQLTypeName.TIMESTAMP),
    )

    val tableDefinition = StandardTableDefinition.of(schema)
    val tableInfo = TableInfo.newBuilder(MIGRATIONS_TABLE, tableDefinition).build()

    bigQuery.create(tableInfo)
    bigQuery.insertAll(
        InsertAllRequest.newBuilder(MIGRATIONS_TABLE)
            .addRow(mapOf("schema_version" to 0L, "last_updated" to System.currentTimeMillis() / 1000.0))
            .build()
    )
}

private fun V1_initial_schema(bigQuery: BigQuery) {
    val schema = Schema.of(
        Field.of("id", StandardSQLTypeName.STRING),
        Field.of("data", StandardSQLTypeName.STRING),
        Field.of("created_at", StandardSQLTypeName.TIMESTAMP),
    )

    val tableDefinition = StandardTableDefinition.of(schema)
    val tableInfo = TableInfo.newBuilder(TableId.of(DATASET_ID, "sykmeldinger"), tableDefinition).build()

    bigQuery.create(tableInfo)
    bigQuery.insertAll(
        InsertAllRequest.newBuilder(MIGRATIONS_TABLE)
            .addRow(mapOf("schema_version" to 1L, "last_updated" to System.currentTimeMillis() / 1000.0))
            .build()
    )
}
