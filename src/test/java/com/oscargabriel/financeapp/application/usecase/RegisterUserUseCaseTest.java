package com.oscargabriel.financeapp.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

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
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.RegistrationCommand;
import com.oscargabriel.financeapp.domain.model.User;
import com.oscargabriel.financeapp.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.domain.port.out.PasswordHasherPort;
import com.oscargabriel.financeapp.domain.port.out.UserRepositoryPort;
import com.oscargabriel.financeapp.support.UserMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegisterUserUseCaseTest {

    private static final Clock RELOJ = Clock.fixed(
            Instant.parse("2026-09-15T15:00:00Z"), ZoneId.of("America/Bogota"));

    @Mock
    private UserRepositoryPort usuarios;

    @Mock
    private CurrencyQueryPort monedas;

    @Mock
    private PasswordHasherPort hasher;

    @Captor
    private ArgumentCaptor<User> usuarioGuardado;

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
    void reportaTodosLosCamposInvalidosEnUnaSolaRespuesta() {
        StepVerifier.create(useCase().register(new RegistrationCommand(
                        "no-es-un-email", "corta", "  ", null, "US", "Marte/Olympus")))
                .verifyErrorSatisfies(error -> {
                    assertThat(((BadRequestException) error).getHttpStatus().value()).isEqualTo(400);
                    assertThat(campos(error)).containsExactlyInAnyOrder(
                            "email", "password", "firstName", "baseCurrencyCode", "timezone");
                });
    }

    /** Sin consultar nada: una validacion de formato no justifica ir a la base. */
    @Test
    void noTocaLaBaseCuandoElPayloadNiSiquieraEsValido() {
        StepVerifier.create(useCase().register(conEmail("no-es-un-email")))
                .verifyError(BadRequestException.class);

        verifyNoInteractions(usuarios, monedas, hasher);
    }

    /**
     * BCrypt trunca en silencio a partir de 72 bytes: sin el tope, dos claves que compartan el
     * prefijo entrarian como la misma.
     */
    @Test
    void rechazaLaClaveQueSuperaElLimiteDeBcrypt() {
        StepVerifier.create(useCase().register(new RegistrationCommand(
                        UserMother.EMAIL, "a".repeat(73), "Ana", null, null, null)))
                .verifyErrorSatisfies(error -> assertThat(campos(error)).containsExactly("password"));
    }

    private void altaPosible() {
        when(usuarios.existsByEmail(anyString())).thenReturn(Mono.just(false));
        when(monedas.exists(anyString())).thenReturn(Mono.just(true));
        when(hasher.hash(anyString())).thenReturn(UserMother.HASH);
        when(usuarios.createWithDefaultCategories(any())).thenReturn(Mono.just(22L));
    }

    private static RegistrationCommand conEmail(String email) {
        return new RegistrationCommand(email, UserMother.PASSWORD, "Ana", null, null, null);
    }

    private static List<String> campos(Throwable error) {
        return ((BadRequestException) error).getErrorResponse().getErrors().stream()
                .map(ErrorDetail::getField)
                .toList();
    }

    private RegisterUserUseCase useCase() {
        return new RegisterUserUseCase(usuarios, monedas, hasher, RELOJ);
    }
}
