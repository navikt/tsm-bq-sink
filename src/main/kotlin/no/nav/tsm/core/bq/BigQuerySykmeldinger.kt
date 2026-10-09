package no.nav.tsm.core.bq

import com.google.cloud.bigquery.TableId
import no.nav.tsm.sykmelding.input.core.model.MedisinskVurdering
import no.nav.tsm.sykmelding.input.core.model.Sykmelding
import no.nav.tsm.sykmelding.input.core.model.SykmeldingMeta
import no.nav.tsm.sykmelding.input.core.model.SykmeldingRecord
import no.nav.tsm.sykmelding.input.core.model.metadata.MessageMetadata
import no.nav.tsm.sykmelding.input.core.model.metadata.OrgIdType
import no.nav.tsm.sykmelding.input.core.model.metadata.PersonIdType
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

val SYKMELDINGER_TABLE: TableId = TableId.of(DATASET_ID, "sykmeldinger")
val UTENLANDSK_SYKMELDINGER_TABLE: TableId = TableId.of(DATASET_ID, "sykmeldinger_utenlandsk")

fun SykmeldingRecord.toBigQueryRow(): Map<String, Any?> {
    val sykmelding = this.sykmelding
    val medisinskVurdering = this.sykmelding.medisinskVurdering
    val sykmeldingMeta = this.sykmelding.metadata
    val recordMeta = this.metadata

    return mapOf(
        "id" to sykmelding.id,
        "fom" to sykmelding.aktivitet.minOf { aktivitet -> aktivitet.fom }.toString(),
        "tom" to sykmelding.aktivitet.maxOf { aktivitet -> aktivitet.tom }.toString(),
        "type" to sykmelding.type.name,
        "regelutfall" to validation.status.name,
        "meta_generert_dato" to sykmelding.metadata.genDate.bqDateTime(),
        "meta_nav_mottatt_dato" to sykmelding.metadata.mottattDato.bqDateTime(),
        "meta_avsender_system" to sykmelding.metadata.avsenderSystem.navn,
        "meta_avsender_version" to sykmelding.metadata.avsenderSystem.versjon,
        "pasient_ident_sha256" to sykmelding.pasient.fnr.shaIt(),
        "hoveddiagnose_kode" to sykmelding.medisinskVurdering.hovedDiagnose?.kode,
        "hoveddiagnose_system" to sykmelding.medisinskVurdering.hovedDiagnose?.system,
        "hoveddiagnose_tekst" to sykmelding.medisinskVurdering.hovedDiagnose?.tekst,
        "bidiagonser" to sykmelding.medisinskVurdering.biDiagnoser,
        "svangerskapsrelatert" to sykmelding.medisinskVurdering.svangerskap,
        "skjermet_for_pasient" to sykmelding.medisinskVurdering.skjermetForPasient,
        "yrkesskadedato" to sykmelding.medisinskVurdering.yrkesskade?.yrkesskadeDato,
    ) + when(medisinskVurdering) {
        is MedisinskVurdering.Digital -> mapOf(
            "annen_fravers_arsak" to medisinskVurdering.annenFravarsgrunn?.name
        )
        is MedisinskVurdering.Legacy -> mapOf(
            "syketilfellet_start_dato" to medisinskVurdering.syketilfelletStartDato.toString(),
            "annen_fravers_arsak" to medisinskVurdering.annenFraversArsak?.arsak?.first()?.name
        )
    } + when (recordMeta) {
        is MessageMetadata.Digital -> mapOf(
            "meta_orgnummer" to recordMeta.orgnummer
        )

        is MessageMetadata.Papir -> mapOf(
            "orgnummer" to recordMeta.sender.ids.find { it.type == OrgIdType.ENH }?.id
        )

        is MessageMetadata.Xml.Emottak -> mapOf(
            "orgnummer" to recordMeta.sender.ids.find { it.type == OrgIdType.ENH }?.id
        )

        is MessageMetadata.Utenlandsk -> emptyMap()
        is MessageMetadata.Xml.Egenmeldt -> emptyMap()
    } + when (sykmelding) {
        is Sykmelding.Nasjonal -> {
            mapOf(
                "behandler_hpr_nr" to sykmelding.behandler.ids.find { it.type == PersonIdType.HPR }?.id,
                "behandler_her_id" to sykmelding.behandler.ids.find { it.type == PersonIdType.HER }?.id,
                "behandler_ident_sha256" to sykmelding.behandler.ids.find { it.type == PersonIdType.FNR || it.type == PersonIdType.DNR }?.id?.shaIt(),
                "behandler_hpr_kategori" to sykmelding.sykmelder.helsepersonellKategori.name,
            )
        }

        is Sykmelding.Utenlandsk -> emptyMap()
    } + when(sykmeldingMeta) {
        is SykmeldingMeta.Legacy -> mapOf(
            "meta_regelsett_versjon" to sykmeldingMeta.regelsettVersjon,
        )
        is SykmeldingMeta.Digital -> emptyMap()
    }
}

/**
 * Used for idents (fnr/dnr) to hash them before storing in BigQuery
 */
private fun String.shaIt(): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))

    return HexFormat.of().formatHex(digest)
}

private fun OffsetDateTime.bqDateTime() = atZoneSameInstant(ZoneOffset.UTC)
    .truncatedTo(ChronoUnit.SECONDS)
    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
