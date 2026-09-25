package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.JwtMutator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.port.in.ListAccountsPort;
import com.oscargabriel.financeapp.infrastructure.config.JwtConfig;
import com.oscargabriel.financeapp.infrastructure.config.SecurityConfig;
import com.oscargabriel.financeapp.support.AccountMother;

import reactor.core.publisher.Flux;

/** Sin el base-path /api, igual que el resto de slices: la ruta completa la cubre AccountsIT. */
@WebFluxTest(AccountController.class)
@Import({SecurityConfig.class, JwtConfig.class})
class AccountControllerTest {

    private static final String URI_BASE = "/accounts";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ListAccountsPort listAccounts;

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
