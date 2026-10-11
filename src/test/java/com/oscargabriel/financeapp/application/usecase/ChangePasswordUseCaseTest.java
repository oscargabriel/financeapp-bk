package com.oscargabriel.financeapp.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.ChangePasswordCommand;
import com.oscargabriel.financeapp.domain.port.out.PasswordHasherPort;
import com.oscargabriel.financeapp.domain.port.out.UserRepositoryPort;
import com.oscargabriel.financeapp.support.UserMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ChangePasswordUseCaseTest {

    private static final String NUEVA = "otraClaveLarga";

    private static final String HASH_NUEVO = "$2a$10$otroHashQueNoEsReal";

    @Mock
    private UserRepositoryPort usuarios;

    @Mock
    private PasswordHasherPort hasher;

    /** La nueva se guarda como el hash que produjo el hasher, nunca en claro. */
    @Test
    void guardaElHashDeLaNuevaSiLaActualCoincide() {
        when(usuarios.findActivePasswordHash(UserMother.ID)).thenReturn(Mono.just(UserMother.HASH));
        when(hasher.matches(UserMother.PASSWORD, UserMother.HASH)).thenReturn(true);
        when(hasher.hash(NUEVA)).thenReturn(HASH_NUEVO);
        when(usuarios.updatePassword(UserMother.ID, HASH_NUEVO)).thenReturn(Mono.empty());

        StepVerifier.create(useCase().change(UserMother.ID, new ChangePasswordCommand(UserMother.PASSWORD, NUEVA)))
                .verifyComplete();

        verify(usuarios).updatePassword(UserMother.ID, HASH_NUEVO);
        verify(usuarios, never()).updatePassword(UserMother.ID, NUEVA);
    }

    @Test
    void conLaActualEquivocadaNoCambiaNada() {
        when(usuarios.findActivePasswordHash(UserMother.ID)).thenReturn(Mono.just(UserMother.HASH));
        when(hasher.matches("noEsLaClave", UserMother.HASH)).thenReturn(false);

        StepVerifier.create(useCase().change(UserMother.ID, new ChangePasswordCommand("noEsLaClave", NUEVA)))
                .verifyErrorSatisfies(error -> esperaError(error, 400, ErrorCodes.INVALID_CREDENTIALS, "currentPassword"));

        verify(hasher, never()).hash(anyString());
        verify(usuarios, never()).updatePassword(any(), anyString());
    }

    @Test
    void unUsuarioInactivoEsUn401() {
        when(usuarios.findActivePasswordHash(UserMother.ID)).thenReturn(Mono.empty());

        StepVerifier.create(useCase().change(UserMother.ID, new ChangePasswordCommand(UserMother.PASSWORD, NUEVA)))
                .verifyErrorSatisfies(error -> esperaError(error, 401, ErrorCodes.UNAUTHENTICATED, "authorization"));

        verify(usuarios, never()).updatePassword(any(), anyString());
    }

    private ChangePasswordUseCase useCase() {
        return new ChangePasswordUseCase(usuarios, hasher);
    }

    private static void esperaError(Throwable error, int status, ErrorCodes codigo, String campo) {
        BadRequestException bre = (BadRequestException) error;
        assertThat(bre.getHttpStatus().value()).isEqualTo(status);
        assertThat(bre.getErrorResponse().getErrors()).singleElement().satisfies(detalle -> {
            assertThat(detalle.getCode()).isEqualTo(codigo.getCode());
            assertThat(detalle.getField()).isEqualTo(campo);
        });
    }
}
