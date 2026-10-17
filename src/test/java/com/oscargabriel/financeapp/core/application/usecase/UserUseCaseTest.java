package com.oscargabriel.financeapp.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.support.RelojFijo;
import com.oscargabriel.financeapp.core.domain.model.ChangePasswordCommand;
import com.oscargabriel.financeapp.core.domain.model.ExchangeRate;
import com.oscargabriel.financeapp.core.domain.model.FirstPasswordCommand;
import com.oscargabriel.financeapp.core.domain.model.GoogleIdentity;
import com.oscargabriel.financeapp.core.domain.model.UpdateUserProfileCommand;
import com.oscargabriel.financeapp.core.domain.model.UserProfile;
import com.oscargabriel.financeapp.core.domain.port.in.ResolveExchangeRatePort;
import com.oscargabriel.financeapp.core.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.GoogleIdTokenVerifierPort;
import com.oscargabriel.financeapp.core.domain.port.out.PasswordHasherPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;
import com.oscargabriel.financeapp.core.support.GoogleMother;
import com.oscargabriel.financeapp.core.support.UserMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class UserUseCaseTest {

    private static final Clock RELOJ = Clock.fixed(RelojFijo.AHORA, ZoneOffset.UTC);

    @Mock
    private UserRepositoryPort usuarios;

    @Mock
    private PasswordHasherPort hasher;

    @Mock
    private GoogleIdTokenVerifierPort google;

    @Mock
    private CurrencyQueryPort monedas;

    @Mock
    private ResolveExchangeRatePort tasas;

    @Captor
    private ArgumentCaptor<UserProfile> perfilGuardado;

    @Nested
    class Leer {

        @Test
        void devuelveElPerfilDelUsuarioDelToken() {
            when(usuarios.findActiveProfile(UserMother.ID)).thenReturn(Mono.just(UserMother.unPerfil()));

            StepVerifier.create(useCase().get(UserMother.ID))
                    .expectNext(UserMother.unPerfil())
                    .verifyComplete();
        }

        /** El token sigue vigente aunque el usuario se desactive: la identidad ya no vale, no falta un recurso. */
        @Test
        void unUsuarioInactivoOBorradoEsUn401() {
            when(usuarios.findActiveProfile(UserMother.ID)).thenReturn(Mono.empty());

            StepVerifier.create(useCase().get(UserMother.ID))
                    .verifyErrorSatisfies(error -> esperaError(error, 401, ErrorCodes.UNAUTHENTICATED, "authorization"));
        }
    }

    @Nested
    class Actualizar {

        private static final String CORREO_NUEVO = "nuevo@correo.com";

        private static final LocalDate HOY = LocalDate.now(RELOJ);

        @Test
        void aplicaLosCamposRecortadosYConservaElResto() {
            actualizacionPosible();

            StepVerifier.create(useCase().update(UserMother.ID,
                            parche(" Ana Maria ", null, null, " +573109876543 ", " Europe/Madrid ", null)))
                    .expectNextCount(1)
                    .verifyComplete();

            assertThat(guardado()).isEqualTo(new UserProfile(UserMother.ID, UserMother.EMAIL, "Ana Maria", null,
                    "+573109876543", "COP", "Europe/Madrid", true, false, false));
            verifyNoInteractions(hasher);
        }

        /** Lo decide el UPDATE: el parche no lo cambia, y si el phone cambia la base desvincula el chat (FIN-60). */
        @Test
        void conservaTelegramLinkedDelPerfilActualYEmiteElDeLaBase() {
            when(usuarios.findActiveProfile(UserMother.ID)).thenReturn(Mono.just(UserMother.unPerfilConTelegram()));
            when(usuarios.updateProfile(any())).thenReturn(Mono.just(UserMother.unPerfil()));

            StepVerifier.create(useCase().update(UserMother.ID, parche(null, null, null, "+573109876543", null, null)))
                    .assertNext(perfil -> assertThat(perfil.telegramLinked()).isFalse())
                    .verifyComplete();

            assertThat(guardado().telegramLinked()).isTrue();
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
            lenient().when(monedas.exists(codigo)).thenReturn(Mono.just(true));
        }

        private void conTasa(String codigo) {
            lenient().when(tasas.resolve("USD", codigo, HOY)).thenReturn(Mono.just(
                    new ExchangeRate("USD", codigo, HOY, new BigDecimal("0.8"), HOY)));
        }

        private void actualizacionPosible() {
            lenient().when(usuarios.findActiveProfile(UserMother.ID)).thenReturn(Mono.just(UserMother.unPerfil()));
            lenient().when(usuarios.findActiveCredentials(UserMother.ID)).thenReturn(Mono.just(UserMother.credenciales()));
            lenient().when(usuarios.existsByEmailForOtherUser(anyString(), any())).thenReturn(Mono.just(false));
            lenient().when(usuarios.updateProfile(any())).thenAnswer(invocacion -> Mono.just(invocacion.getArgument(0)));
        }

        /** Un usuario registrado con Google, sin contrasena (FIN-53). */
        private void actualizacionDeGoogle() {
            actualizacionPosible();
            lenient().when(usuarios.findActiveProfile(UserMother.ID)).thenReturn(Mono.just(UserMother.unPerfilDeGoogle()));
            lenient().when(usuarios.findActiveCredentials(UserMother.ID))
                    .thenReturn(Mono.just(UserMother.credencialesDeGoogle()));
        }

        private void claveCorrecta() {
            lenient().when(hasher.matches(UserMother.PASSWORD, UserMother.HASH)).thenReturn(true);
        }

        private UserProfile guardado() {
            verify(usuarios).updateProfile(perfilGuardado.capture());
            return perfilGuardado.getValue();
        }

        private static UpdateUserProfileCommand parche(String firstName, String lastName, String email, String phone,
                String timezone, String currentPassword) {
            return new UpdateUserProfileCommand(firstName, lastName, email, phone, timezone, null, currentPassword);
        }

        private static UpdateUserProfileCommand moneda(String baseCurrencyCode, String email, String currentPassword) {
            return new UpdateUserProfileCommand(null, null, email, null, null, baseCurrencyCode, currentPassword);
        }
    }

    @Nested
    class CambiarContrasena {

        private static final String NUEVA = "otraClaveLarga";

        private static final String HASH_NUEVO = "$2a$10$otroHashQueNoEsReal";

        /** La nueva se guarda como el hash que produjo el hasher, nunca en claro. */
        @Test
        void guardaElHashDeLaNuevaSiLaActualCoincide() {
            when(usuarios.findActiveCredentials(UserMother.ID)).thenReturn(Mono.just(UserMother.credenciales()));
            when(hasher.matches(UserMother.PASSWORD, UserMother.HASH)).thenReturn(true);
            when(hasher.hash(NUEVA)).thenReturn(HASH_NUEVO);
            when(usuarios.updatePassword(UserMother.ID, HASH_NUEVO)).thenReturn(Mono.empty());

            StepVerifier.create(useCase().changePassword(UserMother.ID,
                            new ChangePasswordCommand(UserMother.PASSWORD, NUEVA)))
                    .verifyComplete();

            verify(usuarios).updatePassword(UserMother.ID, HASH_NUEVO);
            verify(usuarios, never()).updatePassword(UserMother.ID, NUEVA);
        }

        @Test
        void conLaActualEquivocadaNoCambiaNada() {
            when(usuarios.findActiveCredentials(UserMother.ID)).thenReturn(Mono.just(UserMother.credenciales()));
            when(hasher.matches("noEsLaClave", UserMother.HASH)).thenReturn(false);

            StepVerifier.create(useCase().changePassword(UserMother.ID, new ChangePasswordCommand("noEsLaClave", NUEVA)))
                    .verifyErrorSatisfies(error -> esperaError(error, 400, ErrorCodes.INVALID_CREDENTIALS, "currentPassword"));

            verify(hasher, never()).hash(anyString());
            verify(usuarios, never()).updatePassword(any(), anyString());
        }

        /** FIN-53: un error propio, distinto de la clave equivocada, y sin cerrar sesiones. */
        @Test
        void sinContrasenaEsUn409YNoCambiaNada() {
            when(usuarios.findActiveCredentials(UserMother.ID)).thenReturn(Mono.just(UserMother.credencialesDeGoogle()));

            StepVerifier.create(useCase().changePassword(UserMother.ID,
                            new ChangePasswordCommand("cualquierClave1", NUEVA)))
                    .verifyErrorSatisfies(error -> esperaError(error, 409, ErrorCodes.INVALID_STATE, "currentPassword"));

            verify(hasher, never()).matches(anyString(), anyString());
            verify(usuarios, never()).updatePassword(any(), anyString());
        }

        @Test
        void unUsuarioInactivoEsUn401() {
            when(usuarios.findActiveCredentials(UserMother.ID)).thenReturn(Mono.empty());

            StepVerifier.create(useCase().changePassword(UserMother.ID,
                            new ChangePasswordCommand(UserMother.PASSWORD, NUEVA)))
                    .verifyErrorSatisfies(error -> esperaError(error, 401, ErrorCodes.UNAUTHENTICATED, "authorization"));

            verify(usuarios, never()).updatePassword(any(), anyString());
        }
    }

    @Nested
    class DefinirPrimeraContrasena {

        private static final String NUEVA = "primeraClaveLarga";

        private static final String HASH_NUEVO = "$2a$10$primerHashQueNoEsReal";

        private static final FirstPasswordCommand DEFINICION = new FirstPasswordCommand(GoogleMother.ID_TOKEN, NUEVA);

        /** La contrasena se guarda como el hash que produjo el hasher, atada a la cuenta Google del token. */
        @Test
        void defineLaPrimeraConElHashYLaCuentaDeGoogle() {
            sinContrasena();
            when(google.verificar(GoogleMother.ID_TOKEN)).thenReturn(Mono.just(GoogleMother.unaIdentidad()));
            when(hasher.hash(NUEVA)).thenReturn(HASH_NUEVO);
            when(usuarios.setFirstPassword(UserMother.ID, GoogleMother.SUB, HASH_NUEVO)).thenReturn(Mono.just(true));

            StepVerifier.create(useCase().setFirstPassword(UserMother.ID, DEFINICION))
                    .verifyComplete();

            verify(usuarios).setFirstPassword(UserMother.ID, GoogleMother.SUB, HASH_NUEVO);
        }

        @Test
        void unUsuarioInactivoEsUn401() {
            when(usuarios.findActiveCredentials(UserMother.ID)).thenReturn(Mono.empty());

            StepVerifier.create(useCase().setFirstPassword(UserMother.ID, DEFINICION))
                    .verifyErrorSatisfies(error -> esperaError(error, 401, ErrorCodes.UNAUTHENTICATED, "authorization"));

            verifyNoInteractions(google);
        }

        /** El estado va antes que Google: no se verifica un token para algo que se va a rechazar (decision 5). */
        @Test
        void conContrasenaEsUn409SinLlamarAGoogle() {
            when(usuarios.findActiveCredentials(UserMother.ID)).thenReturn(Mono.just(UserMother.credenciales()));

            StepVerifier.create(useCase().setFirstPassword(UserMother.ID, DEFINICION))
                    .verifyErrorSatisfies(error -> esperaError(error, 409, ErrorCodes.INVALID_STATE, "newPassword"));

            verifyNoInteractions(google, hasher);
        }

        @Test
        void unTokenInvalidoEsUn400EnIdToken() {
            sinContrasena();
            when(google.verificar(GoogleMother.ID_TOKEN)).thenReturn(Mono.empty());

            StepVerifier.create(useCase().setFirstPassword(UserMother.ID, DEFINICION))
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

            StepVerifier.create(useCase().setFirstPassword(UserMother.ID, DEFINICION))
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

            StepVerifier.create(useCase().setFirstPassword(UserMother.ID, DEFINICION))
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

            StepVerifier.create(useCase().setFirstPassword(UserMother.ID, DEFINICION))
                    .verifyErrorSatisfies(error -> esperaError(error, 400, ErrorCodes.INVALID_CREDENTIALS, "idToken"));

            nadaSeGuardo();
        }

        @Test
        void rechazaUnTokenSinEmision() {
            sinContrasena();
            when(google.verificar(GoogleMother.ID_TOKEN)).thenReturn(Mono.just(GoogleMother.emitidaEn(null)));

            StepVerifier.create(useCase().setFirstPassword(UserMother.ID, DEFINICION))
                    .verifyErrorSatisfies(error -> esperaError(error, 400, ErrorCodes.INVALID_CREDENTIALS, "idToken"));

            nadaSeGuardo();
        }

        /** Valido y reciente, pero de otra cuenta Google: cuenta el sub, no el correo. */
        @Test
        void rechazaUnTokenDeOtraCuentaDeGoogle() {
            sinContrasena();
            GoogleIdentity otraCuenta = new GoogleIdentity("otro-sub", UserMother.EMAIL, "Ana", "Gomez", RelojFijo.AHORA);
            when(google.verificar(GoogleMother.ID_TOKEN)).thenReturn(Mono.just(otraCuenta));

            StepVerifier.create(useCase().setFirstPassword(UserMother.ID, DEFINICION))
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

            StepVerifier.create(useCase().setFirstPassword(UserMother.ID, DEFINICION))
                    .verifyErrorSatisfies(error -> esperaError(error, 409, ErrorCodes.INVALID_STATE, "newPassword"));
        }

        private void sinContrasena() {
            when(usuarios.findActiveCredentials(UserMother.ID)).thenReturn(Mono.just(UserMother.credencialesDeGoogle()));
        }

        private void nadaSeGuardo() {
            verify(hasher, never()).hash(anyString());
            verify(usuarios, never()).setFirstPassword(any(), anyString(), anyString());
        }
    }

    private UserUseCase useCase() {
        return new UserUseCase(usuarios, hasher, google, monedas, tasas, RELOJ);
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
