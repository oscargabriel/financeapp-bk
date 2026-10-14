package com.oscargabriel.financeapp.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.core.domain.model.GoogleRegistrationCommand;
import com.oscargabriel.financeapp.core.domain.model.User;
import com.oscargabriel.financeapp.core.domain.port.out.GoogleIdTokenVerifierPort;
import com.oscargabriel.financeapp.core.domain.port.out.RegistrationAllowlistPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;
import com.oscargabriel.financeapp.core.support.GoogleMother;
import com.oscargabriel.financeapp.core.support.UserMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoogleRegisterUseCaseTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-14T15:00:00Z"), ZoneOffset.UTC);

    @Mock
    private GoogleIdTokenVerifierPort verificador;

    @Mock
    private UserRepositoryPort usuarios;

    @Mock
    private RegistrationAllowlistPort admitidos;

    @Mock
    private AperturaDeSesion aperturaDeSesion;

    @Captor
    private ArgumentCaptor<User> usuarioGuardado;

    @Test
    void creaUnUsuarioSinContrasenaConLosDatosDeGoogleYAbreSuSesion() {
        registroPosible();

        StepVerifier.create(useCase().register(new GoogleRegistrationCommand(GoogleMother.ID_TOKEN, "Europe/Madrid")))
                .expectNext(GoogleMother.SESION)
                .verifyComplete();

        verify(usuarios).createWithDefaultCategories(usuarioGuardado.capture());
        User usuario = usuarioGuardado.getValue();
        assertThat(usuario.passwordHash()).isNull();
        assertThat(usuario.googleSub()).isEqualTo(GoogleMother.SUB);
        assertThat(usuario.email()).isEqualTo(UserMother.EMAIL);
        assertThat(usuario.firstName()).isEqualTo("Ana");
        assertThat(usuario.lastName()).isEqualTo("Gomez");
        assertThat(usuario.baseCurrencyCode()).isEqualTo("COP");
        assertThat(usuario.timezone()).isEqualTo("Europe/Madrid");
        assertThat(usuario.phone()).isNull();
        assertThat(usuario.id().version()).isEqualTo(7);
        verify(aperturaDeSesion).abrir(usuario.id(), 0L);
    }

    @Test
    void sinZonaUsaLaDelAltaNormal() {
        registroPosible();

        StepVerifier.create(useCase().register(new GoogleRegistrationCommand(GoogleMother.ID_TOKEN, null)))
                .expectNextCount(1)
                .verifyComplete();

        verify(usuarios).createWithDefaultCategories(usuarioGuardado.capture());
        assertThat(usuarioGuardado.getValue().timezone()).isEqualTo("America/Bogota");
    }

    @Test
    void rechazaUnTokenQueNoSirveSinTocarNada() {
        when(verificador.verificar(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(useCase().register(new GoogleRegistrationCommand("no-sirve", null)))
                .verifyErrorSatisfies(error -> esError(error, HttpStatus.UNAUTHORIZED,
                        ErrorCodes.INVALID_CREDENTIALS, "idToken"));

        verifyNoInteractions(usuarios, admitidos, aperturaDeSesion);
    }

    /** Google no respondio: eso no dice nada del token, asi que no sale como un 401. */
    @Test
    void propagaElErrorDelVerificadorSinTocarNada() {
        BadRequestException caido = new BadRequestException(HttpStatus.BAD_GATEWAY,
                ErrorCodes.EXTERNAL_SERVICE_ERROR, "El servicio de Google no esta disponible", "idToken");
        when(verificador.verificar(anyString())).thenReturn(Mono.error(caido));

        StepVerifier.create(useCase().register(new GoogleRegistrationCommand(GoogleMother.ID_TOKEN, null)))
                .verifyErrorMatches(caido::equals);

        verifyNoInteractions(usuarios, admitidos, aperturaDeSesion);
    }

    /** Como el alta normal: a un correo no admitido no se le dice si ya tiene cuenta. */
    @Test
    void rechazaUnCorreoNoAdmitidoSinDecirSiTieneCuenta() {
        registroPosible();
        when(admitidos.isAllowed(anyString())).thenReturn(Mono.just(false));

        StepVerifier.create(useCase().register(new GoogleRegistrationCommand(GoogleMother.ID_TOKEN, null)))
                .verifyErrorSatisfies(error -> esError(error, HttpStatus.FORBIDDEN,
                        ErrorCodes.REGISTRATION_NOT_ALLOWED, "email"));

        verify(usuarios, never()).existsByEmail(anyString());
        verify(usuarios, never()).createWithDefaultCategories(any());
    }

    @Test
    void rechazaUnCorreoYaRegistrado() {
        registroPosible();
        when(usuarios.existsByEmail(UserMother.EMAIL)).thenReturn(Mono.just(true));

        StepVerifier.create(useCase().register(new GoogleRegistrationCommand(GoogleMother.ID_TOKEN, null)))
                .verifyErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT,
                        ErrorCodes.DUPLICATE_RESOURCE, "email"));

        verify(usuarios, never()).createWithDefaultCategories(any());
    }

    /** El usuario cambio su correo despues de registrarse con este Google: sigue teniendo cuenta. */
    @Test
    void rechazaUnaCuentaGoogleYaRegistrada() {
        registroPosible();
        when(usuarios.existsByGoogleSub(GoogleMother.SUB)).thenReturn(Mono.just(true));

        StepVerifier.create(useCase().register(new GoogleRegistrationCommand(GoogleMother.ID_TOKEN, null)))
                .verifyErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT,
                        ErrorCodes.DUPLICATE_RESOURCE, "email"));

        verify(usuarios, never()).createWithDefaultCategories(any());
    }

    @Test
    void conLaRestriccionApagadaNoConsultaLaLista() {
        registroPosible();

        StepVerifier.create(useCaseSinRestriccion()
                        .register(new GoogleRegistrationCommand(GoogleMother.ID_TOKEN, null)))
                .expectNextCount(1)
                .verifyComplete();

        verifyNoInteractions(admitidos);
    }

    private void registroPosible() {
        when(verificador.verificar(GoogleMother.ID_TOKEN)).thenReturn(Mono.just(GoogleMother.unaIdentidad()));
        when(admitidos.isAllowed(anyString())).thenReturn(Mono.just(true));
        when(usuarios.existsByEmail(anyString())).thenReturn(Mono.just(false));
        when(usuarios.existsByGoogleSub(anyString())).thenReturn(Mono.just(false));
        when(usuarios.createWithDefaultCategories(any())).thenReturn(Mono.just(22L));
        when(aperturaDeSesion.abrir(any(), anyLong())).thenReturn(Mono.just(GoogleMother.SESION));
    }

    private GoogleRegisterUseCase useCase() {
        return new GoogleRegisterUseCase(verificador, usuarios, new ReglasDelAlta(usuarios, admitidos, true),
                aperturaDeSesion, RELOJ);
    }

    private GoogleRegisterUseCase useCaseSinRestriccion() {
        return new GoogleRegisterUseCase(verificador, usuarios, new ReglasDelAlta(usuarios, admitidos, false),
                aperturaDeSesion, RELOJ);
    }

    private static void esError(Throwable error, HttpStatus status, ErrorCodes codigo, String campo) {
        assertThat(error).isInstanceOf(BadRequestException.class);
        BadRequestException bre = (BadRequestException) error;
        assertThat(bre.getHttpStatus()).isEqualTo(status);
        assertThat(bre.getErrorResponse().getErrors().get(0).getCode()).isEqualTo(codigo.getCode());
        assertThat(bre.getErrorResponse().getErrors().get(0).getField()).isEqualTo(campo);
    }
}
