package no.nav.tsm.core.bq.migrations

import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.PrimaryKey
import com.google.cloud.bigquery.Schema
import com.google.cloud.bigquery.StandardSQLTypeName
import com.google.cloud.bigquery.StandardTableDefinition
import com.google.cloud.bigquery.TableConstraints
import com.google.cloud.bigquery.TableInfo
import com.google.cloud.bigquery.TimePartitioning
import no.nav.tsm.core.bq.SYKMELDINGER_TABLE
import no.nav.tsm.core.bq.field
import no.nav.tsm.core.bq.nonNullableField
import no.nav.tsm.ktor.logger

private val logger = logger()

fun v1InitialSchema(bigQuery: BigQuery) {
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