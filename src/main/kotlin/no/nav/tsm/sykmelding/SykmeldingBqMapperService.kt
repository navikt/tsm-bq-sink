package no.nav.tsm.sykmelding

import com.google.cloud.bigquery.TableId
import no.nav.tsm.bq.BqPayload
import no.nav.tsm.bq.BqTypes
import no.nav.tsm.bq.DATASET_ID
import no.nav.tsm.ktor.kafka.consumer.RecordMeta
import no.nav.tsm.sykmelding.input.core.model.*
import no.nav.tsm.sykmelding.input.core.model.metadata.MessageMetadata
import no.nav.tsm.sykmelding.input.core.model.metadata.OrgIdType
import no.nav.tsm.sykmelding.input.core.model.metadata.PersonIdType

val SYKMELDINGER_TABLE: TableId = TableId.of(DATASET_ID, "sykmeldinger")
val SYKMELDINGER_UTENLANDSK_TABLE: TableId = TableId.of(DATASET_ID, "sykmeldinger_utenlandsk")

class SykmeldingBqMappersService(
    private val hashService: HashService
) {
    /**
     * All common values between utenlandsk and nasjonal.
     */
    private fun SykmeldingRecord.toCommonBqPayload(meta: RecordMeta): BqPayload {
        val payload = BqPayload(
            "id" to BqTypes.String(sykmelding.id),
            "fom" to BqTypes.Date(sykmelding.aktivitet.minOf { aktivitet -> aktivitet.fom }),
            "tom" to BqTypes.Date(sykmelding.aktivitet.maxOf { aktivitet -> aktivitet.tom }),
            "type" to BqTypes.String(sykmelding.type.name),
            "regelutfall" to BqTypes.String(validation.status.name),
            "perioder" to BqTypes.RecordArray(sykmelding.aktivitet.map { periodeToBqPayload(it) }),
            "kafka_partition" to BqTypes.Long(meta.partition.toLong()),
            "kafka_offset" to BqTypes.Long(meta.offset),
            "kafka_epoch" to BqTypes.Long(meta.timestamp),
            "meta_generert_dato" to BqTypes.DateTime(sykmelding.metadata.genDate),
            "meta_nav_mottatt_dato" to BqTypes.DateTime(sykmelding.metadata.mottattDato),
            "meta_avsender_system" to BqTypes.String(sykmelding.metadata.avsenderSystem.navn),
            "meta_avsender_version" to BqTypes.String(sykmelding.metadata.avsenderSystem.versjon),
            "pasient_ident_sha256" to BqTypes.String(hashService.hashIdent(sykmelding.pasient.fnr)),
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

    fun toUtenlandskBqPayload(record: SykmeldingRecord.Utenlandsk, meta: RecordMeta): BqPayload = record
        .toCommonBqPayload(meta)
        .add("meta_land" to BqTypes.String(record.sykmelding.utenlandskInfo.land))

    fun toNasjonalBqPayload(record: SykmeldingRecord, meta: RecordMeta): BqPayload {
        val sykmelding = record.sykmelding
        if (sykmelding !is Sykmelding.Nasjonal) {
            throw IllegalArgumentException("Cannot convert Utenlandsk sykmelding to Nasjonal row, use toUtenlandskBqPayload() instead")
        }

        val payload = record.toCommonBqPayload(meta).add(
            "behandler_hpr_nr" to BqTypes.String.Nullable(sykmelding.behandler.ids.find { it.type == PersonIdType.HPR }?.id?.trim()),
            "behandler_her_id" to BqTypes.String.Nullable(sykmelding.behandler.ids.find { it.type == PersonIdType.HER }?.id?.trim()),
            "behandler_ident_sha256" to BqTypes.String.Nullable(
                sykmelding.behandler.ids.find { it.type == PersonIdType.FNR || it.type == PersonIdType.DNR }?.id?.let {
                    hashService.hashIdent(it)
                }
            ),
            "behandler_hpr_kategori" to BqTypes.Enum(sykmelding.sykmelder.helsepersonellKategori),
        )

        return payload
    }

    private fun periodeToBqPayload(aktivitet: Aktivitet): BqPayload {
        val payload = BqPayload(
            "type" to BqTypes.Enum(aktivitet.type),
            "fom" to BqTypes.Date(aktivitet.fom),
            "tom" to BqTypes.Date(aktivitet.tom),
        )

        when (aktivitet) {
            is Aktivitet.IkkeMulig -> {
                payload.add(
                    "medisinsk_arsak_arsak" to BqTypes.Enum.Nullable(aktivitet.medisinskArsak?.arsak?.firstOrNull()),
                    "medisinsk_arsak_beskrivelse" to BqTypes.String.Nullable(aktivitet.medisinskArsak?.beskrivelse),
                    "arbeidsrelatert_arsak" to BqTypes.Enum.Nullable(aktivitet.arbeidsrelatertArsak?.arsak?.firstOrNull()),
                    "arbeidsrelatert_arsak_beskrivelse" to BqTypes.String.Nullable(aktivitet.arbeidsrelatertArsak?.beskrivelse)
                )
            }

            is Aktivitet.Avventende -> {
                payload.add(
                    "avventene_innspill_til_arbeidsgiver" to BqTypes.String.Nullable(aktivitet.innspillTilArbeidsgiver)
                )
            }

            is Aktivitet.Behandlingsdager -> {
                payload.add(
                    "enkeltstaende_behandlingsdager" to BqTypes.Long.Nullable(aktivitet.antallBehandlingsdager.toLong())
                )
            }

            is Aktivitet.Gradert -> {
                payload.add(
                    "grad" to BqTypes.Long(aktivitet.grad.toLong()),
                    "gradert_reisetilskudd" to BqTypes.Boolean(aktivitet.reisetilskudd)
                )
            }

            is Aktivitet.Reisetilskudd -> {}
        }

        return payload
    }
}
