package com.oscargabriel.financeapp.core.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GoogleIdentityTest {

    @Test
    void normalizaElCorreoComoElAlta() {
        GoogleIdentity identidad = GoogleIdentity.desdeClaims("sub-1", "  Ana@Ejemplo.COM ", "Ana", null, "Gomez");

        assertThat(identidad.email()).isEqualTo("ana@ejemplo.com");
    }

    @Test
    void tomaElNombreDePilaYElApellido() {
        GoogleIdentity identidad = GoogleIdentity.desdeClaims("sub-1", "ana@ejemplo.com", " Ana ", "Ana Gomez", " Gomez ");

        assertThat(identidad).isEqualTo(new GoogleIdentity("sub-1", "ana@ejemplo.com", "Ana", "Gomez"));
    }

    @Test
    void sinNombreDePilaUsaElNombreCompleto() {
        GoogleIdentity identidad = GoogleIdentity.desdeClaims("sub-1", "ana@ejemplo.com", "  ", "Ana Gomez", null);

        assertThat(identidad.firstName()).isEqualTo("Ana Gomez");
        assertThat(identidad.lastName()).isNull();
    }

    @Test
    void sinNingunNombreUsaLaParteDelCorreoAnteriorALaArroba() {
        GoogleIdentity identidad = GoogleIdentity.desdeClaims("sub-1", "Ana.Gomez@ejemplo.com", null, "", "");

        assertThat(identidad.firstName()).isEqualTo("ana.gomez");
        assertThat(identidad.lastName()).isNull();
    }

    /** users.first_name y last_name son VARCHAR(100). */
    @Test
    void recortaElNombreYElApellidoACienCaracteres() {
        String largo = "a".repeat(130);

        GoogleIdentity identidad = GoogleIdentity.desdeClaims("sub-1", "ana@ejemplo.com", largo, null, largo);

        assertThat(identidad.firstName()).hasSize(100);
        assertThat(identidad.lastName()).hasSize(100);
    }
}
