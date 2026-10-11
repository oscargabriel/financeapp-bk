package com.oscargabriel.financeapp.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.UpdateUserProfileCommand;
import com.oscargabriel.financeapp.domain.model.UserProfile;
import com.oscargabriel.financeapp.domain.port.out.PasswordHasherPort;
import com.oscargabriel.financeapp.domain.port.out.UserRepositoryPort;
import com.oscargabriel.financeapp.support.UserMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UpdateUserProfileUseCaseTest {

    private static final String CORREO_NUEVO = "nuevo@correo.com";

    @Mock
    private UserRepositoryPort usuarios;

    @Mock
    private PasswordHasherPort hasher;

    @Captor
    private ArgumentCaptor<UserProfile> perfilGuardado;

    @Test
    void aplicaLosCamposRecortadosYConservaElResto() {
        actualizacionPosible();

        StepVerifier.create(useCase().update(UserMother.ID,
                        parche(" Ana Maria ", null, null, " 3109876543 ", " Europe/Madrid ", null)))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(guardado()).isEqualTo(new UserProfile(UserMother.ID, UserMother.EMAIL, "Ana Maria", null,
                "3109876543", "COP", "Europe/Madrid"));
        verifyNoInteractions(hasher);
    }

    @Test
    void elApellidoYElCelularEnBlancoSeBorran() {
        actualizacionPosible();

        StepVerifier.create(useCase().update(UserMother.ID, parche(null, "  ", null, "", null, null)))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(guardado().lastName()).isNull();
        assertThat(guardado().phone()).isNull();
        assertThat(guardado().firstName()).isEqualTo("Ana");
    }

    @Test
    void emiteElPerfilQueQuedoGuardado() {
        actualizacionPosible();

        StepVerifier.create(useCase().update(UserMother.ID, parche("Luisa", null, null, null, null, null)))
                .assertNext(perfil -> assertThat(perfil.firstName()).isEqualTo("Luisa"))
                .verifyComplete();
    }

    @Test
    void cambiaElCorreoNormalizadoConLaClaveCorrecta() {
        actualizacionPosible();
        claveCorrecta();

        StepVerifier.create(useCase().update(UserMother.ID,
                        parche(null, null, " Nuevo@Correo.COM ", null, null, UserMother.PASSWORD)))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(guardado().email()).isEqualTo(CORREO_NUEVO);
        verify(usuarios).existsByEmailForOtherUser(CORREO_NUEVO, UserMother.ID);
    }

    @Test
    void cambiarElCorreoSinLaClaveEsUnErrorDeValidacion() {
        actualizacionPosible();

        StepVerifier.create(useCase().update(UserMother.ID, parche(null, null, CORREO_NUEVO, null, null, "  ")))
                .verifyErrorSatisfies(error -> esperaError(error, 400, ErrorCodes.VALIDATION_ERROR, "currentPassword"));

        verify(usuarios, never()).updateProfile(any());
    }

    /** La clave se verifica antes que la unicidad: sin ella no se puede averiguar que correos existen. */
    @Test
    void conLaClaveEquivocadaNoCambiaNadaNiConsultaElCorreo() {
        actualizacionPosible();
        when(hasher.matches("noEsLaClave", UserMother.HASH)).thenReturn(false);

        StepVerifier.create(useCase().update(UserMother.ID, parche(null, null, CORREO_NUEVO, null, null, "noEsLaClave")))
                .verifyErrorSatisfies(error -> esperaError(error, 400, ErrorCodes.INVALID_CREDENTIALS, "currentPassword"));

        verify(usuarios, never()).existsByEmailForOtherUser(anyString(), any());
        verify(usuarios, never()).updateProfile(any());
    }

    @Test
    void elMismoCorreoEnOtraCajaNoExigeLaClave() {
        actualizacionPosible();

        StepVerifier.create(useCase().update(UserMother.ID, parche(null, null, " ANA@ejemplo.com ", null, null, null)))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(guardado().email()).isEqualTo(UserMother.EMAIL);
        verifyNoInteractions(hasher);
    }

    /** Sin cambio de correo la clave enviada se ignora: verificarla haria del PATCH un oraculo. */
    @Test
    void laClaveSinCambioDeCorreoNoSeVerifica() {
        actualizacionPosible();

        StepVerifier.create(useCase().update(UserMother.ID, parche("Luisa", null, null, null, null, "cualquiera")))
                .expectNextCount(1)
                .verifyComplete();

        verifyNoInteractions(hasher);
    }

    @Test
    void elCorreoDeOtroUsuarioEsUnConflicto() {
        actualizacionPosible();
        claveCorrecta();
        when(usuarios.existsByEmailForOtherUser(CORREO_NUEVO, UserMother.ID)).thenReturn(Mono.just(true));

        StepVerifier.create(useCase().update(UserMother.ID,
                        parche(null, null, CORREO_NUEVO, null, null, UserMother.PASSWORD)))
                .verifyErrorSatisfies(error -> esperaError(error, 409, ErrorCodes.DUPLICATE_RESOURCE, "email"));

        verify(usuarios, never()).updateProfile(any());
    }

    @Test
    void unUsuarioInactivoEsUn401() {
        when(usuarios.findActiveProfile(UserMother.ID)).thenReturn(Mono.empty());

        StepVerifier.create(useCase().update(UserMother.ID, parche("Luisa", null, null, null, null, null)))
                .verifyErrorSatisfies(error -> esperaError(error, 401, ErrorCodes.UNAUTHENTICATED, "authorization"));

        verify(usuarios, never()).updateProfile(any());
    }

    private void actualizacionPosible() {
        when(usuarios.findActiveProfile(UserMother.ID)).thenReturn(Mono.just(UserMother.unPerfil()));
        when(usuarios.findActivePasswordHash(UserMother.ID)).thenReturn(Mono.just(UserMother.HASH));
        when(usuarios.existsByEmailForOtherUser(anyString(), any())).thenReturn(Mono.just(false));
        when(usuarios.updateProfile(any())).thenAnswer(invocacion -> Mono.just(invocacion.getArgument(0)));
    }

    private void claveCorrecta() {
        when(hasher.matches(UserMother.PASSWORD, UserMother.HASH)).thenReturn(true);
    }

    private UserProfile guardado() {
        verify(usuarios).updateProfile(perfilGuardado.capture());
        return perfilGuardado.getValue();
    }

    private UpdateUserProfileUseCase useCase() {
        return new UpdateUserProfileUseCase(usuarios, hasher);
    }

    private static UpdateUserProfileCommand parche(String firstName, String lastName, String email, String phone,
            String timezone, String currentPassword) {
        return new UpdateUserProfileCommand(firstName, lastName, email, phone, timezone, currentPassword);
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
