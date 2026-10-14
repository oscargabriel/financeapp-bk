package com.oscargabriel.financeapp.core.application.usecase;

import static com.oscargabriel.financeapp.core.support.AccountMother.BOLSILLO_ID;
import static com.oscargabriel.financeapp.core.support.AccountMother.NOMINA_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.GASTO_GUARDADO_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.INGRESO_GUARDADO_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.TRANSFERENCIA_GUARDADA_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.USER_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.conFecha;
import static com.oscargabriel.financeapp.core.support.TransactionMother.en;
import static com.oscargabriel.financeapp.core.support.TransactionMother.unGastoGuardado;
import static com.oscargabriel.financeapp.core.support.TransactionMother.unIngresoGuardado;
import static com.oscargabriel.financeapp.core.support.TransactionMother.unaTransferenciaGuardada;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
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

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.core.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.TransactionRepositoryPort;
import com.oscargabriel.financeapp.core.support.AccountMother;
import com.oscargabriel.financeapp.compartido.support.RelojFijo;
import com.oscargabriel.financeapp.core.support.TransactionMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeleteTransactionUseCaseTest {

    @Mock
    private TransactionRepositoryPort repositorio;

    @Mock
    private AccountQueryPort cuentas;

    private DeleteTransactionUseCase casoDeUso;

    @BeforeEach
    void escenario() {
        casoDeUso = new DeleteTransactionUseCase(repositorio, cuentas, Clock.fixed(RelojFijo.AHORA, ZoneOffset.UTC));
        when(cuentas.findByUser(USER_ID, true))
                .thenReturn(Flux.just(AccountMother.nomina("10000"), AccountMother.bolsillo("5000")));
        when(repositorio.deleteByIdAndUser(any(), any())).thenReturn(Mono.just(true));
    }

    private void rechazaPorSaldo(java.util.UUID id, String descripcion) {
        StepVerifier.create(casoDeUso.delete(USER_ID, id))
                .expectErrorSatisfies(error -> {
                    BadRequestException bre = (BadRequestException) error;
                    assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(bre.getErrorResponse().getErrors())
                            .extracting(ErrorDetail::getCode, ErrorDetail::getField, ErrorDetail::getDescription)
                            .containsExactly(tuple(ErrorCodes.INSUFFICIENT_FUNDS.getCode(), "id", descripcion));
                })
                .verify();
        verify(repositorio, never()).deleteByIdAndUser(any(), any());
    }

    @Test
    void borraElMovimientoDelUsuarioYCompletaVacio() {
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID))
                .thenReturn(Mono.just(en(unGastoGuardado(), NOMINA_ID, null, "30000")));

        StepVerifier.create(casoDeUso.delete(USER_ID, GASTO_GUARDADO_ID))
                .verifyComplete();

        verify(repositorio).deleteByIdAndUser(GASTO_GUARDADO_ID, USER_ID);
    }

    /** Un pendiente compartido solo se aprueba o se rechaza: borrarlo no puede saltarse el rechazo (FIN-66). */
    @Test
    void unPendienteQueCompartioOtraPersonaNoSeBorra() {
        Transaction g = unGastoGuardado();
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(new Transaction(g.id(),
                g.userId(), g.type(), null, null, null, g.amount(), g.currencyCode(), g.description(), null,
                g.occurredAt(), TransactionStatus.PENDING, g.origin(), null, null, null, null, UUID.randomUUID(),
                TransactionMother.USUARIO_B, UUID.randomUUID())));

        StepVerifier.create(casoDeUso.delete(USER_ID, GASTO_GUARDADO_ID))
                .expectErrorSatisfies(error -> {
                    BadRequestException bre = (BadRequestException) error;
                    assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(bre.getErrorResponse().getErrors())
                            .extracting(ErrorDetail::getField, ErrorDetail::getCode)
                            .containsExactly(tuple("status", ErrorCodes.INVALID_STATE.getCode()));
                })
                .verify();
        verify(repositorio, never()).deleteByIdAndUser(any(), any());
    }

    @Test
    void unMovimientoInexistenteOAjenoEsNoEncontrado() {
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.empty());

        StepVerifier.create(casoDeUso.delete(USER_ID, GASTO_GUARDADO_ID))
                .expectErrorSatisfies(error -> {
                    BadRequestException bre = (BadRequestException) error;
                    assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(bre.getErrorResponse().getErrors())
                            .extracting(ErrorDetail::getField, ErrorDetail::getCode)
                            .containsExactly(tuple("id", ErrorCodes.NOT_FOUND.getCode()));
                })
                .verify();
        verify(repositorio, never()).deleteByIdAndUser(any(), any());
    }

    @Test
    void siDesaparecioAntesDeBorrarloEsNoEncontrado() {
        when(repositorio.findByIdAndUser(GASTO_GUARDADO_ID, USER_ID))
                .thenReturn(Mono.just(en(unGastoGuardado(), NOMINA_ID, null, "30000")));
        when(repositorio.deleteByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(false));

        StepVerifier.create(casoDeUso.delete(USER_ID, GASTO_GUARDADO_ID))
                .expectErrorSatisfies(error -> assertThat(((BadRequestException) error).getHttpStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND))
                .verify();
    }

    @Test
    void borrarUnIngresoYaGastadoSeRechaza() {
        when(repositorio.findByIdAndUser(INGRESO_GUARDADO_ID, USER_ID))
                .thenReturn(Mono.just(en(unIngresoGuardado(), NOMINA_ID, null, "100000")));

        rechazaPorSaldo(INGRESO_GUARDADO_ID, "Saldo insuficiente en Nómina: quedaría en -90000 COP");
    }

    @Test
    void borrarUnaTransferenciaQueElDestinoYaGastoSeRechaza() {
        when(repositorio.findByIdAndUser(TRANSFERENCIA_GUARDADA_ID, USER_ID))
                .thenReturn(Mono.just(en(unaTransferenciaGuardada(), NOMINA_ID, BOLSILLO_ID, "50000")));

        rechazaPorSaldo(TRANSFERENCIA_GUARDADA_ID, "Saldo insuficiente en Bolsillo: quedaría en -45000 COP");
    }

    @Test
    void borrarUnIngresoProgramadoSePermite() {
        when(repositorio.findByIdAndUser(INGRESO_GUARDADO_ID, USER_ID))
                .thenReturn(Mono.just(conFecha(en(unIngresoGuardado(), NOMINA_ID, null, "100000"), RelojFijo.DESPUES)));

        StepVerifier.create(casoDeUso.delete(USER_ID, INGRESO_GUARDADO_ID))
                .verifyComplete();
    }
}
