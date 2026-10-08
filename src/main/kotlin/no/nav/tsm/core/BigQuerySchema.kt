package no.nav.tsm.core

import com.google.cloud.bigquery.*
import no.nav.tsm.ktor.logger

private val logger = logger()

fun migrateDatabase(env: GcpConfig) {
    val bigQuery = BigQueryOptions.newBuilder()
        .setProjectId(env.projectId)
        .setLocation("europe-north1")
        .build().service

    val table = bigQuery.getTable(MIGRATIONS_TABLE)
    if (table == null) createMigrationsTable(bigQuery)

    val currentSchemaVersion = bigQuery.getCurrentSchemaVersion()
    logger.info("Current schema version: $currentSchemaVersion")

    when (currentSchemaVersion) {
        // TODO: Håndtere manglende fall-throughs
        0L -> v1InitialSchema(bigQuery)
    }
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

private fun v1InitialSchema(bigQuery: BigQuery) {
    logger.info("Migrating to schema version 1")

    val schema = Schema.of(
        nonNullableField("id", StandardSQLTypeName.STRING),
        nonNullableField("fom", StandardSQLTypeName.DATE),
        nonNullableField("tom", StandardSQLTypeName.DATE),
        nonNullableField("type", StandardSQLTypeName.STRING),
        nonNullableField("generert_dato", StandardSQLTypeName.DATETIME),
        nonNullableField("nav_mottatt_dato", StandardSQLTypeName.DATETIME),
        nonNullableField("regel_utfall", StandardSQLTypeName.STRING),
        field("avsender_system", StandardSQLTypeName.STRING),
        field("avsender_version", StandardSQLTypeName.STRING),
        field("regelsett_versjon", StandardSQLTypeName.STRING),
        field("behandler_hpr_nr", StandardSQLTypeName.STRING),
        field("behandler_her_id", StandardSQLTypeName.STRING),
        field("behandler_hpr_kategori", StandardSQLTypeName.STRING),
        field("behandler_ident_sha256", StandardSQLTypeName.STRING),
        nonNullableField("pasient_ident_sha256", StandardSQLTypeName.STRING),
    )

    val tableConstraint = TableConstraints.newBuilder().setPrimaryKey(
        PrimaryKey.newBuilder().setColumns(listOf("id")).build()
    ).build()

    val tableDefinition = StandardTableDefinition.newBuilder()
        .setSchema(schema)
        .setTimePartitioning(TimePartitioning.newBuilder(TimePartitioning.Type.MONTH).setField("generert_dato").build())
        .setTableConstraints(tableConstraint)
        .build()

    val tableInfo = TableInfo
        .newBuilder(SYKMELDINGER_TABLE, tableDefinition)
        .build()

    bigQuery.create(tableInfo)
    bigQuery.insertSchemaVersion(1)

    logger.info("Migration to schema version 1 completed")
}
