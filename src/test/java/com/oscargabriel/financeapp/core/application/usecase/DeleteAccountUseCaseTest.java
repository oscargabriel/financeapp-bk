package com.oscargabriel.financeapp.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.Account;
import com.oscargabriel.financeapp.core.domain.port.out.AccountRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.RecurrenceRepositoryPort;
import com.oscargabriel.financeapp.core.support.AccountMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DeleteAccountUseCaseTest {

    @Mock
    private AccountRepositoryPort cuentas;

    @Mock
    private RecurrenceRepositoryPort series;

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

    @Test
    void rechazaUnaCuentaEnCeroConProgramadosSobreElId() {
        guardada(AccountMother.cajaChicaEnCero());
        conProgramados(AccountMother.CAJA_CHICA_ID, 1L);

        StepVerifier.create(useCase().delete(AccountMother.USER_ID, AccountMother.CAJA_CHICA_ID))
                .expectErrorSatisfies(error -> {
                    esError(error, HttpStatus.CONFLICT, ErrorCodes.RESOURCE_IN_USE, "id");
                    assertThat(((BadRequestException) error).getErrorResponse().getErrors().getFirst().getDescription())
                            .contains("1 movimiento programado");
                })
                .verify();

        verify(cuentas, never()).softDelete(any(), any());
    }

    @Test
    void conSaldoYProgramadosReportaLosDosErrores() {
        guardada(AccountMother.efectivo());
        conProgramados(AccountMother.EFECTIVO_ID, 3L);

        StepVerifier.create(useCase().delete(AccountMother.USER_ID, AccountMother.EFECTIVO_ID))
                .expectErrorSatisfies(error -> {
                    BadRequestException bre = (BadRequestException) error;
                    assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(bre.getErrorResponse().getErrors())
                            .extracting(ErrorDetail::getCode, ErrorDetail::getField)
                            .containsExactlyInAnyOrder(
                                    tuple(ErrorCodes.RESOURCE_IN_USE.getCode(), "currentBalance"),
                                    tuple(ErrorCodes.RESOURCE_IN_USE.getCode(), "id"));
                })
                .verify();

        verify(cuentas, never()).softDelete(any(), any());
    }

    /** Toda serie sin fin activa tiene una programada: los dos van en el mismo error de id. */
    @Test
    void conSerieYProgramadoDaUnSoloErrorEnElIdQueMencionaLosDos() {
        guardada(AccountMother.cajaChicaEnCero());
        conProgramados(AccountMother.CAJA_CHICA_ID, 1L);
        conSeries(AccountMother.CAJA_CHICA_ID, 1L);

        StepVerifier.create(useCase().delete(AccountMother.USER_ID, AccountMother.CAJA_CHICA_ID))
                .expectErrorSatisfies(error -> {
                    esError(error, HttpStatus.CONFLICT, ErrorCodes.RESOURCE_IN_USE, "id");
                    assertThat(descripcion(error)).contains("1 serie activa", "1 movimiento programado");
                })
                .verify();

        verify(cuentas, never()).softDelete(any(), any());
    }

    /** El hueco de FA-115: la programada de la serie se borro a mano y la serie sigue activa. */
    @Test
    void conSerieYSinProgramadosRechazaPorLaSerie() {
        guardada(AccountMother.cajaChicaEnCero());
        conSeries(AccountMother.CAJA_CHICA_ID, 2L);

        StepVerifier.create(useCase().delete(AccountMother.USER_ID, AccountMother.CAJA_CHICA_ID))
                .expectErrorSatisfies(error -> {
                    esError(error, HttpStatus.CONFLICT, ErrorCodes.RESOURCE_IN_USE, "id");
                    assertThat(descripcion(error)).contains("2 series activas").doesNotContain("programado");
                })
                .verify();

        verify(cuentas, never()).softDelete(any(), any());
    }

    @Test
    void sinSeriesElMensajeDeLosProgramadosNoCambia() {
        guardada(AccountMother.cajaChicaEnCero());
        conProgramados(AccountMother.CAJA_CHICA_ID, 2L);

        StepVerifier.create(useCase().delete(AccountMother.USER_ID, AccountMother.CAJA_CHICA_ID))
                .expectErrorSatisfies(error -> assertThat(descripcion(error)).isEqualTo(
                        "La cuenta tiene 2 movimientos programados: cancela la serie o la compra en cuotas, o borra"
                                + " esos movimientos, antes de borrarla"))
                .verify();
    }

    @Test
    void conSaldoYUnaSerieReportaLosDosErrores() {
        guardada(AccountMother.efectivo());
        conSeries(AccountMother.EFECTIVO_ID, 1L);

        StepVerifier.create(useCase().delete(AccountMother.USER_ID, AccountMother.EFECTIVO_ID))
                .expectErrorSatisfies(error -> assertThat(((BadRequestException) error).getErrorResponse().getErrors())
                        .extracting(ErrorDetail::getCode, ErrorDetail::getField)
                        .containsExactlyInAnyOrder(
                                tuple(ErrorCodes.RESOURCE_IN_USE.getCode(), "currentBalance"),
                                tuple(ErrorCodes.RESOURCE_IN_USE.getCode(), "id")))
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
        lenient().when(cuentas.countScheduled(id)).thenReturn(Mono.just(0L));
        lenient().when(series.countActiveByAccount(id, AccountMother.USER_ID)).thenReturn(Mono.just(0L));
    }

    private void conProgramados(UUID id, long cuantos) {
        when(cuentas.countScheduled(id)).thenReturn(Mono.just(cuantos));
    }

    private void conSeries(UUID id, long cuantas) {
        when(series.countActiveByAccount(id, AccountMother.USER_ID)).thenReturn(Mono.just(cuantas));
    }

    private static String descripcion(Throwable error) {
        return ((BadRequestException) error).getErrorResponse().getErrors().stream()
                .filter(detalle -> "id".equals(detalle.getField()))
                .findFirst().orElseThrow().getDescription();
    }

    private static void esError(Throwable error, HttpStatus status, ErrorCodes codigo, String campo) {
        BadRequestException bre = (BadRequestException) error;
        assertThat(bre.getHttpStatus()).isEqualTo(status);
        assertThat(bre.getErrorResponse().getErrors())
                .extracting(ErrorDetail::getCode, ErrorDetail::getField)
                .containsExactly(tuple(codigo.getCode(), campo));
    }

    private DeleteAccountUseCase useCase() {
        return new DeleteAccountUseCase(cuentas, series);
    }
}
