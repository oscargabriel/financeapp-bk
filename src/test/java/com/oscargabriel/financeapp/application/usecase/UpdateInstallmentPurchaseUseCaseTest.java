package com.oscargabriel.financeapp.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.model.GroupScope;
import com.oscargabriel.financeapp.domain.model.UpdateInstallmentPurchaseCommand;
import com.oscargabriel.financeapp.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.domain.port.out.InstallmentPurchaseRepositoryPort;
import com.oscargabriel.financeapp.support.InstallmentMother;
import com.oscargabriel.financeapp.support.TransactionMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UpdateInstallmentPurchaseUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-10-09T15:00:00Z");

    private static final UUID USER_ID = TransactionMother.USER_ID;

    private static final UUID COMPRA_ID = InstallmentMother.COMPRA_ID;

    @Mock
    private AccountQueryPort cuentas;

    @Mock
    private CategoryQueryPort categorias;

    @Mock
    private InstallmentPurchaseRepositoryPort compras;

    private UpdateInstallmentPurchaseUseCase casoDeUso;

    @BeforeEach
    void escenario() {
        when(cuentas.findByUser(USER_ID, true)).thenReturn(Flux.fromIterable(InstallmentMother.cuentasConTarjetas()));
        when(categorias.findActiveByUser(eq(USER_ID), any()))
                .thenReturn(Flux.fromIterable(TransactionMother.categoriasDelUsuario()));
        when(compras.update(any(), any(), any(), any(), any(), any())).thenReturn(Mono.just(true));
        when(compras.findByIdAndUser(COMPRA_ID, USER_ID)).thenReturn(Mono.just(InstallmentMother.televisorSinPagar()));
        casoDeUso = new UpdateInstallmentPurchaseUseCase(cuentas, categorias, compras,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void aplicaElAlcanceConLaDescripcionRecortadaYDevuelveLaCompraLeida() {
        StepVerifier.create(casoDeUso.update(USER_ID, COMPRA_ID,
                        new UpdateInstallmentPurchaseCommand(GroupScope.FUTURE, "  TV sala ", null)))
                .expectNext(InstallmentMother.televisorSinPagar())
                .verifyComplete();

        verify(compras).update(eq(COMPRA_ID), eq(USER_ID), eq(GroupScope.FUTURE), eq(AHORA), eq("TV sala"), isNull());
    }

    @Test
    void cambiaLaCategoriaEnTodas() {
        StepVerifier.create(casoDeUso.update(USER_ID, COMPRA_ID,
                        new UpdateInstallmentPurchaseCommand(GroupScope.ALL, null,
                                TransactionMother.AMBAS_ID.toString())))
                .expectNextCount(1)
                .verifyComplete();

        verify(compras).update(eq(COMPRA_ID), eq(USER_ID), eq(GroupScope.ALL), eq(AHORA), isNull(),
                eq(TransactionMother.AMBAS_ID));
    }

    @Test
    void unaCategoriaDeIngresosEsErrorSobreCategoryIdYNoEscribe() {
        StepVerifier.create(casoDeUso.update(USER_ID, COMPRA_ID,
                        new UpdateInstallmentPurchaseCommand(GroupScope.ALL, null,
                                TransactionMother.SALARIO_ID.toString())))
                .expectErrorSatisfies(e -> {
                    BadRequestException rechazo = (BadRequestException) e;
                    assertThat(rechazo.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rechazo.getErrorResponse().getErrors().getFirst().getField()).isEqualTo("categoryId");
                })
                .verify();

        verify(compras, never()).update(any(), any(), any(), any(), any(), any());
    }

    @Test
    void sinCompraQueEditarEs404SobreId() {
        when(compras.update(any(), any(), any(), any(), any(), any())).thenReturn(Mono.just(false));

        StepVerifier.create(casoDeUso.update(USER_ID, COMPRA_ID,
                        new UpdateInstallmentPurchaseCommand(GroupScope.ALL, "TV", null)))
                .expectErrorSatisfies(e -> {
                    BadRequestException rechazo = (BadRequestException) e;
                    assertThat(rechazo.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(rechazo.getErrorResponse().getErrors().getFirst().getCode()).isEqualTo("NOT_FOUND");
                    assertThat(rechazo.getErrorResponse().getErrors().getFirst().getField()).isEqualTo("id");
                })
                .verify();
    }

    @Test
    void siLaCancelaronEnMedioTambienEs404() {
        when(compras.findByIdAndUser(COMPRA_ID, USER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(casoDeUso.update(USER_ID, COMPRA_ID,
                        new UpdateInstallmentPurchaseCommand(GroupScope.ALL, "TV", null)))
                .expectErrorSatisfies(e -> assertThat(((BadRequestException) e).getHttpStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND))
                .verify();
    }

    @Test
    void soloDescripcionNoNecesitaLasCategorias() {
        StepVerifier.create(casoDeUso.update(USER_ID, COMPRA_ID,
                        new UpdateInstallmentPurchaseCommand(GroupScope.ALL, "TV", null)))
                .expectNextCount(1)
                .verifyComplete();

        verifyNoInteractions(cuentas, categorias);
    }
}