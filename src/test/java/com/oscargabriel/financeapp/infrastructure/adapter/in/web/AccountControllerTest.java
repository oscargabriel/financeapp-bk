package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import java.math.BigDecimal;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.JwtMutator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.CreateAccountCommand;
import com.oscargabriel.financeapp.domain.model.UpdateAccountCommand;
import com.oscargabriel.financeapp.domain.port.in.CreateAccountPort;
import com.oscargabriel.financeapp.domain.port.in.ListAccountsPort;
import com.oscargabriel.financeapp.domain.port.in.UpdateAccountPort;
import com.oscargabriel.financeapp.infrastructure.config.JwtConfig;
import com.oscargabriel.financeapp.infrastructure.config.SecurityConfig;
import com.oscargabriel.financeapp.support.AccountMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Sin el base-path /api, igual que el resto de slices: la ruta completa la cubre AccountsIT. */
@WebFluxTest(AccountController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class AccountControllerTest {

    private static final String URI_BASE = "/accounts";

    @Autowired
    private WebTestClient webTestClient;

    private static final String PARCHE_NOMBRE = """
            {"name": "Visa"}
            """;

    private static final String CUERPO_TARJETA = """
            {"name": "Mastercard", "type": "CREDIT", "currencyCode": "COP", "initialBalance": -200000,
             "creditLimit": 3000000, "statementDay": 20, "paymentDueDay": 5}
            """;

    @MockitoBean
    private ListAccountsPort listAccounts;

    @MockitoBean
    private CreateAccountPort createAccount;

    @MockitoBean
    private UpdateAccountPort updateAccount;

    private static JwtMutator tokenDelUsuario() {
        return mockJwt().jwt(jwt -> jwt.subject(AccountMother.USER_ID.toString()));
    }

    @Test
    void devuelve401CuandoNoHayCredenciales() {
        webTestClient.get().uri(URI_BASE)
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(listAccounts);
    }

    @Test
    void devuelveLasCuentasConElFormatoDelContrato() {
        when(listAccounts.list(eq(AccountMother.USER_ID), anyBoolean()))
                .thenReturn(Flux.just(AccountMother.visa()));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$[0].id").isEqualTo(AccountMother.VISA_ID.toString())
                .jsonPath("$[0].name").isEqualTo("Visa")
                .jsonPath("$[0].type").isEqualTo("CREDIT")
                .jsonPath("$[0].currencyCode").isEqualTo("COP")
                .jsonPath("$[0].currentBalance").isEqualTo(-658000.0)
                .jsonPath("$[0].initialBalance").isEqualTo(0)
                .jsonPath("$[0].creditLimit").isEqualTo(5000000.0)
                .jsonPath("$[0].availableCredit").isEqualTo(4342000.0)
                .jsonPath("$[0].statementDay").isEqualTo(15)
                .jsonPath("$[0].paymentDueDay").isEqualTo(5)
                .jsonPath("$[0].isActive").isEqualTo(true)
                .jsonPath("$[0].userId").doesNotExist();
    }

    @Test
    void dejaEnNullElCupoYLasFechasDeUnaCuentaQueNoEsDeCredito() {
        when(listAccounts.list(eq(AccountMother.USER_ID), anyBoolean()))
                .thenReturn(Flux.just(AccountMother.efectivo()));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].type").isEqualTo("CASH")
                .jsonPath("$[0].initialBalance").isEqualTo(500000.0)
                .jsonPath("$[0].currentBalance").isEqualTo(322500.0)
                .jsonPath("$[0].creditLimit").isEqualTo(null)
                .jsonPath("$[0].availableCredit").isEqualTo(null)
                .jsonPath("$[0].statementDay").isEqualTo(null)
                .jsonPath("$[0].paymentDueDay").isEqualTo(null);
    }

    @Test
    void marcaComoInactivaLaCuentaDesactivada() {
        when(listAccounts.list(eq(AccountMother.USER_ID), anyBoolean()))
                .thenReturn(Flux.just(AccountMother.inactiva()));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "?includeInactive=true")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].isActive").isEqualTo(false);
    }

    @Test
    void devuelveUnArrayVacioCuandoElUsuarioNoTieneCuentas() {
        when(listAccounts.list(eq(AccountMother.USER_ID), anyBoolean())).thenReturn(Flux.empty());

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE)
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("[]");
    }

    @Test
    void sinElParametroExcluyeLasDesactivadas() {
        when(listAccounts.list(eq(AccountMother.USER_ID), anyBoolean())).thenReturn(Flux.empty());

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE)
                .exchange()
                .expectStatus().isOk();

        verify(listAccounts).list(AccountMother.USER_ID, false);
    }

    @Test
    void conElParametroEnTrueIncluyeLasDesactivadas() {
        when(listAccounts.list(eq(AccountMother.USER_ID), anyBoolean())).thenReturn(Flux.empty());

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "?includeInactive=true")
                .exchange()
                .expectStatus().isOk();

        verify(listAccounts).list(AccountMother.USER_ID, true);
    }

    @Test
    void conElParametroEnFalseExcluyeLasDesactivadas() {
        when(listAccounts.list(eq(AccountMother.USER_ID), anyBoolean())).thenReturn(Flux.empty());

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "?includeInactive=false")
                .exchange()
                .expectStatus().isOk();

        verify(listAccounts).list(AccountMother.USER_ID, false);
    }

    @Test
    void devuelve400CuandoElParametroNoEsUnBooleano() {
        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE + "?includeInactive=si")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.VALIDATION_ERROR.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("includeInactive");

        verifyNoInteractions(listAccounts);
    }

    @Test
    void devuelve401AlCrearCuandoNoHayCredenciales() {
        webTestClient.post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(CUERPO_TARJETA)
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(createAccount);
    }

    @Test
    void creaLaCuentaYRespondeConElMismoContratoQueElListado() {
        when(createAccount.create(eq(AccountMother.USER_ID), any()))
                .thenReturn(Mono.just(AccountMother.tarjetaCreada()));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(CUERPO_TARJETA)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo("20000000-0000-7000-8000-000000000007")
                .jsonPath("$.name").isEqualTo("Mastercard")
                .jsonPath("$.type").isEqualTo("CREDIT")
                .jsonPath("$.currencyCode").isEqualTo("COP")
                .jsonPath("$.currentBalance").isEqualTo(-200000.0)
                .jsonPath("$.initialBalance").isEqualTo(-200000.0)
                .jsonPath("$.creditLimit").isEqualTo(3000000.0)
                .jsonPath("$.availableCredit").isEqualTo(2800000.0)
                .jsonPath("$.statementDay").isEqualTo(20)
                .jsonPath("$.paymentDueDay").isEqualTo(5)
                .jsonPath("$.isActive").isEqualTo(true)
                .jsonPath("$.userId").doesNotExist();
    }

    @Test
    void pasaAlCasoDeUsoElUsuarioDelTokenYElCuerpoTalCualLlega() {
        when(createAccount.create(eq(AccountMother.USER_ID), any()))
                .thenReturn(Mono.just(AccountMother.tarjetaCreada()));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name": "Mastercard", "type": "CREDIT", "currencyCode": "COP",
                         "initialBalance": -200000, "creditLimit": 3000000, "statementDay": 20,
                         "paymentDueDay": 5}
                        """)
                .exchange()
                .expectStatus().isCreated();

        ArgumentCaptor<CreateAccountCommand> comando = ArgumentCaptor.forClass(CreateAccountCommand.class);
        verify(createAccount).create(eq(AccountMother.USER_ID), comando.capture());
        assertThat(comando.getValue()).isEqualTo(new CreateAccountCommand("Mastercard", "CREDIT", "COP",
                new BigDecimal("-200000"), new BigDecimal("3000000"), 20, 5, null));
    }

    /** Las reglas viven en el record: un cuerpo invalido no llega al caso de uso. */
    @Test
    void devuelve400ConLosCamposInvalidosSinLlamarAlCasoDeUso() {
        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name": "Debito", "type": "DEBIT", "currencyCode": "COP",
                         "creditLimit": 1000, "currentBalance": 1}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.length()").isEqualTo(2)
                .jsonPath("$.errors[?(@.field == 'creditLimit')].description")
                .isEqualTo("Solo una cuenta CREDIT tiene cupo")
                .jsonPath("$.errors[?(@.field == 'currentBalance')].description")
                .isEqualTo("El saldo vigente lo calcula el sistema; envia initialBalance")
                .jsonPath("$.errors[?(@.code != 'VALIDATION_ERROR')]").isEmpty();

        verifyNoInteractions(createAccount);
    }

    /** Lo que necesita la base, como que la moneda este activa, lo sigue reportando el caso de uso. */
    @Test
    void devuelveElErrorDeValidacionDelCasoDeUso() {
        when(createAccount.create(eq(AccountMother.USER_ID), any()))
                .thenReturn(Mono.error(new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                        "La moneda no existe en el catalogo o no esta activa", "currencyCode")));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(CUERPO_TARJETA)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.VALIDATION_ERROR.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("currencyCode");
    }

    @Test
    void modificaLaCuentaConElUsuarioDelTokenElIdYElParcheTalCualLlega() {
        when(updateAccount.update(eq(AccountMother.USER_ID), eq(AccountMother.VISA_ID), any()))
                .thenReturn(Mono.just(AccountMother.visa()));

        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_BASE + "/" + AccountMother.VISA_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name": " Visa ", "currencyCode": "cop", "initialBalance": 10, "creditLimit": 6000000,
                         "statementDay": 16, "paymentDueDay": 6}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(AccountMother.VISA_ID.toString())
                .jsonPath("$.availableCredit").isEqualTo(4342000.0);

        ArgumentCaptor<UpdateAccountCommand> comando = ArgumentCaptor.forClass(UpdateAccountCommand.class);
        verify(updateAccount).update(eq(AccountMother.USER_ID), eq(AccountMother.VISA_ID), comando.capture());
        assertThat(comando.getValue()).isEqualTo(new UpdateAccountCommand(" Visa ", "cop", new BigDecimal("10"),
                new BigDecimal("6000000"), 16, 6));
    }

    @Test
    void rechazaElParcheVacioSobreElCuerpoSinLlamarAlCasoDeUso() {
        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_BASE + "/" + AccountMother.VISA_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"creditLimit": null}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.VALIDATION_ERROR.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("body");

        verifyNoInteractions(updateAccount);
    }

    @Test
    void rechazaUnIdQueNoEsUuidSobreElId() {
        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_BASE + "/abc")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(PARCHE_NOMBRE)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.VALIDATION_ERROR.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("id");

        verifyNoInteractions(updateAccount);
    }

    @Test
    void rechazaElSaldoVigenteElTipoYElEstadoSinLlamarAlCasoDeUso() {
        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_BASE + "/" + AccountMother.VISA_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"currentBalance": 1, "type": "CASH", "isActive": false}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.length()").isEqualTo(3)
                .jsonPath("$.errors[?(@.field == 'currentBalance')]").exists()
                .jsonPath("$.errors[?(@.field == 'type')]").exists()
                .jsonPath("$.errors[?(@.field == 'isActive')]").exists();

        verifyNoInteractions(updateAccount);
    }

    static Stream<Arguments> erroresDelCasoDeUso() {
        return Stream.of(
                Arguments.of(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "id"),
                Arguments.of(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR, "creditLimit"),
                Arguments.of(HttpStatus.CONFLICT, ErrorCodes.RESOURCE_IN_USE, "currencyCode"),
                Arguments.of(HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_RESOURCE, "name"));
    }

    @ParameterizedTest(name = "{1} en {2}")
    @MethodSource("erroresDelCasoDeUso")
    void propagaElErrorDelCasoDeUsoConSuCodigoYCampo(HttpStatus status, ErrorCodes codigo, String campo) {
        when(updateAccount.update(eq(AccountMother.USER_ID), eq(AccountMother.VISA_ID), any()))
                .thenReturn(Mono.error(new BadRequestException(status, codigo, "error del caso de uso", campo)));

        webTestClient.mutateWith(tokenDelUsuario()).patch().uri(URI_BASE + "/" + AccountMother.VISA_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(PARCHE_NOMBRE)
                .exchange()
                .expectStatus().isEqualTo(status)
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(codigo.getCode())
                .jsonPath("$.errors[0].field").isEqualTo(campo);
    }

    @Test
    void devuelve401AlModificarCuandoNoHayCredenciales() {
        webTestClient.patch().uri(URI_BASE + "/" + AccountMother.VISA_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(PARCHE_NOMBRE)
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(updateAccount);
    }

    @Test
    void devuelve401CuandoElSubjectDelTokenNoEsUnUuid() {
        webTestClient.mutateWith(mockJwt().jwt(jwt -> jwt.subject("no-es-uuid")))
                .get().uri(URI_BASE)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.UNAUTHENTICATED.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("authorization");

        verifyNoInteractions(listAccounts);
    }
}
