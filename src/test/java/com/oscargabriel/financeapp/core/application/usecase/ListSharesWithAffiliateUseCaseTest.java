package com.oscargabriel.financeapp.core.application.usecase;

import static com.oscargabriel.financeapp.core.application.usecase.SendAffiliationRequestUseCaseTest.esError;
import static com.oscargabriel.financeapp.core.support.AffiliateBalanceMother.unaParte;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.AFILIACION_ID;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.AJENO_ID;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.DESTINATARIO_ID;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.REMITENTE_ID;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.unaSolicitud;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.core.domain.model.AffiliationStatus;
import com.oscargabriel.financeapp.core.domain.model.ShareWithAffiliate;
import com.oscargabriel.financeapp.core.domain.port.out.AffiliateBalanceQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.AffiliationRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ListSharesWithAffiliateUseCaseTest {

    @Mock
    private AffiliationRepositoryPort afiliaciones;

    @Mock
    private AffiliateBalanceQueryPort saldos;

    private ListSharesWithAffiliateUseCase casoDeUso;

    @BeforeEach
    void setUp() {
        casoDeUso = new ListSharesWithAffiliateUseCase(afiliaciones, saldos);
    }

    @Test
    void elRemitenteVeLasPartesConElDestinatario() {
        ShareWithAffiliate parte = unaParte().build();
        when(afiliaciones.findForParticipant(AFILIACION_ID, REMITENTE_ID))
                .thenReturn(Mono.just(unaSolicitud().conEstado(AffiliationStatus.ACCEPTED).build()));
        when(saldos.findSharesBetween(REMITENTE_ID, DESTINATARIO_ID)).thenReturn(Flux.just(parte));

        StepVerifier.create(casoDeUso.list(REMITENTE_ID, AFILIACION_ID))
                .expectNext(parte)
                .verifyComplete();
    }

    @Test
    void elDestinatarioVeLasPartesConElRemitenteAunqueEsteDesvinculada() {
        ShareWithAffiliate parte = unaParte().queDebo().build();
        when(afiliaciones.findForParticipant(AFILIACION_ID, DESTINATARIO_ID))
                .thenReturn(Mono.just(unaSolicitud().conEstado(AffiliationStatus.UNLINKED).build()));
        when(saldos.findSharesBetween(DESTINATARIO_ID, REMITENTE_ID)).thenReturn(Flux.just(parte));

        StepVerifier.create(casoDeUso.list(DESTINATARIO_ID, AFILIACION_ID))
                .expectNext(parte)
                .verifyComplete();
    }

    @Test
    void unaAjenaOInexistenteEs404SobreId() {
        when(afiliaciones.findForParticipant(AFILIACION_ID, AJENO_ID)).thenReturn(Mono.empty());

        StepVerifier.create(casoDeUso.list(AJENO_ID, AFILIACION_ID))
                .verifyErrorSatisfies(e -> esError(e, HttpStatus.NOT_FOUND, "NOT_FOUND", "id"));

        verify(saldos, never()).findSharesBetween(any(), any());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = AffiliationStatus.class, names = {"REQUESTED", "REJECTED", "CANCELLED"})
    void unaQueNuncaQuedoAceptadaEs409SobreStatus(AffiliationStatus estado) {
        when(afiliaciones.findForParticipant(AFILIACION_ID, REMITENTE_ID))
                .thenReturn(Mono.just(unaSolicitud().conEstado(estado).build()));

        StepVerifier.create(casoDeUso.list(REMITENTE_ID, AFILIACION_ID))
                .verifyErrorSatisfies(e -> esError(e, HttpStatus.CONFLICT, "INVALID_STATE", "status"));

        verify(saldos, never()).findSharesBetween(any(), any());
    }
}
