package com.oscargabriel.financeapp.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class UuidV7Test {

    private static final Instant MOMENTO = Instant.parse("2026-09-15T15:00:00Z");

    @Test
    void marcaLaVersionSieteYLaVarianteRfc() {
        UUID id = UuidV7.from(MOMENTO);

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void guardaElInstanteEnLosPrimerosCuarentaYOchoBits() {
        UUID id = UuidV7.from(MOMENTO);

        assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(MOMENTO.toEpochMilli());
    }

    /** La razon de usar v7 y no v4: ids de momentos distintos ordenan como los momentos. */
    @Test
    void ordenaComoElTiempoAunqueSeanDeMilisegundosConsecutivos() {
        UUID antes = UuidV7.from(MOMENTO);
        UUID despues = UuidV7.from(MOMENTO.plusMillis(1));

        assertThat(antes.toString()).isLessThan(despues.toString());
    }

    @Test
    void noRepiteIdsDentroDelMismoMilisegundo() {
        Set<UUID> generados = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            generados.add(UuidV7.from(MOMENTO));
        }

        assertThat(generados).hasSize(1000);
    }
}
