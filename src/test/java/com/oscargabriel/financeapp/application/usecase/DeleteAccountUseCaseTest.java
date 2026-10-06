package com.oscargabriel.financeapp.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.Account;
import com.oscargabriel.financeapp.domain.port.out.AccountRepositoryPort;
import com.oscargabriel.financeapp.support.AccountMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DeleteAccountUseCaseTest {

    @Mock
    private AccountRepositoryPort cuentas;

    @Test
    void borraUnaCuentaEnCeroAunqueTengaHistoria() {
        guardada(AccountMother.cajaChicaEnCero());
        when(cuentas.softDelete(AccountMother.CAJA_CHICA_ID, AccountMother.USER_ID)).thenReturn(Mono.just(true));

        StepVerifier.create(useCase().delete(AccountMother.USER_ID, AccountMother.CAJA_CHICA_ID))
                .verifyComplete();

        verify(cuentas).softDelete(AccountMother.CAJA_CHICA_ID, AccountMother.USER_ID);
    }

    @Test
    void unaCuentaDesactivadaEnCeroTambienSeBorra() {
        Account inactiva = AccountMother.inactivaEnCero();
        guardada(inactiva);
        when(cuentas.softDelete(inactiva.id(), AccountMother.USER_ID)).thenReturn(Mono.just(true));

        StepVerifier.create(useCase().delete(AccountMother.USER_ID, inactiva.id()))
                .verifyComplete();
    }

    @Test
    void daNotFoundSobreElIdCuandoLaCuentaNoEstaEntreLasVivasDelUsuario() {
        when(cuentas.findActiveByIdAndUser(AccountMother.CAJA_CHICA_ID, AccountMother.USER_ID))
                .thenReturn(Mono.empty());

        StepVerifier.create(useCase().delete(AccountMother.USER_ID, AccountMother.CAJA_CHICA_ID))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "id"))
                .verify();

        verify(cuentas, never()).softDelete(any(), any());
    }

    @Test
    void rechazaUnaCuentaConSaldoAFavorSinBorrarla() {
        guardada(AccountMother.efectivo());

        StepVerifier.create(useCase().delete(AccountMother.USER_ID, AccountMother.EFECTIVO_ID))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, ErrorCodes.RESOURCE_IN_USE,
                        "currentBalance"))
                .verify();

        verify(cuentas, never()).softDelete(any(), any());
    }

    @Test
    void rechazaUnaTarjetaConDeudaSinBorrarla() {
        guardada(AccountMother.visa());

        StepVerifier.create(useCase().delete(AccountMother.USER_ID, AccountMother.VISA_ID))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, ErrorCodes.RESOURCE_IN_USE,
                        "currentBalance"))
                .verify();

        verify(cuentas, never()).softDelete(any(), any());
    }

    /** Borrada entre la lectura y el UPDATE. */
    @Test
    void daNotFoundCuandoElUpdateNoEncuentraLaFila() {
        guardada(AccountMother.cajaChicaEnCero());
        when(cuentas.softDelete(AccountMother.CAJA_CHICA_ID, AccountMother.USER_ID)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase().delete(AccountMother.USER_ID, AccountMother.CAJA_CHICA_ID))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "id"))
                .verify();
    }

    private void guardada(Account cuenta) {
        UUID id = cuenta.id();
        when(cuentas.findActiveByIdAndUser(id, AccountMother.USER_ID)).thenReturn(Mono.just(cuenta));
    }

    private static void esError(Throwable error, HttpStatus status, ErrorCodes codigo, String campo) {
        BadRequestException bre = (BadRequestException) error;
        assertThat(bre.getHttpStatus()).isEqualTo(status);
        assertThat(bre.getErrorResponse().getErrors())
                .extracting(ErrorDetail::getCode, ErrorDetail::getField)
                .containsExactly(tuple(codigo.getCode(), campo));
    }

    private DeleteAccountUseCase useCase() {
        return new DeleteAccountUseCase(cuentas);
    }
}
