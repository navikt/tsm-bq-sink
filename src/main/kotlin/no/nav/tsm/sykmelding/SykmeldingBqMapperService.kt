package no.nav.tsm.sykmelding

import com.google.cloud.bigquery.TableId
import io.opentelemetry.instrumentation.annotations.WithSpan
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
            "regelutfall" to BqTypes.Enum(validation.status),
            "regelutfall_timestamp" to BqTypes.DateTime(validation.timestamp),
            "regelutfall_historikk" to BqTypes.RecordArray(validation.rules.map {
                val historikk = BqPayload(
                    "type" to BqTypes.Enum(it.type),
                    "timestamp" to BqTypes.DateTime(it.timestamp),
                    "status" to BqTypes.Enum(it.validationType),
                )

                val reason = when (it) {
                    is Rule.Invalid -> it.reason
                    is Rule.Pending -> it.reason
                    else -> null
                }

                if (reason != null) {
                    historikk.add(
                        "reason_sykmelder" to BqTypes.String.Nullable(reason.sykmelder),
                        "reason_sykmeldt" to BqTypes.String.Nullable(reason.sykmeldt),
                    )
                }

                historikk
            }),
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
                "annen_fravers_arsak" to BqTypes.Enum.Nullable(mv.annenFravarsgrunn),
            )

            is MedisinskVurdering.Legacy -> payload.add(
                "syketilfelle_start_dato" to BqTypes.Date.Nullable(mv.syketilfelletStartDato),
                "annen_fravers_arsak" to BqTypes.Enum.Nullable(mv.annenFraversArsak?.arsak?.firstOrNull()),
                "annen_fravers_arsak_beskrivelse" to BqTypes.String.Nullable(mv.annenFraversArsak?.beskrivelse),
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

    @WithSpan
    fun toUtenlandskBqPayload(record: SykmeldingRecord.Utenlandsk, meta: RecordMeta): BqPayload = record
        .toCommonBqPayload(meta)
        .add(
            "meta_land" to BqTypes.String(record.sykmelding.utenlandskInfo.land),
            "meta_folkeregistertadresse_er_brakke_eller_tilsvarende" to BqTypes.Boolean(record.sykmelding.utenlandskInfo.folkeRegistertAdresseErBrakkeEllerTilsvarende),
            "meta_er_adresse_utland" to BqTypes.Boolean.Nullable(record.sykmelding.utenlandskInfo.erAdresseUtland)
        )

    @WithSpan
    fun toNasjonalBqPayload(record: SykmeldingRecord, meta: RecordMeta): BqPayload {
        val sykmelding = record.sykmelding
        if (sykmelding !is Sykmelding.Nasjonal) {
            throw IllegalArgumentException("Cannot convert Utenlandsk sykmelding to Nasjonal row, use toUtenlandskBqPayload() instead")
        }

        val payload = record.toCommonBqPayload(meta).add(
            // Sykmelder (signerende)
            "sykmelder_hpr_nr" to BqTypes.String.Nullable(sykmelding.sykmelder.ids.find { it.type == PersonIdType.HPR }?.id?.trim()),
            "sykmelder_her_id" to BqTypes.String.Nullable(sykmelding.sykmelder.ids.find { it.type == PersonIdType.HER }?.id?.trim()),
            "sykmelder_ident_sha256" to BqTypes.String.Nullable(
                sykmelding.sykmelder.ids.find { it.type == PersonIdType.FNR || it.type == PersonIdType.DNR }?.id?.let {
                    hashService.hashIdent(it.trim())
                }
            ),
            "sykmelder_hpr_kategori" to BqTypes.Enum(sykmelding.sykmelder.helsepersonellKategori),
            // Behandler
            "behandler_hpr_nr" to BqTypes.String.Nullable(sykmelding.behandler.ids.find { it.type == PersonIdType.HPR }?.id?.trim()),
            "behandler_her_id" to BqTypes.String.Nullable(sykmelding.behandler.ids.find { it.type == PersonIdType.HER }?.id?.trim()),
            "behandler_ident_sha256" to BqTypes.String.Nullable(
                sykmelding.behandler.ids.find { it.type == PersonIdType.FNR || it.type == PersonIdType.DNR }?.id?.let {
                    hashService.hashIdent(it.trim())
                }
            ),
            // Bistand nav
            "bistand_nav_umiddelbart" to BqTypes.Boolean.Nullable(sykmelding.bistandNav?.bistandUmiddelbart),
            "bistand_nav_beskrivelse" to BqTypes.String.Nullable(sykmelding.bistandNav?.beskrivBistand),
            // Tilbakedatering
            "tilbakedatering_kontaktdato" to BqTypes.Date.Nullable(sykmelding.tilbakedatering?.kontaktDato),
            "tilbakedatering_begrunnelse" to BqTypes.String.Nullable(sykmelding.tilbakedatering?.begrunnelse),
            // Arbeidsgiver
            "arbeidsgiver_antall_type" to BqTypes.Enum(sykmelding.arbeidsgiver.type),
        )

        when (val ag = sykmelding.arbeidsgiver) {
            is ArbeidsgiverInfo.En -> {
                payload.add(
                    "arbeidsgiver_navn" to BqTypes.String.Nullable(ag.navn),
                    "arbeidsgiver_melding_til_arbeidsgiver" to BqTypes.String.Nullable(ag.meldingTilArbeidsgiver),
                    "arbeidsgiver_tiltak_arbeidsplassen" to BqTypes.String.Nullable(ag.tiltakArbeidsplassen),
                )
            }

            is ArbeidsgiverInfo.Flere -> {
                payload.add(
                    "arbeidsgiver_navn" to BqTypes.String.Nullable(ag.navn),
                    "arbeidsgiver_melding_til_arbeidsgiver" to BqTypes.String.Nullable(ag.meldingTilArbeidsgiver),
                    "arbeidsgiver_tiltak_arbeidsplassen" to BqTypes.String.Nullable(ag.tiltakArbeidsplassen),
                )
            }

            is ArbeidsgiverInfo.Ingen -> {}
        }

        val legacyPrognose = when (sykmelding) {
            is Sykmelding.Xml -> sykmelding.prognose
            is Sykmelding.Papir -> sykmelding.prognose
            is Sykmelding.Digital -> null
        }

        if (legacyPrognose != null) {
            payload.add(
                "prognose_arbeidsfor_etter_periode" to BqTypes.Boolean.Nullable(legacyPrognose.arbeidsforEtterPeriode),
                "prognose_hensyn_arbeidsplassen" to BqTypes.String.Nullable(legacyPrognose.hensynArbeidsplassen)
            )

            when (val arbeid = legacyPrognose.arbeid) {
                is IArbeid.ErIArbeid -> {
                    payload.add(
                        "prognose_arbeid_type" to BqTypes.Enum.Nullable(arbeid.type),
                        "prognose_arbeid_vurderingsdato" to BqTypes.Date.Nullable(arbeid.vurderingsdato),
                        "prognose_arbeid_i_arbeid_eget_arbeid_pa_sikt" to BqTypes.Boolean.Nullable(arbeid.egetArbeidPaSikt),
                        "prognose_arbeid_i_arbeid_annet_arbeid_pa_sikt" to BqTypes.Boolean.Nullable(arbeid.annetArbeidPaSikt),
                        "prognose_arbeid_i_arbeid_arbeid_fom" to BqTypes.Date.Nullable(arbeid.arbeidFOM)
                    )
                }

                is IArbeid.ErIkkeIArbeid -> {
                    payload.add(
                        "prognose_arbeid_type" to BqTypes.Enum.Nullable(arbeid.type),
                        "prognose_arbeid_vurderingsdato" to BqTypes.Date.Nullable(arbeid.vurderingsdato),
                        "prognose_arbeid_ikke_i_arbeid_arbeidsfor_pa_sikt" to BqTypes.Boolean.Nullable(arbeid.arbeidsforPaSikt),
                        "prognose_arbeid_ikke_i_arbeid_arbeidsfor_fom" to BqTypes.Date.Nullable(arbeid.arbeidsforFOM)
                    )
                }

                null -> {}
            }
        }

        val digitalPrognose: DigitalPrognose? = if (sykmelding is Sykmelding.Digital && sykmelding.prognose != null) {
            sykmelding.prognose
        } else null

        if (digitalPrognose != null) {
            payload.add(
                "prognose_arbeid_i_arbeid_annet_arbeid_pa_sikt" to BqTypes.Boolean.Nullable(digitalPrognose.friskmeldingTilArbeidsformidling),
            )
        }

        sykmelding.whenDigitalUtypendeSporsmal { utdypendeSporsmal ->
            payload.add(
                "utdypende_sporsmal" to BqTypes.RecordArray(utdypendeSporsmal.map {
                    BqPayload(
                        "type" to BqTypes.Enum(it.type),
                        "svar" to BqTypes.String(it.svar),
                        "skjermetForArbeidsgiver" to BqTypes.Boolean(it.skjermetForArbeidsgiver),
                        "sporsmal" to BqTypes.String.Nullable(it.sporsmal),
                    )
                })
            )
        }

        sykmelding.whenLegacyUtdypendeSorsmal { utdypendeOpplysninger ->
            val legacyUtdypendeSporsmal = utdypendeOpplysninger.toSykInnUtdypendeSpm()
            if (legacyUtdypendeSporsmal != null) {
                payload.add(
                    "utdypende_sporsmal" to BqTypes.RecordArray(legacyUtdypendeSporsmal.map {
                        BqPayload(
                            "type" to BqTypes.Enum(it.type),
                            "svar" to BqTypes.String(it.svar),
                            "skjermetForArbeidsgiver" to BqTypes.Boolean(it.skjermetForArbeidsgiver),
                            "sporsmal" to BqTypes.String.Nullable(it.sporsmal),
                            "feltnummer" to BqTypes.String.Nullable(it.feltnummer),
                        )
                    })
                )
            }
        }

        sykmelding.whenLegacyTiltak { tiltak ->
            payload.add(
                "tiltak_nav" to BqTypes.String.Nullable(tiltak.tiltakNav),
                "tiltak_andre" to BqTypes.String.Nullable(tiltak.andreTiltak),
            )
        }

        return payload
    }
}

private fun Sykmelding.whenDigitalUtypendeSporsmal(block: (List<UtdypendeSporsmal>) -> Unit) {
    if (this is Sykmelding.Digital && this.utdypendeSporsmal != null) {
        block(this.utdypendeSporsmal as List<UtdypendeSporsmal>)
    }
}

private fun Sykmelding.whenLegacyUtdypendeSorsmal(block: (Map<String, Map<String, SporsmalSvar>>) -> Unit) {
    if (this is Sykmelding.Nasjonal.Legacy && this.utdypendeOpplysninger != null) {
        block(this.utdypendeOpplysninger as Map<String, Map<String, SporsmalSvar>>)
    }
}

private fun Sykmelding.whenLegacyTiltak(block: (Tiltak) -> Unit) {
    if (this is Sykmelding.Nasjonal.Legacy && this.tiltak != null) {
        block(this.tiltak as Tiltak)
    }
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

