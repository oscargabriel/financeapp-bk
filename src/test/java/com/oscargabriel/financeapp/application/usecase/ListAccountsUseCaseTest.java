package com.oscargabriel.financeapp.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.oscargabriel.financeapp.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.support.AccountMother;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ListAccountsUseCaseTest {

    @Mock
    private AccountQueryPort query;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void pasaAlPuertoSiHayQueIncluirLasDesactivadas(boolean includeInactive) {
        when(query.findByUser(eq(AccountMother.USER_ID), anyBoolean())).thenReturn(Flux.empty());

        StepVerifier.create(useCase().list(AccountMother.USER_ID, includeInactive)).verifyComplete();

        verify(query).findByUser(AccountMother.USER_ID, includeInactive);
    }

    @Test
    void emiteLasCuentasEnElOrdenQueEntregaElPuerto() {
        when(query.findByUser(AccountMother.USER_ID, false)).thenReturn(
                Flux.just(AccountMother.efectivo(), AccountMother.visa()));

        StepVerifier.create(useCase().list(AccountMother.USER_ID, false))
                .assertNext(cuenta -> assertThat(cuenta.name()).isEqualTo("Efectivo"))
                .assertNext(cuenta -> assertThat(cuenta.name()).isEqualTo("Visa"))
                .verifyComplete();
    }

    private ListAccountsUseCase useCase() {
        return new ListAccountsUseCase(query);
    }
}
