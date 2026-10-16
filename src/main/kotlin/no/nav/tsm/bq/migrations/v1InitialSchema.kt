package no.nav.tsm.bq.migrations

import com.google.cloud.bigquery.*
import no.nav.tsm.bq.*
import no.nav.tsm.ktor.logger
import no.nav.tsm.sykmelding.SYKMELDINGER_TABLE
import no.nav.tsm.sykmelding.SYKMELDINGER_UTENLANDSK_TABLE

private val logger = logger()

val baseSchema = listOf(
    nonNullableField("id", StandardSQLTypeName.STRING, "Sykmelding ID"),
    nonNullableField("fom", StandardSQLTypeName.DATE, "Tidligste fom-dato av alle perioder"),
    nonNullableField("tom", StandardSQLTypeName.DATE, "Siste tom-dato av alle perioder"),
    nonNullableField("type", StandardSQLTypeName.STRING, "Enum: DIGITAL, XML, PAPIR, UTENLANDSK"),
    // Regelutfall
    nonNullableField("regelutfall", StandardSQLTypeName.STRING, "Siste regelutfall i regelutfall_historikk"),
    nonNullableField("regelutfall_timestamp", StandardSQLTypeName.DATETIME),
    arrayRecord(
        "regelutfall_historikk",
        nonNullableField("type", StandardSQLTypeName.STRING),
        nonNullableField("timestamp", StandardSQLTypeName.DATETIME),
        nonNullableField("status", StandardSQLTypeName.STRING),
        nullableField("reason_sykmelder", StandardSQLTypeName.STRING),
        nullableField("reason_sykmeldt", StandardSQLTypeName.STRING),
        description = "Alle regelutfall som har blitt generert for denne sykmeldingen, i kronologisk rekkefølge, den siste (gjeldene) er dratt ut i regelutfall-kolonnen"
    ),
    // Kafka metadata used for de-duplication and ordering
    nonNullableField("kafka_partition", StandardSQLTypeName.INT64),
    nonNullableField("kafka_offset", StandardSQLTypeName.INT64),
    nonNullableField("kafka_epoch", StandardSQLTypeName.INT64),
    // Metadata
    nonNullableField(
        "meta_generert_dato",
        StandardSQLTypeName.DATETIME,
        "Tidspunktet er satt av EPJ-systemet ved innsending, for eksempel når sykmeldingen ble skrevet."
    ),
    nonNullableField(
        "meta_nav_mottatt_dato",
        StandardSQLTypeName.DATETIME,
        "Tidspunkt er generert når meldingen ble prossessert av syfosmmottak eller syk-inn-api"
    ),
    nonNullableField(
        "meta_avsender_system",
        StandardSQLTypeName.STRING,
        "Navnet på EPJ-systemet som sendte inn. For systemer som sender inn via ny løsning vil det være for eksempel: 'EPJ-Navn (FHIR)'"
    ),
    nonNullableField(
        "meta_avsender_version",
        StandardSQLTypeName.STRING,
        "Versjon av EPJ-systemet som genererte sykmeldingen"
    ),
    // Pasient
    nonNullableField(
        "pasient_ident_sha256",
        StandardSQLTypeName.STRING,
        "HMAC-SHA256 av ident (fnr/dnr). Trenger du å gå fra ident→hash kan du bruke en feature i Zara for det."
    ),
    // Medisinsk vurdering
    nullableField(
        "hoveddiagnose_kode",
        StandardSQLTypeName.STRING,
        "Diagnosekode for hoveddiagnose, for eksempel P01, Z05, etc."
    ),
    nullableField(
        "hoveddiagnose_system",
        StandardSQLTypeName.STRING,
        "Kodeverk for hoveddiagnose, for eksempel ICPC2, ICD10, ICPC2B"
    ),
    nullableField(
        "hoveddiagnose_tekst",
        StandardSQLTypeName.STRING,
        "Tekstlig beskrivelse av hoveddiagnosen, kan variere stort eller være utdarert, er satt av EPJ i gammelt flyt, eller fra vår egen diagnosekode mapping i ny flyt."
    ),
    arrayRecord(
        "bidiagnoser",
        nonNullableField("kode", StandardSQLTypeName.STRING),
        nonNullableField("system", StandardSQLTypeName.STRING),
        nullableField("tekst", StandardSQLTypeName.STRING),
    ),
    nonNullableField("svangerskapsrelatert", StandardSQLTypeName.BOOL),
    nonNullableField("skjermet_for_pasient", StandardSQLTypeName.BOOL),
    nullableField("yrkesskadedato", StandardSQLTypeName.DATE),
    nullableField("syketilfelle_start_dato", StandardSQLTypeName.DATE),
    nullableField("annen_fravers_arsak", StandardSQLTypeName.STRING),
    nullableField("annen_fravers_arsak_beskrivelse", StandardSQLTypeName.STRING),
    // Aktivitet
    arrayRecord(
        "perioder",
        nonNullableField(
            "type",
            StandardSQLTypeName.STRING,
            "Enum: AKTIVITET_IKKE_MULIG, AVVENTENDE, BEHANDLINGSDAGER, GRADERT, REISETILSKUDD"
        ),
        nonNullableField("fom", StandardSQLTypeName.DATE),
        nonNullableField("tom", StandardSQLTypeName.DATE),
        nullableField("medisinsk_arsak_beskrivelse", StandardSQLTypeName.STRING),
        nullableField("medisinsk_arsak_arsak", StandardSQLTypeName.STRING),
        nullableField("arbeidsrelatert_arsak", StandardSQLTypeName.STRING),
        nullableField("arbeidsrelatert_arsak_beskrivelse", StandardSQLTypeName.STRING),
        nullableField(
            "enkeltstaende_behandlingsdager",
            StandardSQLTypeName.INT64,
            "Må ikke blandes med 'behandlingsdager' annen fraværs årsak type = BEHANDLING_FORHINDRER_ARBEID"
        ),
        nullableField("grad", StandardSQLTypeName.INT64),
        nullableField("gradert_reisetilskudd", StandardSQLTypeName.BOOL, "Ikke til å blandes med "),
        nullableField("avventene_innspill_til_arbeidsgiver", StandardSQLTypeName.STRING),
        description = "Flat versjon av alle 5 periode-typer, grad vil for eksempel kun være satt når type = GRADERT, medisinsk_arsak og arbeidsrelatert_arsak vil kun være satt når type = AKTIVITET_IKKE_MULIG, etc."
    ),
)

