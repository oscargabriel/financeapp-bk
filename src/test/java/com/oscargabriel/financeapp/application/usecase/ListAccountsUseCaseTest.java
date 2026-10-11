package com.oscargabriel.financeapp.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.oscargabriel.financeapp.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.support.AccountMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ListAccountsUseCaseTest {

    @Mock
    private AccountQueryPort query;

    @Mock
    private SeriesAlDia alDia;

    @BeforeEach
    void seriesAlDia() {
        lenient().when(alDia.ponerAlDia(any())).thenReturn(Mono.empty());
    }

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

    /** El saldo incluye las ocurrencias atrasadas de las series sin fin: se leen despues de crearlas (FA-107). */
    @Test
    void poneAlDiaLasSeriesAntesDeLeerLasCuentas() {
        when(alDia.ponerAlDia(AccountMother.USER_ID)).thenReturn(Mono.error(new IllegalStateException("sin base")));

        StepVerifier.create(useCase().list(AccountMother.USER_ID, false)).verifyError(IllegalStateException.class);

        verifyNoInteractions(query);
    }

    private ListAccountsUseCase useCase() {
        return new ListAccountsUseCase(query, alDia);
    }
}
