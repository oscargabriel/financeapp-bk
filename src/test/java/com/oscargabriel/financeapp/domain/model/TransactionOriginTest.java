package com.oscargabriel.financeapp.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TransactionOriginTest {

    @Test
    void loQueRegistraElAsistenteEntraPendiente() {
        assertThat(TransactionOrigin.TELEGRAM.estadoInicial()).isEqualTo(TransactionStatus.PENDING);
    }

    @ParameterizedTest
    @EnumSource(value = TransactionOrigin.class, names = {"WEB", "IMPORT"})
    void loDemasEntraConfirmado(TransactionOrigin origen) {
        assertThat(origen.estadoInicial()).isEqualTo(TransactionStatus.CONFIRMED);
    }
}
