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

import org.junit.jupiter.api.Test;
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
import com.oscargabriel.financeapp.domain.port.in.CreateAccountPort;
import com.oscargabriel.financeapp.domain.port.in.ListAccountsPort;
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

    private static final String CUERPO_TARJETA = """
            {"name": "Mastercard", "type": "CREDIT", "currencyCode": "COP", "initialBalance": -200000,
             "creditLimit": 3000000, "statementDay": 20, "paymentDueDay": 5}
            """;

    @MockitoBean
    private ListAccountsPort listAccounts;

    @MockitoBean
    private CreateAccountPort createAccount;

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
                .jsonPath("$[0].availableCredit").isEqualTo(4342000.0)
                .jsonPath("$[0].isActive").isEqualTo(true)
                .jsonPath("$[0].creditLimit").doesNotExist()
                .jsonPath("$[0].userId").doesNotExist();
    }

    @Test
    void dejaEnNullElCupoDeUnaCuentaQueNoEsDeCredito() {
        when(listAccounts.list(eq(AccountMother.USER_ID), anyBoolean()))
                .thenReturn(Flux.just(AccountMother.efectivo()));

        webTestClient.mutateWith(tokenDelUsuario()).get().uri(URI_BASE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].type").isEqualTo("CASH")
                .jsonPath("$[0].currentBalance").isEqualTo(322500.0)
                .jsonPath("$[0].availableCredit").isEqualTo(null);
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
                .jsonPath("$.availableCredit").isEqualTo(2800000.0)
                .jsonPath("$.isActive").isEqualTo(true)
                .jsonPath("$.initialBalance").doesNotExist()
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
                         "paymentDueDay": 5, "currentBalance": 1}
                        """)
                .exchange()
                .expectStatus().isCreated();

        ArgumentCaptor<CreateAccountCommand> comando = ArgumentCaptor.forClass(CreateAccountCommand.class);
        verify(createAccount).create(eq(AccountMother.USER_ID), comando.capture());
        assertThat(comando.getValue()).isEqualTo(new CreateAccountCommand("Mastercard", "CREDIT", "COP",
                new BigDecimal("-200000"), new BigDecimal("3000000"), 20, 5, BigDecimal.ONE));
    }

    @Test
    void devuelveElErrorDeValidacionDelCasoDeUso() {
        when(createAccount.create(eq(AccountMother.USER_ID), any()))
                .thenReturn(Mono.error(new BadRequestException(HttpStatus.BAD_REQUEST,
                        ErrorCodes.VALIDATION_ERROR, "Solo una cuenta CREDIT tiene cupo", "creditLimit")));

        webTestClient.mutateWith(tokenDelUsuario()).post().uri(URI_BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(CUERPO_TARJETA)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo(ErrorCodes.VALIDATION_ERROR.getCode())
                .jsonPath("$.errors[0].field").isEqualTo("creditLimit");
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
