package com.oscargabriel.financeapp.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.AccessToken;
import com.oscargabriel.financeapp.core.domain.model.LoginCommand;
import com.oscargabriel.financeapp.core.domain.model.NewRefreshToken;
import com.oscargabriel.financeapp.core.domain.model.RefreshToken;
import com.oscargabriel.financeapp.core.domain.model.SessionTokens;
import com.oscargabriel.financeapp.core.domain.model.UserCredentials;
import com.oscargabriel.financeapp.core.domain.port.out.PasswordHasherPort;
import com.oscargabriel.financeapp.core.domain.port.out.RefreshTokenGeneratorPort;
import com.oscargabriel.financeapp.core.domain.port.out.SesionRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.TokenIssuerPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;
import com.oscargabriel.financeapp.core.support.UserMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginUseCaseTest {

    private static final UUID ID = UUID.fromString("01994f00-0000-7000-8000-000000000001");

    private static final long VERSION = 2L;

    private static final AccessToken TOKEN = new AccessToken("un.jwt.firmado", Duration.ofHours(1));

    private static final Instant AHORA = Instant.parse("2026-10-12T15:00:00Z");

    private static final Duration DURACION_DE_LA_SESION = Duration.ofHours(24);

    private static final String REFRESH = "refresh-en-claro";

    private static final String HASH_DEL_REFRESH = "hash-del-refresh";

    private static final SessionTokens SESION =
            new SessionTokens(TOKEN, new RefreshToken(REFRESH, DURACION_DE_LA_SESION));

    @Mock
    private UserRepositoryPort usuarios;

    @Mock
    private PasswordHasherPort hasher;

    @Mock
    private TokenIssuerPort emisor;

    @Mock
    private RefreshTokenGeneratorPort generador;

    @Mock
    private SesionRepositoryPort sesiones;

    /** Lo que guarda la sesion lo prueba AperturaDeSesionTest; aqui, que el login la abre. */
    @Test
    void abreUnaSesionDelUsuario() {
        loginPosible();

        StepVerifier.create(login().login(new LoginCommand(UserMother.EMAIL, UserMother.PASSWORD)))
                .expectNext(SESION)
                .verifyComplete();

        ArgumentCaptor<NewRefreshToken> abierta = ArgumentCaptor.forClass(NewRefreshToken.class);
        verify(sesiones).abrir(abierta.capture());
        assertThat(abierta.getValue().userId()).isEqualTo(ID);
        assertThat(abierta.getValue().tokenVersion()).isEqualTo(VERSION);
    }

    /**
     * FIN-52: un usuario registrado con Google no tiene hash. Sale el mismo 401 que una clave
     * incorrecta, sin pasarle un nulo al verificador.
     */
    @Test
    void devuelve401AUnUsuarioSinContrasena() {
        when(usuarios.findActiveByEmail(anyString()))
                .thenReturn(Mono.just(new UserCredentials(ID, null, VERSION, "sub-de-google")));

        StepVerifier.create(login().login(new LoginCommand(UserMother.EMAIL, UserMother.PASSWORD)))
                .verifyErrorSatisfies(error -> assertThat(unaCredencialInvalida(error)).isTrue());

        verifyNoInteractions(hasher, emisor, generador, sesiones);
    }

    @Test
    void emiteElTokenDelUsuarioCuandoLaClaveCoincide() {
        loginPosible();

        StepVerifier.create(login().login(new LoginCommand(UserMother.EMAIL, UserMother.PASSWORD)))
                .expectNext(SESION)
                .verifyComplete();

        verify(emisor).issueFor(ID, VERSION);
    }

    /** El indice unico de la tabla es sobre lower(email), y el alta guarda el correo normalizado. */
    @Test
    void normalizaElCorreoAntesDeBuscarlo() {
        loginPosible();

        StepVerifier.create(login().login(new LoginCommand("  ANA@Ejemplo.COM  ", UserMother.PASSWORD)))
                .expectNext(SESION)
                .verifyComplete();

        verify(usuarios).findActiveByEmail(UserMother.EMAIL);
    }

    @Test
    void devuelve401CuandoLaClaveNoCoincide() {
        when(usuarios.findActiveByEmail(anyString())).thenReturn(Mono.just(credenciales()));
        when(hasher.matches(anyString(), anyString())).thenReturn(false);

        StepVerifier.create(login().login(new LoginCommand(UserMother.EMAIL, "otraClave")))
                .verifyErrorSatisfies(error -> assertThat(unaCredencialInvalida(error)).isTrue());

        verifyNoInteractions(emisor, generador, sesiones);
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

        verifyNoInteractions(emisor, generador, sesiones);
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
        return new LoginUseCase(usuarios, hasher, new AperturaDeSesion(emisor, generador, sesiones,
                Clock.fixed(AHORA, ZoneOffset.UTC), DURACION_DE_LA_SESION));
    }

    private void loginPosible() {
        when(usuarios.findActiveByEmail(anyString())).thenReturn(Mono.just(credenciales()));
        when(hasher.matches(anyString(), anyString())).thenReturn(true);
        when(emisor.issueFor(any(), anyLong())).thenReturn(TOKEN);
        when(generador.generar()).thenReturn(REFRESH);
        when(generador.hash(REFRESH)).thenReturn(HASH_DEL_REFRESH);
        when(sesiones.abrir(any())).thenReturn(Mono.empty());
    }

    private static UserCredentials credenciales() {
        return new UserCredentials(ID, UserMother.HASH, VERSION, null);
    }

    private static boolean unaCredencialInvalida(Throwable error) {
        return error instanceof BadRequestException bre
                && bre.getHttpStatus() == HttpStatus.UNAUTHORIZED
                && ErrorCodes.INVALID_CREDENTIALS.getCode().equals(detalle(bre).getCode());
    }

    private static ErrorDetail detalle(BadRequestException error) {
        return error.getErrorResponse().getErrors().get(0);
    }

    private static BadRequestException capturarError(Mono<SessionTokens> cadena) {
        try {
            cadena.block();
        } catch (BadRequestException esperado) {
            return esperado;
        }
        throw new AssertionError("se esperaba un BadRequestException");
    }
}