val nasjonalSykmeldingSchema = Schema.of(
    baseSchema + listOf(
        // Metadata
        nullableField(
            "meta_regelsett_versjon",
            StandardSQLTypeName.STRING,
            "Eksplisitt satt i XML i gammel flyt, kan være null eller 1-3. I ny løsning er den ikke satt."
        ),
        nullableField("meta_orgnummer", StandardSQLTypeName.STRING),
        // Sykmelder (signerende)
        nullableField(
            "sykmelder_hpr_nr",
            StandardSQLTypeName.STRING,
            "'Signerende' sykmelder sitt HPR nummer, dette er helsepersonellet i HPR som blir regel-eksekvert."
        ),
        nullableField("sykmelder_her_id", StandardSQLTypeName.STRING),
        nullableField("sykmelder_ident_sha256", StandardSQLTypeName.STRING),
        nullableField("sykmelder_hpr_kategori", StandardSQLTypeName.STRING),
        // Behandler
        nullableField(
            "behandler_hpr_nr",
            StandardSQLTypeName.STRING,
            "HPR nummer for den som har skrevet sykmeldingen, kan være forskjellig fra sykmelder_hpr_nr dersom sykmeldingen er signert av en annen sykmelder."
        ),
        nullableField("behandler_her_id", StandardSQLTypeName.STRING),
        nullableField("behandler_ident_sha256", StandardSQLTypeName.STRING),
        // Arbeidsgiver
        nonNullableField(
            "arbeidsgiver_antall_type",
            StandardSQLTypeName.STRING,
            "Enum: EN, FLERE, INGEN. Har ikke så mye vekt i videre innsending av sykmleding. Ny flyt har implisitte verdier basert på andre felter. Bør brukes med omhu."
        ),
        nullableField("arbeidsgiver_navn", StandardSQLTypeName.STRING),
        nullableField("arbeidsgiver_melding_til_arbeidsgiver", StandardSQLTypeName.STRING),
        nullableField("arbeidsgiver_tiltak_arbeidsplassen", StandardSQLTypeName.STRING),
        // Bistand nav
        nullableField("bistand_nav_umiddelbart", StandardSQLTypeName.BOOL),
        nullableField("bistand_nav_beskrivelse", StandardSQLTypeName.STRING),
        // Tilbakedatering
        nullableField("tilbakedatering_kontaktdato", StandardSQLTypeName.DATE),
        nullableField("tilbakedatering_begrunnelse", StandardSQLTypeName.STRING),
        // Prognose
        nullableField("prognose_arbeidsfor_etter_periode", StandardSQLTypeName.BOOL),
        nullableField("prognose_hensyn_arbeidsplassen", StandardSQLTypeName.STRING),
        nullableField("prognose_arbeid_type", StandardSQLTypeName.STRING),
        nullableField("prognose_arbeid_vurderingsdato", StandardSQLTypeName.DATE),
        nullableField("prognose_arbeid_ikke_i_arbeid_arbeidsfor_pa_sikt", StandardSQLTypeName.BOOL),
        nullableField("prognose_arbeid_ikke_i_arbeid_arbeidsfor_fom", StandardSQLTypeName.DATE),
        nullableField("prognose_arbeid_i_arbeid_eget_arbeid_pa_sikt", StandardSQLTypeName.BOOL),
        nullableField(
            "prognose_arbeid_i_arbeid_annet_arbeid_pa_sikt",
            StandardSQLTypeName.BOOL,
            "'Annet arbeid på sikt', i ny løsning mappes 'friskmelding til arbeidsfomidling' inn her."
        ),
        nullableField("prognose_arbeid_i_arbeid_arbeid_fom", StandardSQLTypeName.DATE),
        // Tiltak
        nullableField("tiltak_nav", StandardSQLTypeName.STRING),
        nullableField("tiltak_andre", StandardSQLTypeName.STRING),
        // Utdypende spørsmål
        arrayRecord(
            "utdypende_sporsmal",
            nonNullableField("type", StandardSQLTypeName.STRING),
            nonNullableField("svar", StandardSQLTypeName.STRING),
            nonNullableField("skjermetForArbeidsgiver", StandardSQLTypeName.BOOL),
            nullableField("sporsmal", StandardSQLTypeName.STRING),
            nullableField("feltnummer", StandardSQLTypeName.STRING),
        )
    )
)

val utenlandskSykmeldingSchema = Schema.of(
    baseSchema + listOf(
        nonNullableField("meta_land", StandardSQLTypeName.STRING),
        nonNullableField("meta_folkeregistertadresse_er_brakke_eller_tilsvarende", StandardSQLTypeName.BOOL),
        nullableField("meta_er_adresse_utland", StandardSQLTypeName.BOOL),
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

    val regelutfallClustering = Clustering.newBuilder()
        .setFields(listOf("regelutfall", "type", "meta_avsender_system"))
        .build()

    val utenlandskTableDefinition = StandardTableDefinition.newBuilder()
        .setSchema(utenlandskSykmeldingSchema)
        .setTimePartitioning(timePartitioning)
        .setTableConstraints(tableConstraint)
        .setClustering(regelutfallClustering)
        .build()

    bigQuery.create(TableInfo.newBuilder(SYKMELDINGER_TABLE, nasjonalTableDefinition).build())
    bigQuery.create(TableInfo.newBuilder(SYKMELDINGER_UTENLANDSK_TABLE, utenlandskTableDefinition).build())
}
