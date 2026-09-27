package com.oscargabriel.financeapp.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.AccessToken;
import com.oscargabriel.financeapp.domain.model.LoginCommand;
import com.oscargabriel.financeapp.domain.model.UserCredentials;
import com.oscargabriel.financeapp.domain.port.out.PasswordHasherPort;
import com.oscargabriel.financeapp.domain.port.out.TokenIssuerPort;
import com.oscargabriel.financeapp.domain.port.out.UserRepositoryPort;
import com.oscargabriel.financeapp.support.UserMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginUseCaseTest {

    private static final UUID ID = UUID.fromString("01994f00-0000-7000-8000-000000000001");

    private static final AccessToken TOKEN = new AccessToken("un.jwt.firmado", Duration.ofHours(1));

    @Mock
    private UserRepositoryPort usuarios;

    @Mock
    private PasswordHasherPort hasher;

    @Mock
    private TokenIssuerPort emisor;

    @Test
    void emiteElTokenDelUsuarioCuandoLaClaveCoincide() {
        loginPosible();

        StepVerifier.create(login().login(new LoginCommand(UserMother.EMAIL, UserMother.PASSWORD)))
                .expectNext(TOKEN)
                .verifyComplete();

        verify(emisor).issueFor(ID);
    }

    /** El indice unico de la tabla es sobre lower(email), y el alta guarda el correo normalizado. */
    @Test
    void normalizaElCorreoAntesDeBuscarlo() {
        loginPosible();

        StepVerifier.create(login().login(new LoginCommand("  ANA@Ejemplo.COM  ", UserMother.PASSWORD)))
                .expectNext(TOKEN)
                .verifyComplete();

        verify(usuarios).findActiveByEmail(UserMother.EMAIL);
    }

    @Test
    void devuelve401CuandoLaClaveNoCoincide() {
        when(usuarios.findActiveByEmail(anyString())).thenReturn(Mono.just(credenciales()));
        when(hasher.matches(anyString(), anyString())).thenReturn(false);

        StepVerifier.create(login().login(new LoginCommand(UserMother.EMAIL, "otraClave")))
                .verifyErrorSatisfies(error -> assertThat(unaCredencialInvalida(error)).isTrue());

        verifyNoInteractions(emisor);
    }

    /**
     * El puerto no emite nada para un correo desconocido, uno borrado o uno inactivo: los tres
     * caminos tienen que llegar al mismo error, sin pasar por el emisor.
     */
    @Test
    void devuelve401CuandoNoHayUsuarioQuePuedaAutenticarse() {
        when(usuarios.findActiveByEmail(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(login().login(new LoginCommand(UserMother.EMAIL, UserMother.PASSWORD)))
                .verifyErrorSatisfies(error -> assertThat(unaCredencialInvalida(error)).isTrue());

        verifyNoInteractions(emisor);
    }

    /** OWASP A07: los dos fallos son indistinguibles desde fuera, hasta en el texto. */
    @Test
    void noDistingueElCorreoDesconocidoDeLaClaveIncorrecta() {
        when(usuarios.findActiveByEmail(anyString())).thenReturn(Mono.empty());
        BadRequestException sinUsuario = capturarError(
                login().login(new LoginCommand(UserMother.EMAIL, UserMother.PASSWORD)));

        when(usuarios.findActiveByEmail(anyString())).thenReturn(Mono.just(credenciales()));
        when(hasher.matches(anyString(), anyString())).thenReturn(false);
        BadRequestException claveMala = capturarError(
                login().login(new LoginCommand(UserMother.EMAIL, "otraClave")));

        assertThat(sinUsuario.getHttpStatus()).isEqualTo(claveMala.getHttpStatus());
        assertThat(detalle(sinUsuario).getCode()).isEqualTo(detalle(claveMala).getCode());
        assertThat(detalle(sinUsuario).getDescription())
                .isEqualTo(detalle(claveMala).getDescription());
        assertThat(detalle(sinUsuario).getField()).isEqualTo(detalle(claveMala).getField());
    }

    @Test
    void devuelve400ConLosDosCamposCuandoElPayloadVieneVacio() {
        BadRequestException error = capturarError(login().login(new LoginCommand(null, "   ")));

        assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(error.getErrorResponse().getErrors())
                .extracting(detalle -> detalle.getField())
                .containsExactly("email", "password");

        verifyNoInteractions(usuarios, hasher, emisor);
    }

    /** Un payload invalido tiene que salir como senal de error del Mono, no al ensamblar la cadena. */
    @Test
    void noLanzaAlConstruirLaCadenaConUnPayloadInvalido() {
        Mono<AccessToken> cadena = login().login(new LoginCommand(null, null));

        verify(usuarios, never()).findActiveByEmail(anyString());
        StepVerifier.create(cadena).verifyError(BadRequestException.class);
    }

    /** El hash guardado es lo unico que se le pasa al verificador: nunca se compara con equals. */
    @Test
    void verificaLaClaveContraElHashGuardado() {
        loginPosible();

        StepVerifier.create(login().login(new LoginCommand(UserMother.EMAIL, UserMother.PASSWORD)))
                .expectNextCount(1)
                .verifyComplete();

        verify(hasher).matches(UserMother.PASSWORD, UserMother.HASH);
    }

    private LoginUseCase login() {
        return new LoginUseCase(usuarios, hasher, emisor);
    }

    private void loginPosible() {
        when(usuarios.findActiveByEmail(anyString())).thenReturn(Mono.just(credenciales()));
        when(hasher.matches(anyString(), anyString())).thenReturn(true);
        when(emisor.issueFor(any())).thenReturn(TOKEN);
    }

    private static UserCredentials credenciales() {
        return new UserCredentials(ID, UserMother.HASH);
    }

    private static boolean unaCredencialInvalida(Throwable error) {
        return error instanceof BadRequestException bre
                && bre.getHttpStatus() == HttpStatus.UNAUTHORIZED
                && ErrorCodes.INVALID_CREDENTIALS.getCode().equals(detalle(bre).getCode());
    }

    private static ErrorDetail detalle(BadRequestException error) {
        return error.getErrorResponse().getErrors().get(0);
    }

    private static BadRequestException capturarError(Mono<AccessToken> cadena) {
        try {
            cadena.block();
        } catch (BadRequestException esperado) {
            return esperado;
        }
        throw new AssertionError("se esperaba un BadRequestException");
    }
}
