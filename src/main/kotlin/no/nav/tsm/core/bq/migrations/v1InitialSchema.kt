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
import no.nav.tsm.core.bq.SYKMELDINGER_UTENLANDSK_TABLE
import no.nav.tsm.core.bq.field
import no.nav.tsm.core.bq.nonNullableField
import no.nav.tsm.core.bq.repeated
import no.nav.tsm.ktor.logger

private val logger = logger()

val baseSchema = listOf(
    nonNullableField("id", StandardSQLTypeName.STRING),
    nonNullableField("fom", StandardSQLTypeName.DATE),
    nonNullableField("tom", StandardSQLTypeName.DATE),
    nonNullableField("type", StandardSQLTypeName.STRING),
    nonNullableField("regelutfall", StandardSQLTypeName.STRING),
    // Metadata
    nonNullableField("meta_generert_dato", StandardSQLTypeName.DATETIME),
    nonNullableField("meta_nav_mottatt_dato", StandardSQLTypeName.DATETIME),
    field("meta_avsender_system", StandardSQLTypeName.STRING),
    field("meta_avsender_version", StandardSQLTypeName.STRING),
    field("meta_regelsett_versjon", StandardSQLTypeName.STRING),
    // Pasient
    nonNullableField("pasient_ident_sha256", StandardSQLTypeName.STRING),
    // Medisinsk vurdering
    field("hoveddiagnose_kode", StandardSQLTypeName.STRING),
    field("hoveddiagnose_system", StandardSQLTypeName.STRING),
    field("hoveddiagnose_tekst", StandardSQLTypeName.STRING),
    repeated(
        "bidiagnoser",
        nonNullableField("kode", StandardSQLTypeName.STRING),
        nonNullableField("system", StandardSQLTypeName.STRING),
        field("tekst", StandardSQLTypeName.STRING)
    ),
    field("svangerskapsrelatert", StandardSQLTypeName.BOOL),
    field("skjermet_for_pasient", StandardSQLTypeName.BOOL),
    field("yrkesskadedato", StandardSQLTypeName.DATE),
    field("annen_fravers_arsak", StandardSQLTypeName.STRING),
    field("syketilfellet_start_dato", StandardSQLTypeName.STRING),
    // Aktivitet
    field("aktivitet", StandardSQLTypeName.JSON),
)

val nasjonalSykmeldingSchema = Schema.of(
    baseSchema + listOf(
        // Metadata
        field("meta_orgnummer", StandardSQLTypeName.STRING),
        // Behandler
        field("behandler_hpr_nr", StandardSQLTypeName.STRING),
        field("behandler_her_id", StandardSQLTypeName.STRING),
        field("behandler_hpr_kategori", StandardSQLTypeName.STRING),
        field("behandler_ident_sha256", StandardSQLTypeName.STRING),
    )
)

val utenlandskSykmeldingSchema = Schema.of(
    baseSchema + listOf(
        nonNullableField("meta_land", StandardSQLTypeName.STRING),
    )
)

fun v1InitialSchema(bigQuery: BigQuery) {
    logger.info("Migrating to schema version 1")

    val tableConstraint = TableConstraints.newBuilder().setPrimaryKey(
        PrimaryKey.newBuilder().setColumns(listOf("id")).build()
    ).build()
    val timePartitioning = TimePartitioning
        .newBuilder(TimePartitioning.Type.MONTH)
        .setField("meta_generert_dato")
        .build()

    val nasjonalTableDefinition = StandardTableDefinition.newBuilder()
        .setSchema(nasjonalSykmeldingSchema)
        .setTimePartitioning(timePartitioning)
        .setTableConstraints(tableConstraint)
        .build()

    val utenlandskTableDefinition = StandardTableDefinition.newBuilder()
        .setSchema(utenlandskSykmeldingSchema)
        .setTimePartitioning(timePartitioning)
        .setTableConstraints(tableConstraint)
        .build()

    bigQuery.create(TableInfo.newBuilder(SYKMELDINGER_TABLE, nasjonalTableDefinition).build())
    bigQuery.create(TableInfo.newBuilder(SYKMELDINGER_UTENLANDSK_TABLE, utenlandskTableDefinition).build())
    bigQuery.insertSchemaVersion(1)
}
