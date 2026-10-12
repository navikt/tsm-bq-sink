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

fun SykmeldingRecord.Utenlandsk.toUtenlandskBqPayload(): BqPayload {
    return BqPayload(
        "id" to BqTypes.String(sykmelding.id),
        "fom" to BqTypes.Date(sykmelding.aktivitet.minOf { aktivitet -> aktivitet.fom }),
        "tom" to BqTypes.Date(sykmelding.aktivitet.maxOf { aktivitet -> aktivitet.tom }),
        "type" to BqTypes.String(sykmelding.type.name),
        "regelutfall" to BqTypes.String(validation.status.name),
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
        "meta_land" to BqTypes.String(sykmelding.utenlandskInfo.land),
    )
}

fun SykmeldingRecord.toNasjonalBqPayload(): BqPayload {
    val sykmelding = this.sykmelding
    if (sykmelding !is Sykmelding.Nasjonal) {
        throw IllegalArgumentException("Cannot convert Utenlandsk sykmelding to Nasjonal row, use toUtenlandskBqPayload() instead")
    }

    val recordMeta = this.metadata
    if (recordMeta is MessageMetadata.Utenlandsk) {
        throw IllegalArgumentException("Cannot convert Utenlandsk sykmelding to Nasjonal row, use toUtenlandskBqPayload() instead")
    }

    val medisinskVurdering = this.sykmelding.medisinskVurdering
    val sykmeldingMeta = this.sykmelding.metadata

    val base = BqPayload(
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
                "tekst" to BqTypes.String.Nullable(it.tekst)
            )
        }),
        "svangerskapsrelatert" to BqTypes.Boolean(sykmelding.medisinskVurdering.svangerskap),
        "skjermet_for_pasient" to BqTypes.Boolean(sykmelding.medisinskVurdering.skjermetForPasient),
        "yrkesskadedato" to BqTypes.Date.Nullable(sykmelding.medisinskVurdering.yrkesskade?.yrkesskadeDato),
        "behandler_hpr_nr" to BqTypes.String.Nullable(sykmelding.behandler.ids.find { it.type == PersonIdType.HPR }?.id),
        "behandler_her_id" to BqTypes.String.Nullable(sykmelding.behandler.ids.find { it.type == PersonIdType.HER }?.id),
        "behandler_ident_sha256" to BqTypes.String.Nullable(sykmelding.behandler.ids.find { it.type == PersonIdType.FNR || it.type == PersonIdType.DNR }?.id?.shaIt()),
        "behandler_hpr_kategori" to BqTypes.String(sykmelding.sykmelder.helsepersonellKategori.name),
    )

    when (medisinskVurdering) {
        is MedisinskVurdering.Digital -> base.add(
            "annen_fravers_arsak" to BqTypes.Enum.Nullable(medisinskVurdering.annenFravarsgrunn)
        )

        is MedisinskVurdering.Legacy -> base.add(
            "syketilfellet_start_dato" to BqTypes.Date.Nullable(medisinskVurdering.syketilfelletStartDato),
            "annen_fravers_arsak" to BqTypes.Enum.Nullable(medisinskVurdering.annenFraversArsak?.arsak?.firstOrNull())
        )
    }

    when (recordMeta) {
        is MessageMetadata.Digital -> base.add(
            "meta_orgnummer" to BqTypes.String.Nullable(recordMeta.orgnummer)
        )

        is MessageMetadata.Papir -> base.add(
            "meta_orgnummer" to BqTypes.String.Nullable(recordMeta.sender.ids.find { it.type == OrgIdType.ENH }?.id)
        )

        is MessageMetadata.Xml.Emottak -> base.add(
            "meta_orgnummer" to BqTypes.String.Nullable(recordMeta.sender.ids.find { it.type == OrgIdType.ENH }?.id)
        )

        is MessageMetadata.Xml.Egenmeldt -> {}
    }

    when (sykmeldingMeta) {
        is SykmeldingMeta.Legacy -> base.add(
            "meta_regelsett_versjon" to BqTypes.String.Nullable(sykmeldingMeta.regelsettVersjon),
        )

        is SykmeldingMeta.Digital -> {}
    }

    return base
}
