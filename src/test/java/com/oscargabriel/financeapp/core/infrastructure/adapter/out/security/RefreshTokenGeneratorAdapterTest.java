package com.oscargabriel.financeapp.core.infrastructure.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RefreshTokenGeneratorAdapterTest {

    private final RefreshTokenGeneratorAdapter generador = new RefreshTokenGeneratorAdapter();

    /** 32 bytes en base64url sin relleno son 43 caracteres: 256 bits que no se adivinan. */
    @Test
    void generaUnValorDe256BitsEnBase64Url() {
        assertThat(generador.generar()).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void cadaValorEsDistinto() {
        assertThat(generador.generar()).isNotEqualTo(generador.generar());
    }

    /** Es lo que se guarda y lo que se busca: el mismo valor tiene que dar el mismo hash. */
    @Test
    void elHashEsDeterministaYDe64Hexadecimales() {
        String valor = generador.generar();

        assertThat(generador.hash(valor)).isEqualTo(generador.hash(valor)).matches("[0-9a-f]{64}");
    }

    /** El mismo SHA-256 que calcula test-data.sql con encode(sha256(...), 'hex'). */
    @Test
    void elHashEsElSha256EnHexadecimal() {
        assertThat(generador.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void elHashNoContieneElValor() {
        String valor = generador.generar();

        assertThat(generador.hash(valor)).doesNotContain(valor).isNotEqualTo(generador.hash(generador.generar()));
    }
}
