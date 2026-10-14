package com.oscargabriel.financeapp.core.application.usecase;

import static com.oscargabriel.financeapp.core.support.AffiliateBalanceMother.unSaldo;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.oscargabriel.financeapp.core.domain.model.AffiliateBalance;
import com.oscargabriel.financeapp.core.domain.model.AffiliationStatus;
import com.oscargabriel.financeapp.core.domain.port.out.AffiliateBalanceQueryPort;
import com.oscargabriel.financeapp.core.support.UserMother;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ListAffiliateBalancesUseCaseTest {

    @Mock
    private AffiliateBalanceQueryPort saldos;

    private ListAffiliateBalancesUseCase casoDeUso;

    @BeforeEach
    void setUp() {
        casoDeUso = new ListAffiliateBalancesUseCase(saldos);
    }

    @Test
    void devuelveElSaldoDeCadaPersonaDelUsuario() {
        AffiliateBalance afiliada = unSaldo().build();
        AffiliateBalance desvinculada = unSaldo().conEstado(AffiliationStatus.UNLINKED).build();
        when(saldos.findBalances(UserMother.ID)).thenReturn(Flux.just(afiliada, desvinculada));

        StepVerifier.create(casoDeUso.list(UserMother.ID))
                .expectNext(afiliada, desvinculada)
                .verifyComplete();
    }
}
