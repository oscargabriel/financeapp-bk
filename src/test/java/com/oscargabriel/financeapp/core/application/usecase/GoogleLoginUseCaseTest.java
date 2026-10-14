package com.oscargabriel.financeapp.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.core.domain.model.UserCredentials;
import com.oscargabriel.financeapp.core.domain.port.out.GoogleIdTokenVerifierPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;
import com.oscargabriel.financeapp.core.support.GoogleMother;
import com.oscargabriel.financeapp.core.support.UserMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoogleLoginUseCaseTest {

    private static final UUID ID = UUID.fromString("01994f00-0000-7000-8000-000000000001");
    private static final long VERSION = 3L;

    @Mock
    private GoogleIdTokenVerifierPort verificador;

    @Mock
    private UserRepositoryPort usuarios;

    @Mock
    private AperturaDeSesion aperturaDeSesion;

    @BeforeEach
    void tokenValido() {
        when(verificador.verificar(GoogleMother.ID_TOKEN)).thenReturn(Mono.just(GoogleMother.unaIdentidad()));
        when(usuarios.findActiveByGoogleSub(anyString())).thenReturn(Mono.empty());
        when(usuarios.findActiveByEmail(anyString())).thenReturn(Mono.empty());
        when(aperturaDeSesion.abrir(any(), anyLong())).thenReturn(Mono.just(GoogleMother.SESION));
    }

    /** Busca por el sub, no por el correo: el correo de la cuenta pudo cambiar. */
    @Test
    void entraPorLaCuentaGoogleVinculada() {
        when(usuarios.findActiveByGoogleSub(GoogleMother.SUB))
                .thenReturn(Mono.just(new UserCredentials(ID, null, VERSION, GoogleMother.SUB)));

        StepVerifier.create(useCase().login(GoogleMother.ID_TOKEN))
                .expectNext(GoogleMother.SESION)
                .verifyComplete();

        verify(aperturaDeSesion).abrir(ID, VERSION);
        verify(usuarios, never()).findActiveByEmail(anyString());
    }

    @Test
    void vinculaLaCuentaDelMismoCorreoQueNoTieneGoogle() {
        when(usuarios.findActiveByEmail(UserMother.EMAIL))
                .thenReturn(Mono.just(new UserCredentials(ID, UserMother.HASH, VERSION, null)));
        when(usuarios.linkGoogle(ID, GoogleMother.SUB)).thenReturn(Mono.just(true));

        StepVerifier.create(useCase().login(GoogleMother.ID_TOKEN))
                .expectNext(GoogleMother.SESION)
                .verifyComplete();

        verify(usuarios).linkGoogle(ID, GoogleMother.SUB);
        verify(aperturaDeSesion).abrir(ID, VERSION);
    }

    /** Otra peticion vinculo entre la lectura y el UPDATE: no se abre sesion sobre una vinculacion ajena. */
    @Test
    void rechazaSiLaVinculacionNoSeHizo() {
        when(usuarios.findActiveByEmail(UserMother.EMAIL))
                .thenReturn(Mono.just(new UserCredentials(ID, UserMother.HASH, VERSION, null)));
        when(usuarios.linkGoogle(ID, GoogleMother.SUB)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase().login(GoogleMother.ID_TOKEN))
                .verifyErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT,
                        ErrorCodes.DUPLICATE_RESOURCE, "email"));

        verifyNoInteractions(aperturaDeSesion);
    }

    @Test
    void rechazaUnCorreoVinculadoAOtraCuentaGoogle() {
        when(usuarios.findActiveByEmail(UserMother.EMAIL))
                .thenReturn(Mono.just(new UserCredentials(ID, UserMother.HASH, VERSION, "otro-sub")));

        StepVerifier.create(useCase().login(GoogleMother.ID_TOKEN))
                .verifyErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT,
                        ErrorCodes.DUPLICATE_RESOURCE, "email"));

        verify(usuarios, never()).linkGoogle(any(), anyString());
        verifyNoInteractions(aperturaDeSesion);
    }

    /** Sin cuenta, o con una desactivada o borrada: el puerto no emite nada en los tres casos. */
    @Test
    void responde404SiNoHayCuenta() {
        StepVerifier.create(useCase().login(GoogleMother.ID_TOKEN))
                .verifyErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND,
                        ErrorCodes.ACCOUNT_NOT_REGISTERED, "idToken"));

        verifyNoInteractions(aperturaDeSesion);
    }

    @Test
    void rechazaUnTokenQueNoSirve() {
        when(verificador.verificar("no-sirve")).thenReturn(Mono.empty());

        StepVerifier.create(useCase().login("no-sirve"))
                .verifyErrorSatisfies(error -> esError(error, HttpStatus.UNAUTHORIZED,
                        ErrorCodes.INVALID_CREDENTIALS, "idToken"));

        verifyNoInteractions(usuarios, aperturaDeSesion);
    }

    @Test
    void propagaElErrorDelVerificador() {
        BadRequestException caido = new BadRequestException(HttpStatus.BAD_GATEWAY,
                ErrorCodes.EXTERNAL_SERVICE_ERROR, "El servicio de Google no esta disponible", "idToken");
        when(verificador.verificar(GoogleMother.ID_TOKEN)).thenReturn(Mono.error(caido));

        StepVerifier.create(useCase().login(GoogleMother.ID_TOKEN))
                .verifyErrorMatches(caido::equals);

        verifyNoInteractions(usuarios, aperturaDeSesion);
    }

    private GoogleLoginUseCase useCase() {
        return new GoogleLoginUseCase(verificador, usuarios, aperturaDeSesion);
    }

    private static void esError(Throwable error, HttpStatus status, ErrorCodes codigo, String campo) {
        assertThat(error).isInstanceOf(BadRequestException.class);
        BadRequestException bre = (BadRequestException) error;
        assertThat(bre.getHttpStatus()).isEqualTo(status);
        assertThat(bre.getErrorResponse().getErrors().get(0).getCode()).isEqualTo(codigo.getCode());
        assertThat(bre.getErrorResponse().getErrors().get(0).getField()).isEqualTo(campo);
    }
}
