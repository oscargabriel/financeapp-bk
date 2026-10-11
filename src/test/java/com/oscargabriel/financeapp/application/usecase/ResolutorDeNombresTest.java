package com.oscargabriel.financeapp.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ResolutorDeNombresTest {

    private final ResolutorDeNombres<String> cuentas = new ResolutorDeNombres<>(
            List.of("Nequi", "Tarjeta Débito", "Efectivo"), Function.identity());

    @ParameterizedTest
    @ValueSource(strings = {"Tarjeta Débito", "tarjeta debito", "  TARJETA DÉBITO  "})
    void encuentraElNombreSinMayusculasTildesNiEspaciosEnLosExtremos(String nombre) {
        assertThat(cuentas.buscar(nombre)).isEqualTo(new ResolutorDeNombres.Unico<>("Tarjeta Débito"));
    }

    @Test
    void unNombreQueNoEstaNoSeResuelve() {
        assertThat(cuentas.buscar("Bancolombia")).isEqualTo(new ResolutorDeNombres.Ninguno<>());
    }

    /** Coincidir de forma parcial seria adivinar: «tarjeta» no elige «Tarjeta Débito». */
    @Test
    void unNombreParcialNoSeResuelve() {
        assertThat(cuentas.buscar("tarjeta")).isEqualTo(new ResolutorDeNombres.Ninguno<>());
    }

    @Test
    void sinNombreNoSeResuelve() {
        assertThat(cuentas.buscar(null)).isEqualTo(new ResolutorDeNombres.Ninguno<>());
    }

    @Test
    void dosQueNormalizanIgualSonAmbiguos() {
        ResolutorDeNombres<String> categorias = new ResolutorDeNombres<>(
                List.of("Café", "cafe ", "Mercado"), Function.identity());

        assertThat(categorias.buscar("CAFE")).isEqualTo(new ResolutorDeNombres.Varios<>());
    }
}
