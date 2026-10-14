package com.oscargabriel.financeapp.core.infrastructure.adapter.out.google;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.oscargabriel.financeapp.core.domain.model.GoogleIdentity;

import reactor.test.StepVerifier;

class StubGoogleIdTokenVerifierAdapterTest {

    private final StubGoogleIdTokenVerifierAdapter stub = new StubGoogleIdTokenVerifierAdapter();

    @Test
    void leeLaIdentidadDelToken() {
        StepVerifier.create(stub.verificar("stub|g-1|Ana@Bruno.local|Ana|Gomez"))
                .expectNext(new GoogleIdentity("g-1", "ana@bruno.local", "Ana", "Gomez"))
                .verifyComplete();
    }

    /** Con el nombre vacio aplica la misma regla que un token real sin nombre. */
    @Test
    void admiteNombreYApellidoVacios() {
        StepVerifier.create(stub.verificar("stub|g-1|ana@bruno.local||"))
                .expectNext(new GoogleIdentity("g-1", "ana@bruno.local", "ana", null))
                .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "esto-no-es-un-id-token",
        "stub|faltan-partes",
        "stub|g-1|ana@bruno.local|Ana",
        "stub||ana@bruno.local|Ana|Gomez",
        "stub|g-1||Ana|Gomez",
        "otro|g-1|ana@bruno.local|Ana|Gomez"
    })
    void rechazaLoQueNoTieneLaFormaDelStub(String token) {
        StepVerifier.create(stub.verificar(token)).verifyComplete();
    }
}
