package com.oscargabriel.financeapp.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.AccessToken;
import com.oscargabriel.financeapp.core.domain.model.GoogleRegistrationCommand;
import com.oscargabriel.financeapp.core.domain.model.LoginCommand;
import com.oscargabriel.financeapp.core.domain.model.NewRefreshToken;
import com.oscargabriel.financeapp.core.domain.model.RefreshToken;
import com.oscargabriel.financeapp.core.domain.model.RegistrationCommand;
import com.oscargabriel.financeapp.core.domain.model.RotatedSession;
import com.oscargabriel.financeapp.core.domain.model.SessionTokens;
import com.oscargabriel.financeapp.core.domain.model.User;
import com.oscargabriel.financeapp.core.domain.model.UserCredentials;
import com.oscargabriel.financeapp.core.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.GoogleIdTokenVerifierPort;
import com.oscargabriel.financeapp.core.domain.port.out.PasswordHasherPort;
import com.oscargabriel.financeapp.core.domain.port.out.RefreshTokenGeneratorPort;
import com.oscargabriel.financeapp.core.domain.port.out.RegistrationAllowlistPort;
import com.oscargabriel.financeapp.core.domain.port.out.SesionRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.TokenIssuerPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;
import com.oscargabriel.financeapp.core.support.GoogleMother;
import com.oscargabriel.financeapp.core.support.UserMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AuthUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-10-12T15:00:00Z");

    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    private static final Duration DURACION_DE_LA_SESION = Duration.ofHours(24);

    private static final UUID ID = UUID.fromString("01994f00-0000-7000-8000-000000000001");

    @Mock
    private UserRepositoryPort usuarios;

    @Mock
    private CurrencyQueryPort monedas;

    @Mock
    private PasswordHasherPort hasher;

    @Mock
    private RegistrationAllowlistPort admitidos;

    @Mock
    private GoogleIdTokenVerifierPort verificador;

    @Mock
    private SesionRepositoryPort sesiones;

    @Mock
    private RefreshTokenGeneratorPort generador;

    @Mock
    private TokenIssuerPort emisor;

    @Mock
    private AperturaDeSesion aperturaDeSesion;

    @Captor
    private ArgumentCaptor<User> usuarioGuardado;

    @Nested
    class Registrar {

        @Test
        void guardaElUsuarioConElHashYNuncaConLaClaveEnClaro() {
            altaPosible();

            StepVerifier.create(useCase().register(UserMother.unAlta()))
                    .assertNext(registrado -> {
                        assertThat(registrado.defaultCategories()).isEqualTo(22L);
                        assertThat(registrado.user().passwordHash()).isEqualTo(UserMother.HASH);
                    })
                    .verifyComplete();

            verify(usuarios).createWithDefaultCategories(usuarioGuardado.capture());
            assertThat(usuarioGuardado.getValue().passwordHash()).isEqualTo(UserMother.HASH);
            assertThat(usuarioGuardado.getValue().passwordHash()).isNotEqualTo(UserMother.PASSWORD);
        }

        @Test
        void generaElIdentificadorComoUuidVersionSiete() {
            altaPosible();

            StepVerifier.create(useCase().register(UserMother.unAlta()))
                    .assertNext(registrado -> assertThat(registrado.user().id().version()).isEqualTo(7))
                    .verifyComplete();
        }

        @Test
        void completaMonedaYZonaConLosMismosValoresQueElDefaultDeLaTabla() {
            altaPosible();

            StepVerifier.create(useCase().register(UserMother.unAltaSinPreferencias()))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(usuarios).createWithDefaultCategories(usuarioGuardado.capture());
            assertThat(usuarioGuardado.getValue().baseCurrencyCode()).isEqualTo("COP");
            assertThat(usuarioGuardado.getValue().timezone()).isEqualTo("America/Bogota");
        }

        @Test
        void guardaElCelularRecortado() {
            altaPosible();

            StepVerifier.create(useCase().register(UserMother.unAltaConCelular(" +573001234567 ")))
                    .assertNext(registrado -> assertThat(registrado.user().phone()).isEqualTo("+573001234567"))
                    .verifyComplete();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void unCelularAusenteOEnBlancoSeGuardaComoNull(String celular) {
            altaPosible();

            StepVerifier.create(useCase().register(UserMother.unAltaConCelular(celular)))
                    .assertNext(registrado -> assertThat(registrado.user().phone()).isNull())
                    .verifyComplete();
        }

        @Test
        void normalizaElEmailAMinusculasPorqueElUnicoDeLaBaseEsSobreLower() {
            altaPosible();

            StepVerifier.create(useCase().register(conEmail("  Ana@Ejemplo.COM  ")))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(usuarios).createWithDefaultCategories(usuarioGuardado.capture());
            assertThat(usuarioGuardado.getValue().email()).isEqualTo("ana@ejemplo.com");
        }

        @Test
        void emiteConflictoCuandoElEmailYaEstaRegistrado() {
            altaPosible();
            when(usuarios.existsByEmail(anyString())).thenReturn(Mono.just(true));

            StepVerifier.create(useCase().register(UserMother.unAlta()))
                    .verifyErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(BadRequestException.class);
                        BadRequestException bre = (BadRequestException) error;
                        assertThat(bre.getHttpStatus().value()).isEqualTo(409);
                        assertThat(bre.getErrorResponse().getErrors())
                                .singleElement()
                                .satisfies(detalle -> {
                                    assertThat(detalle.getCode())
                                            .isEqualTo(ErrorCodes.DUPLICATE_RESOURCE.getCode());
                                    assertThat(detalle.getField()).isEqualTo("email");
                                });
                    });

            verify(usuarios, never()).createWithDefaultCategories(any());
        }

        @Test
        void emiteErrorCuandoLaMonedaNoEstaEnElCatalogo() {
            altaPosible();
            when(monedas.exists("USD")).thenReturn(Mono.just(false));

            StepVerifier.create(useCase().register(UserMother.unAlta()))
                    .verifyErrorSatisfies(error -> assertThat(campos(error))
                            .containsExactly("baseCurrencyCode"));

            verify(usuarios, never()).createWithDefaultCategories(any());
        }

        @Test
        void rechazaConProhibidoUnCorreoQueNoEstaAdmitido() {
            altaPosible();
            when(admitidos.isAllowed(anyString())).thenReturn(Mono.just(false));

            StepVerifier.create(useCase().register(UserMother.unAlta()))
                    .verifyErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(BadRequestException.class);
                        BadRequestException bre = (BadRequestException) error;
                        assertThat(bre.getHttpStatus().value()).isEqualTo(403);
                        assertThat(bre.getErrorResponse().getErrors())
                                .singleElement()
                                .satisfies(detalle -> {
                                    assertThat(detalle.getCode())
                                            .isEqualTo(ErrorCodes.REGISTRATION_NOT_ALLOWED.getCode());
                                    assertThat(detalle.getField()).isEqualTo("email");
                                });
                    });

            verify(usuarios, never()).createWithDefaultCategories(any());
        }

        @Test
        void consultaLaListaConElCorreoNormalizado() {
            altaPosible();

            StepVerifier.create(useCase().register(conEmail(" Invitado@FinanceApp.LOCAL ")))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(admitidos).isAllowed("invitado@financeapp.local");
        }

        @Test
        void noRevelaSiUnCorreoNoAdmitidoYaTieneCuenta() {
            altaPosible();
            when(admitidos.isAllowed(anyString())).thenReturn(Mono.just(false));
            lenient().when(usuarios.existsByEmail(anyString())).thenReturn(Mono.just(true));

            StepVerifier.create(useCase().register(UserMother.unAlta()))
                    .verifyErrorSatisfies(error -> assertThat(((BadRequestException) error).getHttpStatus().value())
                            .isEqualTo(403));

            verify(usuarios, never()).existsByEmail(anyString());
        }

        @Test
        void laMonedaInexistenteSaleAntesQueElRechazo() {
            altaPosible();
            when(monedas.exists("USD")).thenReturn(Mono.just(false));
            lenient().when(admitidos.isAllowed(anyString())).thenReturn(Mono.just(false));

            StepVerifier.create(useCase().register(UserMother.unAlta()))
                    .verifyErrorSatisfies(error -> assertThat(campos(error))
                            .containsExactly("baseCurrencyCode"));

            verify(admitidos, never()).isAllowed(anyString());
        }

        @Test
        void conLaRestriccionApagadaNoConsultaLaLista() {
            altaPosible();
            lenient().when(admitidos.isAllowed(anyString())).thenReturn(Mono.just(false));

            StepVerifier.create(useCaseSinRestriccion().register(UserMother.unAlta()))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(admitidos, never()).isAllowed(anyString());
        }

        private void altaPosible() {
            lenient().when(admitidos.isAllowed(anyString())).thenReturn(Mono.just(true));
            lenient().when(usuarios.existsByEmail(anyString())).thenReturn(Mono.just(false));
            lenient().when(monedas.exists(anyString())).thenReturn(Mono.just(true));
            lenient().when(hasher.hash(anyString())).thenReturn(UserMother.HASH);
            lenient().when(usuarios.createWithDefaultCategories(any())).thenReturn(Mono.just(22L));
        }

        private static RegistrationCommand conEmail(String email) {
            return new RegistrationCommand(email, UserMother.PASSWORD, "Ana", null, null, null, null);
        }

        private static List<String> campos(Throwable error) {
            return ((BadRequestException) error).getErrorResponse().getErrors().stream()
                    .map(ErrorDetail::getField)
                    .toList();
        }
    }

    @Nested
    class Login {

        private static final long VERSION = 2L;

        private static final AccessToken TOKEN = new AccessToken("un.jwt.firmado", Duration.ofHours(1));

        private static final String REFRESH = "refresh-en-claro";

        private static final String HASH_DEL_REFRESH = "hash-del-refresh";

        private static final SessionTokens SESION =
                new SessionTokens(TOKEN, new RefreshToken(REFRESH, DURACION_DE_LA_SESION));

        /** Lo que guarda la sesion lo prueba AperturaDeSesionTest; aqui, que el login la abre. */
        @Test
        void abreUnaSesionDelUsuario() {
            loginPosible();

            StepVerifier.create(useCaseConAperturaReal().login(new LoginCommand(UserMother.EMAIL, UserMother.PASSWORD)))
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

            StepVerifier.create(useCaseConAperturaReal().login(new LoginCommand(UserMother.EMAIL, UserMother.PASSWORD)))
                    .verifyErrorSatisfies(error -> assertThat(unaCredencialInvalida(error)).isTrue());

            verifyNoInteractions(hasher, emisor, generador, sesiones);
        }

        @Test
        void emiteElTokenDelUsuarioCuandoLaClaveCoincide() {
            loginPosible();

            StepVerifier.create(useCaseConAperturaReal().login(new LoginCommand(UserMother.EMAIL, UserMother.PASSWORD)))
                    .expectNext(SESION)
                    .verifyComplete();

            verify(emisor).issueFor(ID, VERSION);
        }

        /** El indice unico de la tabla es sobre lower(email), y el alta guarda el correo normalizado. */
        @Test
        void normalizaElCorreoAntesDeBuscarlo() {
            loginPosible();

            StepVerifier.create(useCaseConAperturaReal().login(new LoginCommand("  ANA@Ejemplo.COM  ", UserMother.PASSWORD)))
                    .expectNext(SESION)
                    .verifyComplete();

            verify(usuarios).findActiveByEmail(UserMother.EMAIL);
        }

        @Test
        void devuelve401CuandoLaClaveNoCoincide() {
            when(usuarios.findActiveByEmail(anyString())).thenReturn(Mono.just(credenciales()));
            when(hasher.matches(anyString(), anyString())).thenReturn(false);

            StepVerifier.create(useCaseConAperturaReal().login(new LoginCommand(UserMother.EMAIL, "otraClave")))
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

            StepVerifier.create(useCaseConAperturaReal().login(new LoginCommand(UserMother.EMAIL, UserMother.PASSWORD)))
                    .verifyErrorSatisfies(error -> assertThat(unaCredencialInvalida(error)).isTrue());

            verifyNoInteractions(emisor, generador, sesiones);
        }

        /** OWASP A07: los dos fallos son indistinguibles desde fuera, hasta en el texto. */
        @Test
        void noDistingueElCorreoDesconocidoDeLaClaveIncorrecta() {
            when(usuarios.findActiveByEmail(anyString())).thenReturn(Mono.empty());
            BadRequestException sinUsuario = capturarError(
                    useCaseConAperturaReal().login(new LoginCommand(UserMother.EMAIL, UserMother.PASSWORD)));

            when(usuarios.findActiveByEmail(anyString())).thenReturn(Mono.just(credenciales()));
            when(hasher.matches(anyString(), anyString())).thenReturn(false);
            BadRequestException claveMala = capturarError(
                    useCaseConAperturaReal().login(new LoginCommand(UserMother.EMAIL, "otraClave")));

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

            StepVerifier.create(useCaseConAperturaReal().login(new LoginCommand(UserMother.EMAIL, UserMother.PASSWORD)))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(hasher).matches(UserMother.PASSWORD, UserMother.HASH);
        }

        private void loginPosible() {
            lenient().when(usuarios.findActiveByEmail(anyString())).thenReturn(Mono.just(credenciales()));
            lenient().when(hasher.matches(anyString(), anyString())).thenReturn(true);
            lenient().when(emisor.issueFor(any(), anyLong())).thenReturn(TOKEN);
            lenient().when(generador.generar()).thenReturn(REFRESH);
            lenient().when(generador.hash(REFRESH)).thenReturn(HASH_DEL_REFRESH);
            lenient().when(sesiones.abrir(any())).thenReturn(Mono.empty());
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

    @Nested
    class RegistrarConGoogle {

        @Test
        void creaUnUsuarioSinContrasenaConLosDatosDeGoogleYAbreSuSesion() {
            registroPosible();

            StepVerifier.create(useCase().googleRegister(
                            new GoogleRegistrationCommand(GoogleMother.ID_TOKEN, "Europe/Madrid")))
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

            StepVerifier.create(useCase().googleRegister(new GoogleRegistrationCommand(GoogleMother.ID_TOKEN, null)))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(usuarios).createWithDefaultCategories(usuarioGuardado.capture());
            assertThat(usuarioGuardado.getValue().timezone()).isEqualTo("America/Bogota");
        }

        @Test
        void rechazaUnTokenQueNoSirveSinTocarNada() {
            when(verificador.verificar(anyString())).thenReturn(Mono.empty());

            StepVerifier.create(useCase().googleRegister(new GoogleRegistrationCommand("no-sirve", null)))
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

            StepVerifier.create(useCase().googleRegister(new GoogleRegistrationCommand(GoogleMother.ID_TOKEN, null)))
                    .verifyErrorMatches(caido::equals);

            verifyNoInteractions(usuarios, admitidos, aperturaDeSesion);
        }

        /** Como el alta normal: a un correo no admitido no se le dice si ya tiene cuenta. */
        @Test
        void rechazaUnCorreoNoAdmitidoSinDecirSiTieneCuenta() {
            registroPosible();
            when(admitidos.isAllowed(anyString())).thenReturn(Mono.just(false));

            StepVerifier.create(useCase().googleRegister(new GoogleRegistrationCommand(GoogleMother.ID_TOKEN, null)))
                    .verifyErrorSatisfies(error -> esError(error, HttpStatus.FORBIDDEN,
                            ErrorCodes.REGISTRATION_NOT_ALLOWED, "email"));

            verify(usuarios, never()).existsByEmail(anyString());
            verify(usuarios, never()).createWithDefaultCategories(any());
        }

        @Test
        void rechazaUnCorreoYaRegistrado() {
            registroPosible();
            when(usuarios.existsByEmail(UserMother.EMAIL)).thenReturn(Mono.just(true));

            StepVerifier.create(useCase().googleRegister(new GoogleRegistrationCommand(GoogleMother.ID_TOKEN, null)))
                    .verifyErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT,
                            ErrorCodes.DUPLICATE_RESOURCE, "email"));

            verify(usuarios, never()).createWithDefaultCategories(any());
        }

        /** El usuario cambio su correo despues de registrarse con este Google: sigue teniendo cuenta. */
        @Test
        void rechazaUnaCuentaGoogleYaRegistrada() {
            registroPosible();
            when(usuarios.existsByGoogleSub(GoogleMother.SUB)).thenReturn(Mono.just(true));

            StepVerifier.create(useCase().googleRegister(new GoogleRegistrationCommand(GoogleMother.ID_TOKEN, null)))
                    .verifyErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT,
                            ErrorCodes.DUPLICATE_RESOURCE, "email"));

            verify(usuarios, never()).createWithDefaultCategories(any());
        }

        @Test
        void conLaRestriccionApagadaNoConsultaLaLista() {
            registroPosible();

            StepVerifier.create(useCaseSinRestriccion()
                            .googleRegister(new GoogleRegistrationCommand(GoogleMother.ID_TOKEN, null)))
                    .expectNextCount(1)
                    .verifyComplete();

            verifyNoInteractions(admitidos);
        }

        private void registroPosible() {
            lenient().when(verificador.verificar(GoogleMother.ID_TOKEN))
                    .thenReturn(Mono.just(GoogleMother.unaIdentidad()));
            lenient().when(admitidos.isAllowed(anyString())).thenReturn(Mono.just(true));
            lenient().when(usuarios.existsByEmail(anyString())).thenReturn(Mono.just(false));
            lenient().when(usuarios.existsByGoogleSub(anyString())).thenReturn(Mono.just(false));
            lenient().when(usuarios.createWithDefaultCategories(any())).thenReturn(Mono.just(22L));
            lenient().when(aperturaDeSesion.abrir(any(), anyLong())).thenReturn(Mono.just(GoogleMother.SESION));
        }
    }

    @Nested
    class LoginConGoogle {

        private static final long VERSION = 3L;

        @BeforeEach
        void tokenValido() {
            lenient().when(verificador.verificar(GoogleMother.ID_TOKEN))
                    .thenReturn(Mono.just(GoogleMother.unaIdentidad()));
            lenient().when(usuarios.findActiveByGoogleSub(anyString())).thenReturn(Mono.empty());
            lenient().when(usuarios.findActiveByEmail(anyString())).thenReturn(Mono.empty());
            lenient().when(aperturaDeSesion.abrir(any(), anyLong())).thenReturn(Mono.just(GoogleMother.SESION));
        }

        /** Busca por el sub, no por el correo: el correo de la cuenta pudo cambiar. */
        @Test
        void entraPorLaCuentaGoogleVinculada() {
            when(usuarios.findActiveByGoogleSub(GoogleMother.SUB))
                    .thenReturn(Mono.just(new UserCredentials(ID, null, VERSION, GoogleMother.SUB)));

            StepVerifier.create(useCase().googleLogin(GoogleMother.ID_TOKEN))
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

            StepVerifier.create(useCase().googleLogin(GoogleMother.ID_TOKEN))
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

            StepVerifier.create(useCase().googleLogin(GoogleMother.ID_TOKEN))
                    .verifyErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT,
                            ErrorCodes.DUPLICATE_RESOURCE, "email"));

            verifyNoInteractions(aperturaDeSesion);
        }

        @Test
        void rechazaUnCorreoVinculadoAOtraCuentaGoogle() {
            when(usuarios.findActiveByEmail(UserMother.EMAIL))
                    .thenReturn(Mono.just(new UserCredentials(ID, UserMother.HASH, VERSION, "otro-sub")));

            StepVerifier.create(useCase().googleLogin(GoogleMother.ID_TOKEN))
                    .verifyErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT,
                            ErrorCodes.DUPLICATE_RESOURCE, "email"));

            verify(usuarios, never()).linkGoogle(any(), anyString());
            verifyNoInteractions(aperturaDeSesion);
        }

        /** Sin cuenta, o con una desactivada o borrada: el puerto no emite nada en los tres casos. */
        @Test
        void responde404SiNoHayCuenta() {
            StepVerifier.create(useCase().googleLogin(GoogleMother.ID_TOKEN))
                    .verifyErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND,
                            ErrorCodes.ACCOUNT_NOT_REGISTERED, "idToken"));

            verifyNoInteractions(aperturaDeSesion);
        }

        @Test
        void rechazaUnTokenQueNoSirve() {
            when(verificador.verificar("no-sirve")).thenReturn(Mono.empty());

            StepVerifier.create(useCase().googleLogin("no-sirve"))
                    .verifyErrorSatisfies(error -> esError(error, HttpStatus.UNAUTHORIZED,
                            ErrorCodes.INVALID_CREDENTIALS, "idToken"));

            verifyNoInteractions(usuarios, aperturaDeSesion);
        }

        @Test
        void propagaElErrorDelVerificador() {
            BadRequestException caido = new BadRequestException(HttpStatus.BAD_GATEWAY,
                    ErrorCodes.EXTERNAL_SERVICE_ERROR, "El servicio de Google no esta disponible", "idToken");
            when(verificador.verificar(GoogleMother.ID_TOKEN)).thenReturn(Mono.error(caido));

            StepVerifier.create(useCase().googleLogin(GoogleMother.ID_TOKEN))
                    .verifyErrorMatches(caido::equals);

            verifyNoInteractions(usuarios, aperturaDeSesion);
        }
    }

    @Nested
    class Refrescar {

        private static final long VERSION = 4L;

        /** La sesion se abrio hace 4 horas: le quedan 20. */
        private static final Instant FIN_DE_LA_SESION = AHORA.plus(Duration.ofHours(20));

        private static final AccessToken TOKEN = new AccessToken("un.jwt.nuevo", Duration.ofHours(1));

        @BeforeEach
        void setUp() {
            lenient().when(generador.hash("refresh-viejo")).thenReturn("hash-viejo");
            lenient().when(generador.generar()).thenReturn("refresh-nuevo");
            lenient().when(generador.hash("refresh-nuevo")).thenReturn("hash-nuevo");
            lenient().when(emisor.issueFor(ID, VERSION)).thenReturn(TOKEN);
            lenient().when(sesiones.revocarSesionDe(anyString(), any())).thenReturn(Mono.empty());
        }

        @Test
        void cambiaUnRefreshVigentePorUnAccessYUnRefreshNuevos() {
            when(sesiones.rotar(eq("hash-viejo"), any(), eq("hash-nuevo"), eq(AHORA)))
                    .thenReturn(Mono.just(new RotatedSession(ID, VERSION, FIN_DE_LA_SESION)));

            StepVerifier.create(useCase().refresh("refresh-viejo"))
                    .expectNext(new SessionTokens(TOKEN, new RefreshToken("refresh-nuevo", Duration.ofHours(20))))
                    .verifyComplete();
        }

        /** El reemplazo vence con la sesion, no 24 horas despues del refresco. */
        @Test
        void elRefreshNuevoConservaElVencimientoDeLaSesion() {
            when(sesiones.rotar(any(), any(), any(), any()))
                    .thenReturn(Mono.just(new RotatedSession(ID, VERSION, FIN_DE_LA_SESION)));

            StepVerifier.create(useCase().refresh("refresh-viejo"))
                    .assertNext(tokens -> assertThat(tokens.refresh().expiresIn())
                            .isEqualTo(Duration.between(AHORA, FIN_DE_LA_SESION)))
                    .verifyComplete();
            verify(sesiones, never()).revocarSesionDe(anyString(), any());
        }

        /**
         * Vencido, inexistente, usado, revocado o de un usuario que ya no puede entrar: la base no los
         * distingue y el caso de uso tampoco. Si era un reuso, revocar la sesion invalida el reemplazo;
         * en los demas casos no queda nada vivo que revocar.
         */
        @Test
        void unRefreshQueNoSirveRevocaSuSesionYDa401() {
            when(sesiones.rotar(any(), any(), any(), any())).thenReturn(Mono.empty());

            StepVerifier.create(useCase().refresh("refresh-viejo"))
                    .verifyErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(BadRequestException.class);
                        BadRequestException rechazo = (BadRequestException) error;
                        ErrorDetail detalle = rechazo.getErrorResponse().getErrors().get(0);
                        assertThat(rechazo.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                        assertThat(detalle.getCode()).isEqualTo(ErrorCodes.UNAUTHENTICATED.getCode());
                        assertThat(detalle.getField()).isEqualTo("refreshToken");
                        assertThat(detalle.getDescription()).isEqualTo("Autenticacion requerida");
                    });

            verify(sesiones).revocarSesionDe("hash-viejo", AHORA);
            verifyNoInteractions(emisor);
        }
    }

    @Nested
    class Logout {

        @BeforeEach
        void setUp() {
            when(generador.hash("un-refresh")).thenReturn("hash-del-refresh");
        }

        /**
         * Revoca la sesion por el hash, que es lo unico que hay en la base. Que el refresh exista o no
         * no cambia la respuesta: el puerto completa vacio en los dos casos.
         */
        @Test
        void revocaLaSesionDelRefreshYCompletaVacio() {
            when(sesiones.revocarSesionDe("hash-del-refresh", AHORA)).thenReturn(Mono.empty());

            StepVerifier.create(useCase().logout("un-refresh")).verifyComplete();

            verify(sesiones).revocarSesionDe("hash-del-refresh", AHORA);
        }
    }

    private AuthUseCase useCase() {
        return authUseCase(new ReglasDelAlta(usuarios, admitidos, true), aperturaDeSesion);
    }

    private AuthUseCase useCaseSinRestriccion() {
        return authUseCase(new ReglasDelAlta(usuarios, admitidos, false), aperturaDeSesion);
    }

    /** El login con contrasena usa la apertura real; los de Google la mockean (design.md de FIN-129). */
    private AuthUseCase useCaseConAperturaReal() {
        return authUseCase(new ReglasDelAlta(usuarios, admitidos, true),
                new AperturaDeSesion(emisor, generador, sesiones, RELOJ, DURACION_DE_LA_SESION));
    }

    private AuthUseCase authUseCase(ReglasDelAlta reglas, AperturaDeSesion apertura) {
        return new AuthUseCase(usuarios, monedas, hasher, verificador, sesiones, generador, emisor, reglas,
                apertura, RELOJ);
    }

    private static void esError(Throwable error, HttpStatus status, ErrorCodes codigo, String campo) {
        assertThat(error).isInstanceOf(BadRequestException.class);
        BadRequestException bre = (BadRequestException) error;
        assertThat(bre.getHttpStatus()).isEqualTo(status);
        assertThat(bre.getErrorResponse().getErrors().get(0).getCode()).isEqualTo(codigo.getCode());
        assertThat(bre.getErrorResponse().getErrors().get(0).getField()).isEqualTo(campo);
    }
}
