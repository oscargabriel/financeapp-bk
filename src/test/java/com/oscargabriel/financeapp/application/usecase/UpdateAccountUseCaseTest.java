package com.oscargabriel.financeapp.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.Account;
import com.oscargabriel.financeapp.domain.model.AccountType;
import com.oscargabriel.financeapp.domain.model.UpdateAccountCommand;
import com.oscargabriel.financeapp.domain.port.out.AccountRepositoryPort;
import com.oscargabriel.financeapp.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.support.AccountMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class UpdateAccountUseCaseTest {

    @Mock
    private AccountRepositoryPort cuentas;

    @Mock
    private CurrencyQueryPort monedas;

    @Captor
    private ArgumentCaptor<Account> cuentaGuardada;

    @Test
    void aplicaNombreYSaldoInicialYConservaElResto() {
        guardada(AccountMother.efectivo());
        actualizacionPosible();

        StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                        new UpdateAccountCommand(" Bolsillo ", null, new BigDecimal("200000"), null, null, null)))
                .expectNextCount(1)
                .verifyComplete();

        verify(cuentas).update(eq(AccountMother.USER_ID), cuentaGuardada.capture());
        assertThat(cuentaGuardada.getValue()).isEqualTo(new Account(AccountMother.EFECTIVO_ID, "Bolsillo",
                AccountType.CASH, "COP", new BigDecimal("200000"), new BigDecimal("322500.0000"),
                null, null, null, true));
    }

    @Test
    void aplicaCupoYDiasSobreUnaTarjetaYConservaLoQueVieneEnNull() {
        Account tarjeta = AccountMother.tarjetaCreada();
        guardada(tarjeta);
        actualizacionPosible();

        StepVerifier.create(useCase().update(AccountMother.USER_ID, tarjeta.id(),
                        new UpdateAccountCommand(null, null, null, new BigDecimal("4000000"), 25, null)))
                .expectNextCount(1)
                .verifyComplete();

        verify(cuentas).update(eq(AccountMother.USER_ID), cuentaGuardada.capture());
        assertThat(cuentaGuardada.getValue()).isEqualTo(new Account(tarjeta.id(), "Mastercard",
                AccountType.CREDIT, "COP", new BigDecimal("-200000.0000"), new BigDecimal("-200000.0000"),
                new BigDecimal("4000000"), 25, 5, true));
    }

    @Test
    void devuelveLaCuentaComoLaDejoLaBase() {
        Account comoQuedo = new Account(AccountMother.EFECTIVO_ID, "Efectivo", AccountType.CASH, "COP",
                new BigDecimal("600000.0000"), new BigDecimal("422500.0000"), null, null, null, true);
        guardada(AccountMother.efectivo());
        when(cuentas.update(any(), any())).thenReturn(Mono.just(comoQuedo));

        StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                        new UpdateAccountCommand(null, null, new BigDecimal("600000"), null, null, null)))
                .assertNext(cuenta -> assertThat(cuenta).isEqualTo(comoQuedo))
                .verifyComplete();
    }

    @Test
    void daNotFoundSobreElIdCuandoLaCuentaNoEstaEntreLasVivasDelUsuario() {
        when(cuentas.findActiveByIdAndUser(AccountMother.EFECTIVO_ID, AccountMother.USER_ID))
                .thenReturn(Mono.empty());

        StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                        new UpdateAccountCommand("Bolsillo", null, null, null, null, null)))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "id"))
                .verify();

        verify(cuentas, never()).update(any(), any());
    }

    @Test
    void rechazaLosCamposDeCreditoSobreUnaCuentaQueNoEsDeCreditoUnoPorCampo() {
        guardada(AccountMother.efectivo());

        StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                        new UpdateAccountCommand(null, "USD", null, new BigDecimal("1000000"), 3, 10)))
                .expectErrorSatisfies(error -> {
                    BadRequestException bre = (BadRequestException) error;
                    assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(bre.getErrorResponse().getErrors())
                            .extracting(ErrorDetail::getCode, ErrorDetail::getField)
                            .containsExactlyInAnyOrder(
                                    tuple(ErrorCodes.VALIDATION_ERROR.getCode(), "creditLimit"),
                                    tuple(ErrorCodes.VALIDATION_ERROR.getCode(), "statementDay"),
                                    tuple(ErrorCodes.VALIDATION_ERROR.getCode(), "paymentDueDay"));
                })
                .verify();

        verifyNoInteractions(monedas);
        verify(cuentas, never()).hasTransactions(any());
        verify(cuentas, never()).update(any(), any());
    }

    @Test
    void unaMonedaNuevaQueNoEstaActivaDaBadRequestSinConsultarMovimientos() {
        guardada(AccountMother.efectivo());
        when(monedas.exists("XYZ")).thenReturn(Mono.just(false));

        StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                        new UpdateAccountCommand(null, " xyz ", null, null, null, null)))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                        "currencyCode"))
                .verify();

        verify(cuentas, never()).hasTransactions(any());
        verify(cuentas, never()).update(any(), any());
    }

    @Test
    void rechazaCambiarLaMonedaDeUnaCuentaConMovimientosSinGuardarNada() {
        guardada(AccountMother.efectivo());
        when(monedas.exists("USD")).thenReturn(Mono.just(true));
        when(cuentas.hasTransactions(AccountMother.EFECTIVO_ID)).thenReturn(Mono.just(true));

        StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                        new UpdateAccountCommand("Otro", "USD", null, null, null, null)))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, ErrorCodes.RESOURCE_IN_USE,
                        "currencyCode"))
                .verify();

        verify(cuentas, never()).update(any(), any());
    }

    @Test
    void sinMovimientosCambiaLaMonedaEnMayusculas() {
        guardada(AccountMother.efectivo());
        when(monedas.exists("USD")).thenReturn(Mono.just(true));
        when(cuentas.hasTransactions(AccountMother.EFECTIVO_ID)).thenReturn(Mono.just(false));
        actualizacionPosible();

        StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                        new UpdateAccountCommand(null, " usd ", null, null, null, null)))
                .expectNextCount(1)
                .verifyComplete();

        verify(cuentas).update(eq(AccountMother.USER_ID), cuentaGuardada.capture());
        assertThat(cuentaGuardada.getValue().currencyCode()).isEqualTo("USD");
    }

    /** Sin stubs de monedas ni de hasTransactions: el modo estricto de Mockito falla si se llaman. */
    @Test
    void laMismaMonedaEnOtraCajaNoConsultaElCatalogoNiLosMovimientos() {
        guardada(AccountMother.efectivo());
        actualizacionPosible();

        StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                        new UpdateAccountCommand(null, "cop", null, null, null, null)))
                .expectNextCount(1)
                .verifyComplete();

        verifyNoInteractions(monedas);
        verify(cuentas, never()).hasTransactions(any());
    }

    @Test
    void propagaElConflictoDeNombreQueReportaElRepositorio() {
        guardada(AccountMother.efectivo());
        when(cuentas.update(any(), any())).thenReturn(Mono.error(new BadRequestException(HttpStatus.CONFLICT,
                ErrorCodes.DUPLICATE_RESOURCE, "Ya hay una cuenta con ese nombre", "name")));

        StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                        new UpdateAccountCommand("Visa", null, null, null, null, null)))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_RESOURCE,
                        "name"))
                .verify();
    }

    /** Borrada entre la lectura y la escritura. */
    @Test
    void daNotFoundCuandoElUpdateNoEncuentraLaFila() {
        guardada(AccountMother.efectivo());
        when(cuentas.update(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                        new UpdateAccountCommand("Bolsillo", null, null, null, null, null)))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "id"))
                .verify();
    }

    private void guardada(Account cuenta) {
        when(cuentas.findActiveByIdAndUser(cuenta.id(), AccountMother.USER_ID)).thenReturn(Mono.just(cuenta));
    }

    private void actualizacionPosible() {
        when(cuentas.update(any(), any())).thenAnswer(invocacion -> Mono.just(invocacion.getArgument(1)));
    }

    private static void esError(Throwable error, HttpStatus status, ErrorCodes codigo, String campo) {
        BadRequestException bre = (BadRequestException) error;
        assertThat(bre.getHttpStatus()).isEqualTo(status);
        assertThat(bre.getErrorResponse().getErrors())
                .extracting(ErrorDetail::getCode, ErrorDetail::getField)
                .containsExactly(tuple(codigo.getCode(), campo));
    }

    private UpdateAccountUseCase useCase() {
        return new UpdateAccountUseCase(cuentas, monedas);
    }
}
