package no.nav.tsm.bq.migrations

import com.google.cloud.bigquery.*
import no.nav.tsm.bq.*
import no.nav.tsm.ktor.logger
import no.nav.tsm.sykmelding.SYKMELDINGER_TABLE
import no.nav.tsm.sykmelding.SYKMELDINGER_UTENLANDSK_TABLE

private val logger = logger()

val baseSchema = listOf(
    nonNullableField("id", StandardSQLTypeName.STRING),
    nonNullableField("fom", StandardSQLTypeName.DATE),
    nonNullableField("tom", StandardSQLTypeName.DATE),
    nonNullableField("type", StandardSQLTypeName.STRING),
    nonNullableField("regelutfall", StandardSQLTypeName.STRING),
    // Kafka metadata used for de-duplication and ordering
    nonNullableField("kafka_partition", StandardSQLTypeName.INT64),
    nonNullableField("kafka_offset", StandardSQLTypeName.INT64),
    nonNullableField("kafka_epoch", StandardSQLTypeName.INT64),
    // Metadata
    nonNullableField("meta_generert_dato", StandardSQLTypeName.DATETIME),
    nonNullableField("meta_nav_mottatt_dato", StandardSQLTypeName.DATETIME),
    nonNullableField("meta_avsender_system", StandardSQLTypeName.STRING),
    nonNullableField("meta_avsender_version", StandardSQLTypeName.STRING),
    // Pasient
    nonNullableField("pasient_ident_sha256", StandardSQLTypeName.STRING),
    // Medisinsk vurdering
    nullableField("hoveddiagnose_kode", StandardSQLTypeName.STRING),
    nullableField("hoveddiagnose_system", StandardSQLTypeName.STRING),
    nullableField("hoveddiagnose_tekst", StandardSQLTypeName.STRING),
    arrayRecord(
        "bidiagnoser",
        nonNullableField("kode", StandardSQLTypeName.STRING),
        nonNullableField("system", StandardSQLTypeName.STRING),
        nullableField("tekst", StandardSQLTypeName.STRING)
    ),
    nonNullableField("svangerskapsrelatert", StandardSQLTypeName.BOOL),
    nonNullableField("skjermet_for_pasient", StandardSQLTypeName.BOOL),
    nullableField("yrkesskadedato", StandardSQLTypeName.DATE),
    nullableField("syketilfelle_start_dato", StandardSQLTypeName.STRING),
    nullableField("annen_fravers_arsak", StandardSQLTypeName.STRING),
    nullableField("annen_fravers_arsak_beskrivelse", StandardSQLTypeName.STRING),
    // Aktivitet
    arrayRecord(
        "perioder",
        nonNullableField("type", StandardSQLTypeName.STRING),
        nonNullableField("fom", StandardSQLTypeName.DATE),
        nonNullableField("tom", StandardSQLTypeName.DATE),
        nullableField("medisinsk_arsak_beskrivelse", StandardSQLTypeName.STRING),
        nullableField("medisinsk_arsak_arsak", StandardSQLTypeName.STRING),
        nullableField("arbeidsrelatert_arsak", StandardSQLTypeName.STRING),
        nullableField("arbeidsrelatert_arsak_beskrivelse", StandardSQLTypeName.STRING),
        nullableField("enkeltstaende_behandlingsdager", StandardSQLTypeName.INT64),
        nullableField("grad", StandardSQLTypeName.INT64),
        nullableField("gradert_reisetilskudd", StandardSQLTypeName.BOOL),
        nullableField("avventene_innspill_til_arbeidsgiver", StandardSQLTypeName.STRING),
    ),
)

val nasjonalSykmeldingSchema = Schema.of(
    baseSchema + listOf(
        // Metadata
        nullableField("meta_regelsett_versjon", StandardSQLTypeName.STRING),
        nullableField("meta_orgnummer", StandardSQLTypeName.STRING),
        // Sykmelder (signerende)
        nonNullableField("sykmelder_hpr_nr", StandardSQLTypeName.STRING),
        nullableField("sykmelder_her_id", StandardSQLTypeName.STRING),
        nullableField("sykmelder_ident_sha256", StandardSQLTypeName.STRING),
        nonNullableField("sykmelder_hpr_kategori", StandardSQLTypeName.STRING),
        // Behandler
        nullableField("behandler_hpr_nr", StandardSQLTypeName.STRING),
        nullableField("behandler_her_id", StandardSQLTypeName.STRING),
        nullableField("behandler_ident_sha256", StandardSQLTypeName.STRING),
    )
)

val utenlandskSykmeldingSchema = Schema.of(
    baseSchema + listOf(
        nonNullableField("meta_land", StandardSQLTypeName.STRING),
    )
)

/**
 * Do NOT edit this function. Any new changes NEEDS to be done in a new changeset.
 *
 * Pretend this is a flyway migration.
 */
fun v1InitialSchema(bq: BigQuery) {
    logger.info("Migrating to schema version 1")

    createSykmeldingerTable(bq)

    bq.insertSchemaVersion(1)
}

private fun createSykmeldingerTable(bigQuery: BigQuery) {
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
}
