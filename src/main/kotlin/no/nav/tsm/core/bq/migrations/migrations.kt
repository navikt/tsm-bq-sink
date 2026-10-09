package no.nav.tsm.core.bq.migrations

import com.google.cloud.bigquery.*
import no.nav.tsm.core.bq.DATASET_ID
import no.nav.tsm.ktor.logger

private val logger = logger()

private val MIGRATIONS_TABLE: TableId = TableId.of(DATASET_ID, "_migrations")

fun BigQuery.insertSchemaVersion(version: Long) {
    insertAll(
        InsertAllRequest.newBuilder(MIGRATIONS_TABLE)
            .addRow(mapOf("schema_version" to version, "last_updated" to System.currentTimeMillis() / 1000.0))
            .build()
    )
}

fun ensureMigrationsTable(bigQuery: BigQuery) {
    val table = bigQuery.getTable(MIGRATIONS_TABLE)
    if (table == null) createMigrationsTable(bigQuery)
}


fun applyMigrations(bigQuery: BigQuery) {
    val currentSchemaVersion = bigQuery.getCurrentSchemaVersion()
    logger.info("Current schema version: $currentSchemaVersion")

    if (currentSchemaVersion < 1) v1InitialSchema(bigQuery)

    // Any future migrations should be added here, e.g.:
    // if (currentSchemaVersion < 2) v2SomeNewSchema(bigQuery)
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
    bigQuery.insertSchemaVersion(0)
}

private fun BigQuery.getCurrentSchemaVersion(): Long {
    return this.query(
        QueryJobConfiguration.newBuilder(
            "SELECT schema_version FROM `${DATASET_ID}._migrations` ORDER BY last_updated DESC LIMIT 1"
        ).build()
    ).values.firstOrNull()?.get(0)?.longValue ?: 0L
}

