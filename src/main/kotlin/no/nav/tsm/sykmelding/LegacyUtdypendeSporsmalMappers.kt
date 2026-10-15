package no.nav.tsm.sykmelding

import no.nav.tsm.ktor.logger
import no.nav.tsm.sykmelding.input.core.model.SporsmalSvar
import no.nav.tsm.sykmelding.input.core.model.SvarRestriksjon

private val logger = logger()

class BqUtdypendeSporsmal(
    val type: SupportedUtypendeSporsmalType,
    val svar: String,
    val skjermetForArbeidsgiver: Boolean,
    val sporsmal: String?,
    val feltnummer: String?,
)

fun Map<String, Map<String, SporsmalSvar>>.toSykInnUtdypendeSpm(): List<BqUtdypendeSporsmal>? {
    // Key er for eksempel: 6.3.1
    val sporsmal: List<Map.Entry<String, SporsmalSvar>> = values
        .flatMap { it.entries }
        .filter { isSupported(it.key) }
        .sortedByDescending { it.key }

    if (sporsmal.isEmpty()) return null

    return listOfNotNull(
        sporsmal.tryMapSpm(SupportedUtypendeSporsmalType.HENSYN_PA_ARBEIDSPLASSEN),
        sporsmal.tryMapSpm(SupportedUtypendeSporsmalType.MEDISINSK_OPPSUMMERING),
        sporsmal.tryMapSpm(SupportedUtypendeSporsmalType.UTFORDRINGER_MED_GRADERT_ARBEID),
        sporsmal.tryMapSpm(SupportedUtypendeSporsmalType.UTFORDRINGER_MED_ARBEID),
        sporsmal.tryMapSpm(SupportedUtypendeSporsmalType.BEHANDLING_OG_FREMTIDIG_ARBEID),
        sporsmal.tryMapSpm(SupportedUtypendeSporsmalType.UAVKLARTE_FORHOLD),
        sporsmal.tryMapSpm(SupportedUtypendeSporsmalType.FORVENTET_HELSETILSTAND_UTVIKLING),
        sporsmal.tryMapSpm(SupportedUtypendeSporsmalType.MEDISINSKE_HENSYN),
    )
}


enum class SupportedUtypendeSporsmalType(vararg val keys: String, val defaultSpm: String) {
    MEDISINSK_OPPSUMMERING(
        "6.3.1",
        "6.4.1",
        "6.5.1",
        defaultSpm = "Gi en kort medisinsk oppsummering av tilstanden (sykehistorie, hovedsymptomer, behandling)",
    ),
    UTFORDRINGER_MED_GRADERT_ARBEID(
        "6.3.2",
        defaultSpm = "Beskriv kort hvilke helsemessige begrensninger som gjør det vanskelig å jobbe gradert",
    ),
    HENSYN_PA_ARBEIDSPLASSEN(
        "6.3.3",
        defaultSpm = "Beskriv eventuelle medisinske forhold som bør ivaretas ved eventuell tilbakeføring til nåværende arbeid (ikke obligatorisk)",
    ),
    UTFORDRINGER_MED_ARBEID(
        "6.4.2",
        "6.5.2",
        defaultSpm = "Beskriv kort hvilke utfordringer helsetilstanden gir i arbeidssituasjonen nå. Oppgi også kort hva pasienten likevel kan mestre",
    ),
    BEHANDLING_OG_FREMTIDIG_ARBEID(
        "6.4.3",
        defaultSpm =
            "Beskriv pågående og planlagt utredning/behandling, og om dette forventes å påvirke muligheten for økt arbeidsdeltakelse fremover",
    ),
    UAVKLARTE_FORHOLD(
        "6.4.4",
        defaultSpm =
            "Er det forhold som fortsatt er uavklarte eller hindrer videre arbeidsdeltakelse, som Nav bør være kjent med i sin oppfølging?",
    ),
    FORVENTET_HELSETILSTAND_UTVIKLING(
        "6.5.3",
        defaultSpm =
            "Hvordan forventes helsetilstanden å utvikle seg de neste 3-6 månedene med tanke på mulighet for økt arbeidsdeltakelse?",
    ),
    MEDISINSKE_HENSYN(
        "6.5.4",
        defaultSpm =
            "Er det medisinske hensyn eller avklaringsbehov Nav bør kjenne til i videre oppfølging?",
    );

    fun isType(string: String): Boolean {
        return keys.contains(string)
    }
}

private fun List<Map.Entry<String, SporsmalSvar>>.tryMapSpm(
    supportedSpm: SupportedUtypendeSporsmalType
): BqUtdypendeSporsmal? {
    val sporsmal: Map.Entry<String, SporsmalSvar>? = firstOrNull { supportedSpm.isType(it.key) }
    if (sporsmal == null) return null

    return BqUtdypendeSporsmal(
        type = supportedSpm,
        svar = sporsmal.value.svar,
        sporsmal = sporsmal.value.sporsmal ?: supportedSpm.defaultSpm,
        skjermetForArbeidsgiver = sporsmal.value.restriksjoner.contains(SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER),
        feltnummer = sporsmal.key,
    )

}

private fun isSupported(spmKey: String): Boolean {
    return when (spmKey) {
        "6.3.1",
        "6.3.2",
        "6.3.3" -> true

        "6.4.1",
        "6.4.2",
        "6.4.3",
        "6.4.4" -> true

        "6.5.1",
        "6.5.2",
        "6.5.3",
        "6.5.4" -> true

        else -> {
            logger.warn("$spmKey is not supported")
            false
        }
    }
}
