package com.oscargabriel.financeapp.puente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.core.domain.model.CreateTransactionCommand;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.model.TransactionOrigin;
import com.oscargabriel.financeapp.core.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.core.domain.port.in.CreateTransactionsPort;
import com.oscargabriel.financeapp.ia.domain.model.NewMovement;
import com.oscargabriel.financeapp.ia.domain.model.RegisteredMovement;
import com.oscargabriel.financeapp.ia.domain.model.TransactionType;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PendienteDelAsistenteAdapterTest {

    private static final UUID USUARIO = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final UUID CUENTA = UUID.fromString("10000000-0000-7000-8000-000000000001");
    private static final UUID DESTINO = UUID.fromString("10000000-0000-7000-8000-000000000002");
    private static final UUID CATEGORIA = UUID.fromString("20000000-0000-7000-8000-000000000001");
    private static final UUID GUARDADO = UUID.fromString("30000000-0000-7000-8000-000000000001");
    private static final Instant CUANDO = Instant.parse("2026-10-05T17:00:00Z");

    @Mock
    private CreateTransactionsPort alta;

    @Captor
    private ArgumentCaptor<List<CreateTransactionCommand>> lote;

    private PendienteDelAsistenteAdapter puente;

    @BeforeEach
    void escenario() {
        puente = new PendienteDelAsistenteAdapter(alta);
    }

    private static Transaction guardado() {
        return new Transaction(GUARDADO, USUARIO, com.oscargabriel.financeapp.core.domain.model.TransactionType.EXPENSE,
                CUENTA, null, CATEGORIA, new BigDecimal("20000"), "COP", "Almuerzo", null, CUANDO,
                TransactionStatus.PENDING, TransactionOrigin.TELEGRAM, null, null, null, null);
    }

    @Test
    void registraUnLoteDeUnoConElOrigenDelAsistenteYDevuelveIdEstadoYFecha() {
        when(alta.create(eq(USUARIO), eq(TransactionOrigin.TELEGRAM), anyList())).thenReturn(Flux.just(guardado()));

        StepVerifier.create(puente.register(USUARIO, new NewMovement(TransactionType.EXPENSE, CUENTA, null, CATEGORIA,
                        new BigDecimal("20000"), "COP", "Almuerzo", "2026-10-05T12:00-05:00")))
                .expectNext(new RegisteredMovement(GUARDADO, "PENDING", CUANDO))
                .verifyComplete();

        verify(alta).create(eq(USUARIO), eq(TransactionOrigin.TELEGRAM), lote.capture());
        assertThat(lote.getValue()).containsExactly(new CreateTransactionCommand("EXPENSE", CUENTA.toString(), null,
                CATEGORIA.toString(), new BigDecimal("20000"), null, "COP", "Almuerzo", null,
                "2026-10-05T12:00-05:00"));
    }

    @Test
    void unaTransferenciaLlevaLaCuentaDestinoYNoLaCategoria() {
        when(alta.create(eq(USUARIO), eq(TransactionOrigin.TELEGRAM), anyList())).thenReturn(Flux.just(guardado()));

        StepVerifier.create(puente.register(USUARIO, new NewMovement(TransactionType.TRANSFER, CUENTA, DESTINO, null,
                        new BigDecimal("100000"), "COP", "Al ahorro", null)))
                .expectNextCount(1)
                .verifyComplete();

        verify(alta).create(eq(USUARIO), eq(TransactionOrigin.TELEGRAM), lote.capture());
        assertThat(lote.getValue().getFirst()).extracting(CreateTransactionCommand::type,
                        CreateTransactionCommand::destinationAccountId, CreateTransactionCommand::categoryId,
                        CreateTransactionCommand::occurredAt)
                .containsExactly("TRANSFER", DESTINO.toString(), null, null);
    }

    @Test
    void propagaElRechazoDelAltaTalCual() {
        BadRequestException rechazo = new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                "La cuenta esta desactivada", "[0].accountId");
        when(alta.create(eq(USUARIO), any(), anyList())).thenReturn(Flux.error(rechazo));

        StepVerifier.create(puente.register(USUARIO, new NewMovement(TransactionType.EXPENSE, CUENTA, null, CATEGORIA,
                        new BigDecimal("20000"), "COP", "Almuerzo", null)))
                .verifyErrorMatches(e -> e == rechazo);
    }
}
