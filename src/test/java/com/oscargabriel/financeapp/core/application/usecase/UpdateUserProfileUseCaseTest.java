package com.oscargabriel.financeapp.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
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
import com.oscargabriel.financeapp.compartido.support.RelojFijo;
import com.oscargabriel.financeapp.core.domain.model.ExchangeRate;
import com.oscargabriel.financeapp.core.domain.model.UpdateUserProfileCommand;
import com.oscargabriel.financeapp.core.domain.model.UserProfile;
import com.oscargabriel.financeapp.core.domain.port.in.ResolveExchangeRatePort;
import com.oscargabriel.financeapp.core.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.PasswordHasherPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;
import com.oscargabriel.financeapp.core.support.UserMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UpdateUserProfileUseCaseTest {

    private static final String CORREO_NUEVO = "nuevo@correo.com";

    private static final Clock RELOJ = Clock.fixed(RelojFijo.AHORA, ZoneOffset.UTC);

    private static final LocalDate HOY = LocalDate.now(RELOJ);

    @Mock
    private UserRepositoryPort usuarios;

    @Mock
    private PasswordHasherPort hasher;

    @Mock
    private CurrencyQueryPort monedas;

    @Mock
    private ResolveExchangeRatePort tasas;

    @Captor
    private ArgumentCaptor<UserProfile> perfilGuardado;

    @Test
    void aplicaLosCamposRecortadosYConservaElResto() {
        actualizacionPosible();

        StepVerifier.create(useCase().update(UserMother.ID,
                        parche(" Ana Maria ", null, null, " +573109876543 ", " Europe/Madrid ", null)))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(guardado()).isEqualTo(new UserProfile(UserMother.ID, UserMother.EMAIL, "Ana Maria", null,
                "+573109876543", "COP", "Europe/Madrid", true, false));
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

    /** FIN-53: sin contrasena no hay contra que comparar; el usuario la define primero. */
    @Test
    void cambiarElCorreoSinTenerContrasenaEsUn409() {
        actualizacionDeGoogle();

        StepVerifier.create(useCase().update(UserMother.ID, parche(null, null, CORREO_NUEVO, null, null, "cualquierClave1")))
                .verifyErrorSatisfies(error -> esperaError(error, 409, ErrorCodes.INVALID_STATE, "currentPassword"));

        verifyNoInteractions(hasher);
        verify(usuarios, never()).existsByEmailForOtherUser(anyString(), any());
        verify(usuarios, never()).updateProfile(any());
    }

    /** El estado va antes que la presencia de la clave: no se le pide una que no tiene (decision 5). */
    @Test
    void cambiarElCorreoSinTenerContrasenaNiMandarlaEsUn409() {
        actualizacionDeGoogle();

        StepVerifier.create(useCase().update(UserMother.ID, parche(null, null, CORREO_NUEVO, null, null, null)))
                .verifyErrorSatisfies(error -> esperaError(error, 409, ErrorCodes.INVALID_STATE, "currentPassword"));

        verify(usuarios, never()).updateProfile(any());
    }

    @Test
    void sinContrasenaSeModificaLoQueNoEsElCorreo() {
        actualizacionDeGoogle();

        StepVerifier.create(useCase().update(UserMother.ID, parche("Ana Maria", null, UserMother.EMAIL, null, null, null)))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(guardado().firstName()).isEqualTo("Ana Maria");
        verify(usuarios, never()).findActiveCredentials(any());
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

    @Test
    void cambiaLaMonedaNormalizadaTrasAsegurarSuTasaDeHoy() {
        actualizacionPosible();
        monedaActiva("EUR");
        conTasa("EUR");

        StepVerifier.create(useCase().update(UserMother.ID, moneda(" eur ", null, null)))
                .assertNext(perfil -> assertThat(perfil.baseCurrencyCode()).isEqualTo("EUR"))
                .verifyComplete();

        assertThat(guardado().baseCurrencyCode()).isEqualTo("EUR");
        verify(tasas).resolve("USD", "EUR", HOY);
    }

    /** USD→USD vale 1: resolve lo responde sin buscar tasas (tasas-de-cambio, «Mismo par»). */
    @Test
    void cambiarADolaresTambienPasaPorLaResolucion() {
        actualizacionPosible();
        monedaActiva("USD");
        when(tasas.resolve("USD", "USD", HOY)).thenReturn(Mono.just(ExchangeRate.mismoPar("USD", HOY)));

        StepVerifier.create(useCase().update(UserMother.ID, moneda("usd", null, null)))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(guardado().baseCurrencyCode()).isEqualTo("USD");
    }

    @Test
    void laMismaMonedaEnOtraCajaNoConsultaElCatalogoNiLasTasas() {
        actualizacionPosible();

        StepVerifier.create(useCase().update(UserMother.ID, moneda(" cop ", null, null)))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(guardado().baseCurrencyCode()).isEqualTo("COP");
        verifyNoInteractions(monedas, tasas);
    }

    @Test
    void unaMonedaInactivaOInexistenteEsUn400SinBuscarTasa() {
        actualizacionPosible();
        when(monedas.exists("XTS")).thenReturn(Mono.just(false));

        StepVerifier.create(useCase().update(UserMother.ID,
                        new UpdateUserProfileCommand("Otro", null, null, null, null, "XTS", null)))
                .verifyErrorSatisfies(error -> esperaError(error, 400, ErrorCodes.VALIDATION_ERROR, "baseCurrencyCode"));

        verifyNoInteractions(tasas);
        verify(usuarios, never()).updateProfile(any());
    }

    @Test
    void sinNingunaTasaDeLaMonedaEsUn502SinGuardarNada() {
        actualizacionPosible();
        monedaActiva("MXN");
        when(tasas.resolve("USD", "MXN", HOY)).thenReturn(Mono.error(new BadRequestException(HttpStatus.NOT_FOUND,
                ErrorCodes.NOT_FOUND, "No hay ninguna tasa de cambio de USD a MXN", "date")));

        StepVerifier.create(useCase().update(UserMother.ID,
                        new UpdateUserProfileCommand("Otro", null, null, null, null, "MXN", null)))
                .verifyErrorSatisfies(error -> {
                    esperaError(error, 502, ErrorCodes.EXTERNAL_SERVICE_ERROR, "server");
                    assertThat(error.getMessage()).doesNotContainIgnoringCase("proveedor");
                });

        verify(usuarios, never()).updateProfile(any());
    }

    /** La moneda se valida antes que la clave: los dos son 400, y el del catalogo no consulta nada sensible. */
    @Test
    void laMonedaInactivaSaleAntesQueLaClaveEquivocada() {
        actualizacionPosible();
        when(monedas.exists("XTS")).thenReturn(Mono.just(false));

        StepVerifier.create(useCase().update(UserMother.ID, moneda("XTS", CORREO_NUEVO, "noEsLaClave")))
                .verifyErrorSatisfies(error -> esperaError(error, 400, ErrorCodes.VALIDATION_ERROR, "baseCurrencyCode"));

        verifyNoInteractions(hasher);
        verify(usuarios, never()).findActiveCredentials(any());
    }

    /** La tasa va al final: puede salir al proveedor, y un parche rechazado no debe gastar esa consulta. */
    @Test
    void conLaClaveEquivocadaNoSeBuscaLaTasa() {
        actualizacionPosible();
        monedaActiva("MXN");
        when(hasher.matches("noEsLaClave", UserMother.HASH)).thenReturn(false);

        StepVerifier.create(useCase().update(UserMother.ID, moneda("MXN", CORREO_NUEVO, "noEsLaClave")))
                .verifyErrorSatisfies(error -> esperaError(error, 400, ErrorCodes.INVALID_CREDENTIALS, "currentPassword"));

        verifyNoInteractions(tasas);
        verify(usuarios, never()).updateProfile(any());
    }

    private void monedaActiva(String codigo) {
        when(monedas.exists(codigo)).thenReturn(Mono.just(true));
    }

    private void conTasa(String codigo) {
        when(tasas.resolve("USD", codigo, HOY)).thenReturn(Mono.just(
                new ExchangeRate("USD", codigo, HOY, new BigDecimal("0.8"), HOY)));
    }

    private void actualizacionPosible() {
        when(usuarios.findActiveProfile(UserMother.ID)).thenReturn(Mono.just(UserMother.unPerfil()));
        when(usuarios.findActiveCredentials(UserMother.ID)).thenReturn(Mono.just(UserMother.credenciales()));
        when(usuarios.existsByEmailForOtherUser(anyString(), any())).thenReturn(Mono.just(false));
        when(usuarios.updateProfile(any())).thenAnswer(invocacion -> Mono.just(invocacion.getArgument(0)));
    }

    /** Un usuario registrado con Google, sin contrasena (FIN-53). */
    private void actualizacionDeGoogle() {
        actualizacionPosible();
        when(usuarios.findActiveProfile(UserMother.ID)).thenReturn(Mono.just(UserMother.unPerfilDeGoogle()));
        when(usuarios.findActiveCredentials(UserMother.ID)).thenReturn(Mono.just(UserMother.credencialesDeGoogle()));
    }

    private void claveCorrecta() {
        when(hasher.matches(UserMother.PASSWORD, UserMother.HASH)).thenReturn(true);
    }

    private UserProfile guardado() {
        verify(usuarios).updateProfile(perfilGuardado.capture());
        return perfilGuardado.getValue();
    }

    private UpdateUserProfileUseCase useCase() {
        return new UpdateUserProfileUseCase(usuarios, hasher, monedas, tasas, RELOJ);
    }

    private static UpdateUserProfileCommand parche(String firstName, String lastName, String email, String phone,
            String timezone, String currentPassword) {
        return new UpdateUserProfileCommand(firstName, lastName, email, phone, timezone, null, currentPassword);
    }

    private static UpdateUserProfileCommand moneda(String baseCurrencyCode, String email, String currentPassword) {
        return new UpdateUserProfileCommand(null, null, email, null, null, baseCurrencyCode, currentPassword);
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
