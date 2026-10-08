package com.oscargabriel.financeapp.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.port.out.UserRepositoryPort;
import com.oscargabriel.financeapp.support.UserMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class GetUserProfileUseCaseTest {

    @Mock
    private UserRepositoryPort usuarios;

    @Test
    void devuelveElPerfilDelUsuarioDelToken() {
        when(usuarios.findActiveProfile(UserMother.ID)).thenReturn(Mono.just(UserMother.unPerfil()));

        StepVerifier.create(new GetUserProfileUseCase(usuarios).get(UserMother.ID))
                .expectNext(UserMother.unPerfil())
                .verifyComplete();
    }

    /** El token sigue vigente aunque el usuario se desactive: la identidad ya no vale, no falta un recurso. */
    @Test
    void unUsuarioInactivoOBorradoEsUn401() {
        when(usuarios.findActiveProfile(UserMother.ID)).thenReturn(Mono.empty());

        StepVerifier.create(new GetUserProfileUseCase(usuarios).get(UserMother.ID))
                .verifyErrorSatisfies(error -> {
                    BadRequestException bre = (BadRequestException) error;
                    assertThat(bre.getHttpStatus().value()).isEqualTo(401);
                    assertThat(bre.getErrorResponse().getErrors()).singleElement().satisfies(detalle -> {
                        assertThat(detalle.getCode()).isEqualTo(ErrorCodes.UNAUTHENTICATED.getCode());
                        assertThat(detalle.getField()).isEqualTo("authorization");
                    });
                });
    }
}
