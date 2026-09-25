package com.oscargabriel.financeapp.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.oscargabriel.financeapp.support.AccountMother;

class AccountTest {

    /** Un saldo negativo es deuda: resta del cupo. */
    @Test
    void elCupoDeUnaTarjetaConDeudaEsElLimiteMenosLaDeuda() {
        assertThat(AccountMother.visa().availableCredit()).isEqualByComparingTo(new BigDecimal("4342000"));
    }

    @Test
    void unSaldoAFavorAumentaElCupoDisponible() {
        assertThat(AccountMother.visaConSaldoAFavor().availableCredit())
                .isEqualByComparingTo(new BigDecimal("5120000"));
    }

    @Test
    void unaTarjetaSinLimiteNoTieneCupoQueCalcular() {
        assertThat(AccountMother.creditoSinLimite().availableCredit()).isNull();
    }

    @Test
    void unaCuentaQueNoEsDeCreditoNoTieneCupo() {
        assertThat(AccountMother.efectivo().availableCredit()).isNull();
    }
}
