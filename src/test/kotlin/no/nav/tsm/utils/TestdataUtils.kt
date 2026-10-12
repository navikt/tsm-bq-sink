package no.nav.tsm.utils

import no.nav.tsm.sykmelding.input.core.model.Aktivitet
import no.nav.tsm.sykmelding.input.core.model.ArbeidsgiverInfo
import no.nav.tsm.sykmelding.input.core.model.AvsenderSystem
import no.nav.tsm.sykmelding.input.core.model.Behandler
import no.nav.tsm.sykmelding.input.core.model.DiagnoseInfo
import no.nav.tsm.sykmelding.input.core.model.DiagnoseSystem
import no.nav.tsm.sykmelding.input.core.model.MedisinskVurdering
import no.nav.tsm.sykmelding.input.core.model.Pasient
import no.nav.tsm.sykmelding.input.core.model.RuleType
import no.nav.tsm.sykmelding.input.core.model.Sykmelder
import no.nav.tsm.sykmelding.input.core.model.Sykmelding
import no.nav.tsm.sykmelding.input.core.model.SykmeldingMeta
import no.nav.tsm.sykmelding.input.core.model.SykmeldingRecord
import no.nav.tsm.sykmelding.input.core.model.UtenlandskInfo
import no.nav.tsm.sykmelding.input.core.model.ValidationResult
import no.nav.tsm.sykmelding.input.core.model.metadata.HelsepersonellKategori
import no.nav.tsm.sykmelding.input.core.model.metadata.MessageMetadata
import no.nav.tsm.sykmelding.input.core.model.metadata.Navn
import no.nav.tsm.sykmelding.input.core.model.metadata.PersonId
import no.nav.tsm.sykmelding.input.core.model.metadata.PersonIdType
import java.time.OffsetDateTime

fun testSykmelding(id: String): SykmeldingRecord = SykmeldingRecord.Digital(
    metadata = MessageMetadata.Digital(orgnummer = "123456789"),
    sykmelding = Sykmelding.Digital(
        id = id,
        metadata = SykmeldingMeta.Digital(
            genDate = OffsetDateTime.now(),
            mottattDato = OffsetDateTime.now(),
            avsenderSystem = AvsenderSystem(navn = "Test System", versjon = "1.0.0")
        ),
        pasient = Pasient(
            fnr = "12345678901",
            kontaktinfo = emptyList(),
            navn = null,
            navKontor = null,
            navnFastlege = null,
        ),
        medisinskVurdering = MedisinskVurdering.Digital(
            hovedDiagnose = DiagnoseInfo(
                kode = "A01",
                system = DiagnoseSystem.ICPC2,
                tekst = "Test diagnose"
            ),
            biDiagnoser = listOf(
                DiagnoseInfo(
                    kode = "B02",
                    system = DiagnoseSystem.ICD10,
                    tekst = "Bi-diagnose test"
                )
            ),
            svangerskap = false,
            skjermetForPasient = false,
            yrkesskade = null,
            annenFravarsgrunn = null,
        ),
        aktivitet = listOf(
            Aktivitet.IkkeMulig(
                fom = OffsetDateTime.now().minusDays(15).toLocalDate(),
                tom = OffsetDateTime.now().minusDays(10).toLocalDate(),
                medisinskArsak = null,
                arbeidsrelatertArsak = null
            ),
            Aktivitet.Gradert(
                fom = OffsetDateTime.now().minusDays(11).toLocalDate(),
                tom = OffsetDateTime.now().minusDays(5).toLocalDate(),
                grad = 69,
                reisetilskudd = false
            ),
        ),
        behandler = Behandler(
            navn = Navn(fornavn = "Ola", mellomnavn = null, etternavn = "Nordmann"),
            adresse = null,
            ids = listOf(
                PersonId(type = PersonIdType.HPR, id = "123456"),
                PersonId(type = PersonIdType.FNR, id = "23123213131")
            ),
            kontaktinfo = emptyList(),
        ),
        sykmelder = Sykmelder(
            ids = listOf(PersonId(type = PersonIdType.HPR, id = "123456")),
            helsepersonellKategori = HelsepersonellKategori.LEGE,
        ),
        arbeidsgiver = ArbeidsgiverInfo.Ingen(),
        tilbakedatering = null,
        bistandNav = null,
        utdypendeSporsmal = null,
        prognose = null,
    ),
    validation = ValidationResult(
        status = RuleType.OK,
        rules = emptyList(),
        timestamp = OffsetDateTime.now()
    )
)

fun testUtenlandsk(id: String): SykmeldingRecord.Utenlandsk = SykmeldingRecord.Utenlandsk(
    metadata = MessageMetadata.Utenlandsk(
        land = "SE",
        journalPostId = "123122131",
    ),
    sykmelding = Sykmelding.Utenlandsk(
        id = id,
        metadata = SykmeldingMeta.Legacy(
            genDate = OffsetDateTime.now(),
            mottattDato = OffsetDateTime.now(),
            avsenderSystem = AvsenderSystem(navn = "Test System", versjon = "1.0.0"),
            behandletTidspunkt = OffsetDateTime.now(),
            regelsettVersjon = null,
            strekkode = null,
        ),
        utenlandskInfo = UtenlandskInfo(
            land = "SE",
            folkeRegistertAdresseErBrakkeEllerTilsvarende = false,
            erAdresseUtland = null
        ),
        pasient = Pasient(
            fnr = "12345678901",
            kontaktinfo = emptyList(),
            navn = null,
            navKontor = null,
            navnFastlege = null,
        ),
        medisinskVurdering = MedisinskVurdering.Legacy(
            hovedDiagnose = null,
            biDiagnoser = null,
            svangerskap = false,
            skjermetForPasient = false,
            yrkesskade = null,
            annenFraversArsak = null,
            syketilfelletStartDato = null,
        ),
        aktivitet = listOf(
            Aktivitet.Gradert(
                fom = OffsetDateTime.now().minusDays(10).toLocalDate(),
                tom = OffsetDateTime.now().minusDays(5).toLocalDate(),
                grad = 69,
                reisetilskudd = false
            )
        ),
    ),
    validation = ValidationResult(
        status = RuleType.OK,
        rules = emptyList(),
        timestamp = OffsetDateTime.now()
    )


)
