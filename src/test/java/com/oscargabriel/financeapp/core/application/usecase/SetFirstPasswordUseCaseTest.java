package com.oscargabriel.financeapp.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.support.RelojFijo;
import com.oscargabriel.financeapp.core.domain.model.FirstPasswordCommand;
import com.oscargabriel.financeapp.core.domain.model.GoogleIdentity;
import com.oscargabriel.financeapp.core.domain.port.out.GoogleIdTokenVerifierPort;
import com.oscargabriel.financeapp.core.domain.port.out.PasswordHasherPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;
import com.oscargabriel.financeapp.core.support.GoogleMother;
import com.oscargabriel.financeapp.core.support.UserMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SetFirstPasswordUseCaseTest {

    private static final String NUEVA = "primeraClaveLarga";

    private static final String HASH_NUEVO = "$2a$10$primerHashQueNoEsReal";

    private static final FirstPasswordCommand DEFINICION = new FirstPasswordCommand(GoogleMother.ID_TOKEN, NUEVA);

    @Mock
    private UserRepositoryPort usuarios;

    @Mock
    private GoogleIdTokenVerifierPort google;

    @Mock
    private PasswordHasherPort hasher;

    /** La contrasena se guarda como el hash que produjo el hasher, atada a la cuenta Google del token. */
    @Test
    void defineLaPrimeraConElHashYLaCuentaDeGoogle() {
        sinContrasena();
        when(google.verificar(GoogleMother.ID_TOKEN)).thenReturn(Mono.just(GoogleMother.unaIdentidad()));
        when(hasher.hash(NUEVA)).thenReturn(HASH_NUEVO);
        when(usuarios.setFirstPassword(UserMother.ID, GoogleMother.SUB, HASH_NUEVO)).thenReturn(Mono.just(true));

        StepVerifier.create(useCase().define(UserMother.ID, DEFINICION))
                .verifyComplete();

        verify(usuarios).setFirstPassword(UserMother.ID, GoogleMother.SUB, HASH_NUEVO);
    }

    @Test
    void unUsuarioInactivoEsUn401() {
        when(usuarios.findActiveCredentials(UserMother.ID)).thenReturn(Mono.empty());

        StepVerifier.create(useCase().define(UserMother.ID, DEFINICION))
                .verifyErrorSatisfies(error -> esperaError(error, 401, ErrorCodes.UNAUTHENTICATED, "authorization"));

        verifyNoInteractions(google);
    }

    /** El estado va antes que Google: no se verifica un token para algo que se va a rechazar (decision 5). */
    @Test
    void conContrasenaEsUn409SinLlamarAGoogle() {
        when(usuarios.findActiveCredentials(UserMother.ID)).thenReturn(Mono.just(UserMother.credenciales()));

        StepVerifier.create(useCase().define(UserMother.ID, DEFINICION))
                .verifyErrorSatisfies(error -> esperaError(error, 409, ErrorCodes.INVALID_STATE, "newPassword"));

        verifyNoInteractions(google, hasher);
    }

    @Test
    void unTokenInvalidoEsUn400EnIdToken() {
        sinContrasena();
        when(google.verificar(GoogleMother.ID_TOKEN)).thenReturn(Mono.empty());

        StepVerifier.create(useCase().define(UserMother.ID, DEFINICION))
                .verifyErrorSatisfies(error -> esperaError(error, 400, ErrorCodes.INVALID_CREDENTIALS, "idToken"));

        nadaSeGuardo();
    }

    /** El 502 lo arma el adapter: el caso de uso lo deja pasar tal cual. */
    @Test
    void googleNoDisponiblePasaTalCual() {
        BadRequestException noDisponible = new BadRequestException(HttpStatus.BAD_GATEWAY,
                ErrorCodes.EXTERNAL_SERVICE_ERROR, "El servicio de Google no esta disponible", "idToken");
        sinContrasena();
        when(google.verificar(GoogleMother.ID_TOKEN)).thenReturn(Mono.error(noDisponible));

        StepVerifier.create(useCase().define(UserMother.ID, DEFINICION))
                .verifyErrorSatisfies(error -> assertThat(error).isSameAs(noDisponible));

        nadaSeGuardo();
    }

    /** Los bordes de la ventana de 5 minutos, antes y despues de ahora, se aceptan (decision 2). */
    @ParameterizedTest
    @ValueSource(longs = {-300, 0, 300})
    void aceptaUnTokenEmitidoDentroDeLaVentana(long segundos) {
        sinContrasena();
        when(google.verificar(GoogleMother.ID_TOKEN))
                .thenReturn(Mono.just(GoogleMother.emitidaEn(RelojFijo.AHORA.plusSeconds(segundos))));
        when(hasher.hash(NUEVA)).thenReturn(HASH_NUEVO);
        when(usuarios.setFirstPassword(UserMother.ID, GoogleMother.SUB, HASH_NUEVO)).thenReturn(Mono.just(true));

        StepVerifier.create(useCase().define(UserMother.ID, DEFINICION))
                .verifyComplete();
    }

    /**
     * Fuera de la ventana: viejo, o adelante. 3600 es el iat que Spring deduce (exp - 1 s) de un token
     * que no lo trae (decision 2).
     */
    @ParameterizedTest
    @ValueSource(longs = {-301, -600, 301, 3600})
    void rechazaUnTokenEmitidoFueraDeLaVentana(long segundos) {
        sinContrasena();
        when(google.verificar(GoogleMother.ID_TOKEN))
                .thenReturn(Mono.just(GoogleMother.emitidaEn(RelojFijo.AHORA.plusSeconds(segundos))));

        StepVerifier.create(useCase().define(UserMother.ID, DEFINICION))
                .verifyErrorSatisfies(error -> esperaError(error, 400, ErrorCodes.INVALID_CREDENTIALS, "idToken"));

        nadaSeGuardo();
    }

    @Test
    void rechazaUnTokenSinEmision() {
        sinContrasena();
        when(google.verificar(GoogleMother.ID_TOKEN)).thenReturn(Mono.just(GoogleMother.emitidaEn(null)));

        StepVerifier.create(useCase().define(UserMother.ID, DEFINICION))
                .verifyErrorSatisfies(error -> esperaError(error, 400, ErrorCodes.INVALID_CREDENTIALS, "idToken"));

        nadaSeGuardo();
    }

    /** Valido y reciente, pero de otra cuenta Google: cuenta el sub, no el correo. */
    @Test
    void rechazaUnTokenDeOtraCuentaDeGoogle() {
        sinContrasena();
        GoogleIdentity otraCuenta = new GoogleIdentity("otro-sub", UserMother.EMAIL, "Ana", "Gomez", RelojFijo.AHORA);
        when(google.verificar(GoogleMother.ID_TOKEN)).thenReturn(Mono.just(otraCuenta));

        StepVerifier.create(useCase().define(UserMother.ID, DEFINICION))
                .verifyErrorSatisfies(error -> esperaError(error, 400, ErrorCodes.INVALID_CREDENTIALS, "idToken"));

        nadaSeGuardo();
    }

    /** Otra peticion la definio entre la lectura y la escritura: la escritura condicional no la pisa. */
    @Test
    void siLaEscrituraNoGuardaEsUn409() {
        sinContrasena();
        when(google.verificar(GoogleMother.ID_TOKEN)).thenReturn(Mono.just(GoogleMother.unaIdentidad()));
        when(hasher.hash(NUEVA)).thenReturn(HASH_NUEVO);
        when(usuarios.setFirstPassword(UserMother.ID, GoogleMother.SUB, HASH_NUEVO)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase().define(UserMother.ID, DEFINICION))
                .verifyErrorSatisfies(error -> esperaError(error, 409, ErrorCodes.INVALID_STATE, "newPassword"));
    }

    private void sinContrasena() {
        when(usuarios.findActiveCredentials(UserMother.ID)).thenReturn(Mono.just(UserMother.credencialesDeGoogle()));
    }

    private void nadaSeGuardo() {
        verify(hasher, never()).hash(anyString());
        verify(usuarios, never()).setFirstPassword(any(), anyString(), anyString());
    }

    private SetFirstPasswordUseCase useCase() {
        return new SetFirstPasswordUseCase(usuarios, google, hasher, Clock.fixed(RelojFijo.AHORA, ZoneOffset.UTC));
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
