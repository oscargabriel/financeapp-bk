package com.oscargabriel.financeapp.core.domain.model;

import static com.oscargabriel.financeapp.core.support.AffiliationMother.AJENO_ID;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.DESTINATARIO_ID;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.REMITENTE_ID;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.unaSolicitud;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AffiliationActionTest {

    @ParameterizedTest(name = "{0} sobre {1} por el {2}: {3}")
    @CsvSource({
            "ACCEPT, REQUESTED, destinatario, true",
            "ACCEPT, REQUESTED, remitente,    false",
            "ACCEPT, ACCEPTED,  destinatario, false",
            "ACCEPT, REJECTED,  destinatario, false",
            "ACCEPT, CANCELLED, destinatario, false",
            "REJECT, REQUESTED, destinatario, true",
            "REJECT, REQUESTED, remitente,    false",
            "REJECT, ACCEPTED,  destinatario, false",
            "CANCEL, REQUESTED, remitente,    true",
            "CANCEL, REQUESTED, destinatario, false",
            "CANCEL, ACCEPTED,  remitente,    false",
            "UNLINK, ACCEPTED,  remitente,    true",
            "UNLINK, ACCEPTED,  destinatario, true",
            "UNLINK, REQUESTED, remitente,    false",
            "UNLINK, UNLINKED,  destinatario, false",
            "ACCEPT, REQUESTED, ajeno,        false",
            "CANCEL, REQUESTED, ajeno,        false",
            "UNLINK, ACCEPTED,  ajeno,        false"
    })
    void aplicaSegunElEstadoYElPapel(AffiliationAction accion, AffiliationStatus estado, String papel, boolean aplica) {
        Affiliation solicitud = unaSolicitud().conEstado(estado).build();

        assertThat(accion.appliesTo(solicitud, usuario(papel))).isEqualTo(aplica);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"CANCEL, true", "ACCEPT, false", "REJECT, false"})
    void sinDestinatarioSoloSuRemitenteLaCancela(AffiliationAction accion, boolean aplica) {
        Affiliation solicitud = unaSolicitud().sinDestinatario().build();

        assertThat(accion.appliesTo(solicitud, REMITENTE_ID)).isEqualTo(aplica);
    }

    @ParameterizedTest(name = "{0} lleva de {1} a {2}")
    @CsvSource({
            "ACCEPT, REQUESTED, ACCEPTED",
            "REJECT, REQUESTED, REJECTED",
            "CANCEL, REQUESTED, CANCELLED",
            "UNLINK, ACCEPTED,  UNLINKED"
    })
    void cadaAccionLlevaDeUnEstadoAOtro(AffiliationAction accion, AffiliationStatus desde, AffiliationStatus hacia) {
        assertThat(accion.from()).isEqualTo(desde);
        assertThat(accion.to()).isEqualTo(hacia);
    }

    private static UUID usuario(String papel) {
        return switch (papel) {
            case "remitente" -> REMITENTE_ID;
            case "destinatario" -> DESTINATARIO_ID;
            default -> AJENO_ID;
        };
    }
}
