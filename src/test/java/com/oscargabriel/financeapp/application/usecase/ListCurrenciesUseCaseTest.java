package com.oscargabriel.financeapp.application.usecase;

import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.oscargabriel.financeapp.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.support.CurrencyMother;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ListCurrenciesUseCaseTest {

    @Mock
    private CurrencyQueryPort query;

    @Test
    void emiteLasMonedasActivasEnElOrdenQueEntregaElPuerto() {
        when(query.findActive()).thenReturn(Flux.just(CurrencyMother.cop(), CurrencyMother.usd()));

        StepVerifier.create(new ListCurrenciesUseCase(query).listActive())
                .expectNext(CurrencyMother.cop(), CurrencyMother.usd())
                .verifyComplete();
    }
}
