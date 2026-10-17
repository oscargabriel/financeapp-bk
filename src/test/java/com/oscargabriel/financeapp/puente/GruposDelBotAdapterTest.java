package com.oscargabriel.financeapp.puente;

import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.port.in.TransactionPort;
import com.oscargabriel.financeapp.telegram.domain.exceptions.GrupoYaNoPendienteException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class GruposDelBotAdapterTest {

    private static final UUID USUARIO = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final UUID GRUPO = UUID.fromString("71000000-0000-7000-8000-000000000001");

    @Mock
    private TransactionPort transacciones;

    @Mock
    private Transaction confirmado;

    private GruposDelBotAdapter puente;

    @BeforeEach
    void escenario() {
        puente = new GruposDelBotAdapter(transacciones);
    }

    private static BadRequestException error(HttpStatus status, String field) {
        return new BadRequestException(status, List.of(ErrorDetail.of(
                status == HttpStatus.NOT_FOUND ? ErrorCodes.NOT_FOUND.getCode() : ErrorCodes.INVALID_STATE.getCode(),
                "No", field)));
    }

    @Test
    void confirmarDiceCuantosMovimientosQuedaronConfirmados() {
        when(transacciones.approveGroup(USUARIO, GRUPO)).thenReturn(Flux.just(confirmado, confirmado));

        StepVerifier.create(puente.confirmar(USUARIO, GRUPO))
                .expectNext(2)
                .verifyComplete();
    }

    @Test
    void confirmarUnGrupoQueNoEsDelUsuarioEsYaNoPendiente() {
        when(transacciones.approveGroup(USUARIO, GRUPO)).thenReturn(Flux.error(error(HttpStatus.NOT_FOUND, "groupId")));

        StepVerifier.create(puente.confirmar(USUARIO, GRUPO))
                .verifyError(GrupoYaNoPendienteException.class);
    }

    @Test
    void confirmarUnGrupoYaConfirmadoEsYaNoPendiente() {
        when(transacciones.approveGroup(USUARIO, GRUPO)).thenReturn(Flux.error(error(HttpStatus.CONFLICT, "status")));

        StepVerifier.create(puente.confirmar(USUARIO, GRUPO))
                .verifyError(GrupoYaNoPendienteException.class);
    }

    @Test
    void unaReglaDeLaConfirmacionSaleTalCual() {
        BadRequestException regla = error(HttpStatus.CONFLICT, "[0].accountId");
        when(transacciones.approveGroup(USUARIO, GRUPO)).thenReturn(Flux.error(regla));

        StepVerifier.create(puente.confirmar(USUARIO, GRUPO))
                .verifyErrorMatches(e -> e == regla);
    }

    @Test
    void otroErrorSaleTalCual() {
        IllegalStateException caida = new IllegalStateException("la base no responde");
        when(transacciones.approveGroup(USUARIO, GRUPO)).thenReturn(Flux.error(caida));

        StepVerifier.create(puente.confirmar(USUARIO, GRUPO))
                .verifyErrorMatches(e -> e == caida);
    }

    @Test
    void descartarCompletaVacio() {
        when(transacciones.rejectGroup(USUARIO, GRUPO)).thenReturn(Mono.empty());

        StepVerifier.create(puente.descartar(USUARIO, GRUPO))
                .verifyComplete();
    }

    @Test
    void descartarUnGrupoQueYaNoEstaPendienteEsYaNoPendiente() {
        when(transacciones.rejectGroup(USUARIO, GRUPO)).thenReturn(Mono.error(error(HttpStatus.NOT_FOUND, "groupId")));

        StepVerifier.create(puente.descartar(USUARIO, GRUPO))
                .verifyError(GrupoYaNoPendienteException.class);
    }

    @Test
    void descartarConUnErrorDeOtroStatusSaleTalCual() {
        BadRequestException otro = error(HttpStatus.BAD_REQUEST, "status");
        when(transacciones.rejectGroup(USUARIO, GRUPO)).thenReturn(Mono.error(otro));

        StepVerifier.create(puente.descartar(USUARIO, GRUPO))
                .verifyErrorMatches(e -> e == otro);
    }
}
