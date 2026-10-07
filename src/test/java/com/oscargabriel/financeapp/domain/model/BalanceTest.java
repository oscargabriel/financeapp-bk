package com.oscargabriel.financeapp.domain.model;

import static com.oscargabriel.financeapp.support.AccountMother.efectivo;
import static com.oscargabriel.financeapp.support.AccountMother.visa;
import static com.oscargabriel.financeapp.support.BalanceMother.DESDE;
import static com.oscargabriel.financeapp.support.BalanceMother.HASTA;
import static com.oscargabriel.financeapp.support.BalanceMother.sumasDelEscenario;
import static com.oscargabriel.financeapp.support.BalanceMother.totales;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class BalanceTest {

    @Test
    void elNetoEsIngresosMenosGastos() {
        assertThat(totales("5300000.0000", "1905500.0000").net()).isEqualByComparingTo("3394500");
    }

    @Test
    void sinIngresosElNetoSaleNegativo() {
        assertThat(totales("0", "620000.0000").net()).isEqualByComparingTo("-620000");
    }

    @Test
    void juntaElRangoLasSumasYLasCuentas() {
        Balance saldo = Balance.of(DESDE, HASTA, sumasDelEscenario(), List.of(efectivo(), visa()));

        assertThat(saldo.currencyCode()).isEqualTo("COP");
        assertThat(saldo.from()).isEqualTo(DESDE);
        assertThat(saldo.to()).isEqualTo(HASTA);
        assertThat(saldo.period().net()).isEqualByComparingTo("3394500");
        assertThat(saldo.allTime().net()).isEqualByComparingTo("9129500");
        assertThat(saldo.accounts()).containsExactly(efectivo(), visa());
    }
}
