package no.nav.tsm.core.bq

import com.google.cloud.bigquery.TableId
import no.nav.tsm.sykmelding.input.core.model.MedisinskVurdering
import no.nav.tsm.sykmelding.input.core.model.Sykmelding
import no.nav.tsm.sykmelding.input.core.model.SykmeldingMeta
import no.nav.tsm.sykmelding.input.core.model.SykmeldingRecord
import no.nav.tsm.sykmelding.input.core.model.metadata.MessageMetadata
import no.nav.tsm.sykmelding.input.core.model.metadata.OrgIdType
import no.nav.tsm.sykmelding.input.core.model.metadata.PersonIdType

val SYKMELDINGER_TABLE: TableId = TableId.of(DATASET_ID, "sykmeldinger")
val SYKMELDINGER_UTENLANDSK_TABLE: TableId = TableId.of(DATASET_ID, "sykmeldinger_utenlandsk")

/**
 * All common values between utenlandsk and nasjonal.
 */
private fun SykmeldingRecord.toCommonBqPayload(): BqPayload {
    val payload = BqPayload(
        "id" to BqTypes.String(sykmelding.id),
        "fom" to BqTypes.Date(sykmelding.aktivitet.minOf { aktivitet -> aktivitet.fom }),
        "tom" to BqTypes.Date(sykmelding.aktivitet.maxOf { aktivitet -> aktivitet.tom }),
        "type" to BqTypes.String(sykmelding.type.name),
        "regelutfall" to BqTypes.String(validation.status.name),
        "aktivitet" to BqTypes.JsonArray(sykmelding.aktivitet),
        "meta_generert_dato" to BqTypes.DateTime(sykmelding.metadata.genDate),
        "meta_nav_mottatt_dato" to BqTypes.DateTime(sykmelding.metadata.mottattDato),
        "meta_avsender_system" to BqTypes.String(sykmelding.metadata.avsenderSystem.navn),
        "meta_avsender_version" to BqTypes.String(sykmelding.metadata.avsenderSystem.versjon),
        "pasient_ident_sha256" to BqTypes.String(sykmelding.pasient.fnr.shaIt()),
        "hoveddiagnose_kode" to BqTypes.String.Nullable(sykmelding.medisinskVurdering.hovedDiagnose?.kode),
        "hoveddiagnose_system" to BqTypes.Enum.Nullable(sykmelding.medisinskVurdering.hovedDiagnose?.system),
        "hoveddiagnose_tekst" to BqTypes.String.Nullable(sykmelding.medisinskVurdering.hovedDiagnose?.tekst),
        "bidiagnoser" to BqTypes.RecordArray.Nullable(sykmelding.medisinskVurdering.biDiagnoser?.map {
            BqPayload(
                "kode" to BqTypes.String(it.kode),
                "system" to BqTypes.Enum(it.system),
                "tekst" to BqTypes.String.Nullable(it.tekst),
            )
        }),
        "svangerskapsrelatert" to BqTypes.Boolean.Nullable(sykmelding.medisinskVurdering.svangerskap),
        "skjermet_for_pasient" to BqTypes.Boolean.Nullable(sykmelding.medisinskVurdering.skjermetForPasient),
        "yrkesskadedato" to BqTypes.Date.Nullable(sykmelding.medisinskVurdering.yrkesskade?.yrkesskadeDato),
    )

    when (val mv = this.sykmelding.medisinskVurdering) {
        is MedisinskVurdering.Digital -> payload.add(
            "annen_fravers_arsak" to BqTypes.Enum.Nullable(mv.annenFravarsgrunn)
        )

        is MedisinskVurdering.Legacy -> payload.add(
            "syketilfellet_start_dato" to BqTypes.Date.Nullable(mv.syketilfelletStartDato),
            "annen_fravers_arsak" to BqTypes.Enum.Nullable(mv.annenFraversArsak?.arsak?.firstOrNull())
        )
    }

    when (val rm = this.metadata) {
        is MessageMetadata.Digital -> payload.add(
            "meta_orgnummer" to BqTypes.String.Nullable(rm.orgnummer)
        )

        is MessageMetadata.Papir -> payload.add(
            "meta_orgnummer" to BqTypes.String.Nullable(rm.sender.ids.find { it.type == OrgIdType.ENH }?.id)
        )

        is MessageMetadata.Xml.Emottak -> payload.add(
            "meta_orgnummer" to BqTypes.String.Nullable(rm.sender.ids.find { it.type == OrgIdType.ENH }?.id)
        )

        is MessageMetadata.Xml.Egenmeldt -> {}
        is MessageMetadata.Utenlandsk -> {}
    }

    when (val sm = sykmelding.metadata) {
        is SykmeldingMeta.Legacy -> payload.add(
            "meta_regelsett_versjon" to BqTypes.String.Nullable(sm.regelsettVersjon),
        )

        is SykmeldingMeta.Digital -> {}
    }

    return payload
}

fun SykmeldingRecord.Utenlandsk.toUtenlandskBqPayload(): BqPayload = this.toCommonBqPayload()
    .add(
        "meta_land" to BqTypes.String(sykmelding.utenlandskInfo.land)
    )

fun SykmeldingRecord.toNasjonalBqPayload(): BqPayload {
    val sykmelding = this.sykmelding
    if (sykmelding !is Sykmelding.Nasjonal) {
        throw IllegalArgumentException("Cannot convert Utenlandsk sykmelding to Nasjonal row, use toUtenlandskBqPayload() instead")
    }

    val payload = this.toCommonBqPayload().add(
        "behandler_hpr_nr" to BqTypes.String.Nullable(sykmelding.behandler.ids.find { it.type == PersonIdType.HPR }?.id?.trim()),
        "behandler_her_id" to BqTypes.String.Nullable(sykmelding.behandler.ids.find { it.type == PersonIdType.HER }?.id?.trim()),
        "behandler_ident_sha256" to BqTypes.String.Nullable(sykmelding.behandler.ids.find { it.type == PersonIdType.FNR || it.type == PersonIdType.DNR }?.id?.shaIt()),
        "behandler_hpr_kategori" to BqTypes.Enum(sykmelding.sykmelder.helsepersonellKategori),
    )

    return payload
}
