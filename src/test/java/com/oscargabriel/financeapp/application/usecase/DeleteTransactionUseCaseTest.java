package com.oscargabriel.financeapp.application.usecase;

import static com.oscargabriel.financeapp.support.TransactionMother.GASTO_GUARDADO_ID;
import static com.oscargabriel.financeapp.support.TransactionMother.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.port.out.TransactionRepositoryPort;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DeleteTransactionUseCaseTest {

    @Mock
    private TransactionRepositoryPort repositorio;

    @InjectMocks
    private DeleteTransactionUseCase casoDeUso;

    @Test
    void borraElMovimientoDelUsuarioYCompletaVacio() {
        when(repositorio.deleteByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(true));

        StepVerifier.create(casoDeUso.delete(USER_ID, GASTO_GUARDADO_ID))
                .verifyComplete();

        verify(repositorio).deleteByIdAndUser(GASTO_GUARDADO_ID, USER_ID);
    }

    @Test
    void unMovimientoInexistenteOAjenoEsNoEncontrado() {
        when(repositorio.deleteByIdAndUser(GASTO_GUARDADO_ID, USER_ID)).thenReturn(Mono.just(false));

        StepVerifier.create(casoDeUso.delete(USER_ID, GASTO_GUARDADO_ID))
                .expectErrorSatisfies(error -> {
                    BadRequestException bre = (BadRequestException) error;
                    assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(bre.getErrorResponse().getErrors())
                            .extracting(ErrorDetail::getField, ErrorDetail::getCode)
                            .containsExactly(tuple("id", ErrorCodes.NOT_FOUND.getCode()));
                })
                .verify();
    }
}
