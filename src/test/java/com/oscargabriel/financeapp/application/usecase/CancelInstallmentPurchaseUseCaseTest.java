package com.oscargabriel.financeapp.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.port.out.InstallmentPurchaseRepositoryPort;
import com.oscargabriel.financeapp.support.InstallmentMother;
import com.oscargabriel.financeapp.support.TransactionMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class CancelInstallmentPurchaseUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-10-09T15:00:00Z");

    @Mock
    private InstallmentPurchaseRepositoryPort compras;

    private CancelInstallmentPurchaseUseCase casoDeUso;

    @BeforeEach
    void escenario() {
        casoDeUso = new CancelInstallmentPurchaseUseCase(compras, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void cancelaConElInstanteDelReloj() {
        when(compras.cancel(InstallmentMother.COMPRA_ID, TransactionMother.USER_ID, AHORA)).thenReturn(Mono.just(true));

        StepVerifier.create(casoDeUso.cancel(TransactionMother.USER_ID, InstallmentMother.COMPRA_ID)).verifyComplete();
    }

    @Test
    void sinCompraQueCancelarEs404() {
        when(compras.cancel(InstallmentMother.COMPRA_ID, TransactionMother.USER_ID, AHORA)).thenReturn(Mono.just(false));

        StepVerifier.create(casoDeUso.cancel(TransactionMother.USER_ID, InstallmentMother.COMPRA_ID))
                .expectErrorSatisfies(e -> {
                    BadRequestException rechazo = (BadRequestException) e;
                    assertThat(rechazo.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(rechazo.getErrorResponse().getErrors().getFirst().getDescription())
                            .isEqualTo("La compra no existe");
                })
                .verify();
    }
}