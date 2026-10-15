package com.oscargabriel.financeapp.core.infrastructure.adapter.out.google;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.oscargabriel.financeapp.compartido.support.RelojFijo;
import com.oscargabriel.financeapp.core.domain.model.GoogleIdentity;

import reactor.test.StepVerifier;

class StubGoogleIdTokenVerifierAdapterTest {

    private final StubGoogleIdTokenVerifierAdapter stub =
            new StubGoogleIdTokenVerifierAdapter(Clock.fixed(RelojFijo.AHORA, ZoneOffset.UTC));

    /** Sin sexto campo, el token sale emitido en el instante del reloj. */
    @Test
    void leeLaIdentidadDelToken() {
        StepVerifier.create(stub.verificar("stub|g-1|Ana@Bruno.local|Ana|Gomez"))
                .expectNext(new GoogleIdentity("g-1", "ana@bruno.local", "Ana", "Gomez", RelojFijo.AHORA))
                .verifyComplete();
    }

    /** Con el nombre vacio aplica la misma regla que un token real sin nombre. */
    @Test
    void admiteNombreYApellidoVacios() {
        StepVerifier.create(stub.verificar("stub|g-1|ana@bruno.local||"))
                .expectNext(new GoogleIdentity("g-1", "ana@bruno.local", "ana", null, RelojFijo.AHORA))
                .verifyComplete();
    }

    /** El sexto campo es el iat en segundos epoch (FIN-53): asi bruno/ prueba un token viejo. */
    @Test
    void elSextoCampoEsElInstanteDeEmision() {
        Instant haceDiezMinutos = RelojFijo.AHORA.minusSeconds(600);

        StepVerifier.create(stub.verificar("stub|g-1|ana@bruno.local|Ana|Gomez|" + haceDiezMinutos.getEpochSecond()))
                .expectNext(new GoogleIdentity("g-1", "ana@bruno.local", "Ana", "Gomez", haceDiezMinutos))
                .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "esto-no-es-un-id-token",
        "stub|faltan-partes",
        "stub|g-1|ana@bruno.local|Ana",
        "stub||ana@bruno.local|Ana|Gomez",
        "stub|g-1||Ana|Gomez",
        "otro|g-1|ana@bruno.local|Ana|Gomez",
        "stub|g-1|ana@bruno.local|Ana|Gomez|no-es-un-numero",
        "stub|g-1|ana@bruno.local|Ana|Gomez|1790000000|sobra"
    })
    void rechazaLoQueNoTieneLaFormaDelStub(String token) {
        StepVerifier.create(stub.verificar(token)).verifyComplete();
    }
}
