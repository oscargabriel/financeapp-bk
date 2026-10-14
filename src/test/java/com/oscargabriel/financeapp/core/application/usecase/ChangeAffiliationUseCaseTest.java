package com.oscargabriel.financeapp.core.application.usecase;

import static com.oscargabriel.financeapp.core.application.usecase.SendAffiliationRequestUseCaseTest.esError;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.AFILIACION_ID;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.AJENO_ID;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.DESTINATARIO_ID;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.REMITENTE_ID;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.unaSolicitud;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.unaVista;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.core.domain.model.AffiliationAction;
import com.oscargabriel.financeapp.core.domain.model.AffiliationStatus;
import com.oscargabriel.financeapp.core.domain.model.AffiliationView;
import com.oscargabriel.financeapp.core.domain.port.out.AffiliationRepositoryPort;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ChangeAffiliationUseCaseTest {

    @Mock
    private AffiliationRepositoryPort afiliaciones;

    private ChangeAffiliationUseCase casoDeUso;

    @BeforeEach
    void setUp() {
        casoDeUso = new ChangeAffiliationUseCase(afiliaciones);
    }

    @Test
    void aceptaYDevuelveLaRelacionComoQueda() {
        AffiliationView aceptada = unaVista().conEstado(AffiliationStatus.ACCEPTED).build();
        when(afiliaciones.findForParticipant(AFILIACION_ID, DESTINATARIO_ID)).thenReturn(Mono.just(unaSolicitud().build()));
        when(afiliaciones.changeStatus(AFILIACION_ID, AffiliationStatus.REQUESTED, AffiliationStatus.ACCEPTED))
                .thenReturn(Mono.just(true));
        when(afiliaciones.findViewForParticipant(AFILIACION_ID, DESTINATARIO_ID)).thenReturn(Mono.just(aceptada));

        StepVerifier.create(casoDeUso.change(DESTINATARIO_ID, AFILIACION_ID, AffiliationAction.ACCEPT))
                .expectNext(aceptada)
                .verifyComplete();
    }

    @Test
    void elDestinatarioDesvinculaUnaAceptada() {
        AffiliationView desvinculada = unaVista().conEstado(AffiliationStatus.UNLINKED).build();
        when(afiliaciones.findForParticipant(AFILIACION_ID, DESTINATARIO_ID))
                .thenReturn(Mono.just(unaSolicitud().conEstado(AffiliationStatus.ACCEPTED).build()));
        when(afiliaciones.changeStatus(AFILIACION_ID, AffiliationStatus.ACCEPTED, AffiliationStatus.UNLINKED))
                .thenReturn(Mono.just(true));
        when(afiliaciones.findViewForParticipant(AFILIACION_ID, DESTINATARIO_ID)).thenReturn(Mono.just(desvinculada));

        StepVerifier.create(casoDeUso.change(DESTINATARIO_ID, AFILIACION_ID, AffiliationAction.UNLINK))
                .expectNext(desvinculada)
                .verifyComplete();
    }

    @Test
    void unaAjenaOInexistenteEs404SobreIdSinCambiarNada() {
        when(afiliaciones.findForParticipant(AFILIACION_ID, AJENO_ID)).thenReturn(Mono.empty());

        StepVerifier.create(casoDeUso.change(AJENO_ID, AFILIACION_ID, AffiliationAction.ACCEPT))
                .verifyErrorSatisfies(e -> esError(e, HttpStatus.NOT_FOUND, "NOT_FOUND", "id"));

        verify(afiliaciones, never()).changeStatus(any(), any(), any());
    }

    @Test
    void unaAccionQueNoCorrespondeAlPapelEs409SobreStatusSinCambiarNada() {
        when(afiliaciones.findForParticipant(AFILIACION_ID, REMITENTE_ID)).thenReturn(Mono.just(unaSolicitud().build()));

        StepVerifier.create(casoDeUso.change(REMITENTE_ID, AFILIACION_ID, AffiliationAction.ACCEPT))
                .verifyErrorSatisfies(e -> esError(e, HttpStatus.CONFLICT, "INVALID_STATE", "status"));

        verify(afiliaciones, never()).changeStatus(any(), any(), any());
    }

    @Test
    void siOtraPeticionLaCambioAntesEs409SobreStatus() {
        when(afiliaciones.findForParticipant(AFILIACION_ID, REMITENTE_ID)).thenReturn(Mono.just(unaSolicitud().build()));
        when(afiliaciones.changeStatus(AFILIACION_ID, AffiliationStatus.REQUESTED, AffiliationStatus.CANCELLED))
                .thenReturn(Mono.just(false));

        StepVerifier.create(casoDeUso.change(REMITENTE_ID, AFILIACION_ID, AffiliationAction.CANCEL))
                .verifyErrorSatisfies(e -> esError(e, HttpStatus.CONFLICT, "INVALID_STATE", "status"));
    }
}
