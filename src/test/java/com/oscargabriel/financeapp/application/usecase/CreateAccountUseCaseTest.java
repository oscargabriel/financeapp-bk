package com.oscargabriel.financeapp.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.AccountType;
import com.oscargabriel.financeapp.domain.model.CreateAccountCommand;
import com.oscargabriel.financeapp.domain.model.NewAccount;
import com.oscargabriel.financeapp.domain.port.out.AccountRepositoryPort;
import com.oscargabriel.financeapp.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.support.AccountMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreateAccountUseCaseTest {

    private static final Clock RELOJ = Clock.fixed(
            Instant.parse("2026-09-26T15:00:00Z"), ZoneId.of("America/Bogota"));

    @Mock
    private AccountRepositoryPort cuentas;

    @Mock
    private CurrencyQueryPort monedas;

    @Captor
    private ArgumentCaptor<NewAccount> cuentaGuardada;

    @Test
    void guardaLaCuentaDelUsuarioConNombreYMonedaNormalizados() {
        altaPosible();

        StepVerifier.create(useCase().create(AccountMother.USER_ID, new CreateAccountCommand(
                        "  Billetera  ", "cash", " cop ", new BigDecimal("150000"), null, null, null, null, null)))
                .expectNextCount(1)
                .verifyComplete();

        verify(cuentas).create(cuentaGuardada.capture());
        NewAccount guardada = cuentaGuardada.getValue();
        assertThat(guardada.userId()).isEqualTo(AccountMother.USER_ID);
        assertThat(guardada.name()).isEqualTo("Billetera");
        assertThat(guardada.type()).isEqualTo(AccountType.CASH);
        assertThat(guardada.currencyCode()).isEqualTo("COP");
        assertThat(guardada.initialBalance()).isEqualByComparingTo("150000");
        assertThat(guardada.creditLimit()).isNull();
        assertThat(guardada.statementDay()).isNull();
        assertThat(guardada.paymentDueDay()).isNull();
        assertThat(guardada.monthlyInterestRate()).isNull();
    }

    @Test
    void generaElIdentificadorComoUuidVersionSiete() {
        altaPosible();

        StepVerifier.create(useCase().create(AccountMother.USER_ID, AccountMother.altaEfectivo()))
                .expectNextCount(1)
                .verifyComplete();

        verify(cuentas).create(cuentaGuardada.capture());
        assertThat(cuentaGuardada.getValue().id().version()).isEqualTo(7);
    }

    @Test
    void sinSaldoInicialLaCuentaEntraEnCero() {
        altaPosible();

        StepVerifier.create(useCase().create(AccountMother.USER_ID,
                        new CreateAccountCommand("Billetera", "CASH", "COP", null, null, null, null, null, null)))
                .expectNextCount(1)
                .verifyComplete();

        verify(cuentas).create(cuentaGuardada.capture());
        assertThat(cuentaGuardada.getValue().initialBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void unaTarjetaConservaSusCamposDeCreditoYSuDeudaInicial() {
        altaPosible();

        StepVerifier.create(useCase().create(AccountMother.USER_ID, AccountMother.altaTarjeta()))
                .expectNextCount(1)
                .verifyComplete();

        verify(cuentas).create(cuentaGuardada.capture());
        NewAccount guardada = cuentaGuardada.getValue();
        assertThat(guardada.type()).isEqualTo(AccountType.CREDIT);
        assertThat(guardada.initialBalance()).isEqualByComparingTo("-200000");
        assertThat(guardada.creditLimit()).isEqualByComparingTo("3000000");
        assertThat(guardada.statementDay()).isEqualTo(20);
        assertThat(guardada.paymentDueDay()).isEqualTo(5);
        assertThat(guardada.monthlyInterestRate()).isEqualByComparingTo("2.15");
    }

    @Test
    void devuelveLaCuentaComoLaDejoLaBase() {
        altaPosible();

        StepVerifier.create(useCase().create(AccountMother.USER_ID, AccountMother.altaTarjeta()))
                .assertNext(cuenta -> assertThat(cuenta).isEqualTo(AccountMother.tarjetaCreada()))
                .verifyComplete();
    }

    @Test
    void rechazaLaMonedaQueNoEstaActivaEnElCatalogoSinInsertar() {
        when(monedas.exists("USD")).thenReturn(Mono.just(false));

        StepVerifier.create(useCase().create(AccountMother.USER_ID, alta("Ahorros", "SAVINGS", "USD")))
                .expectErrorSatisfies(error -> {
                    BadRequestException bre = (BadRequestException) error;
                    assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(bre.getErrorResponse().getErrors())
                            .extracting(ErrorDetail::getField)
                            .containsExactly("currencyCode");
                })
                .verify();

        verify(cuentas, never()).create(any());
    }

    @Test
    void propagaElConflictoQueReportaElRepositorio() {
        when(monedas.exists(anyString())).thenReturn(Mono.just(true));
        when(cuentas.create(any())).thenReturn(Mono.error(new BadRequestException(HttpStatus.CONFLICT,
                ErrorCodes.DUPLICATE_RESOURCE, "Ya hay una cuenta con ese nombre", "name")));

        StepVerifier.create(useCase().create(AccountMother.USER_ID, AccountMother.altaEfectivo()))
                .expectErrorSatisfies(error -> assertThat(((BadRequestException) error).getHttpStatus())
                        .isEqualTo(HttpStatus.CONFLICT))
                .verify();
    }

    @Test
    void noConsultaNadaHastaQueAlguienSeSuscribe() {
        useCase().create(AccountMother.USER_ID, AccountMother.altaEfectivo());

        verifyNoInteractions(monedas, cuentas);
    }

    private void altaPosible() {
        when(monedas.exists(anyString())).thenReturn(Mono.just(true));
        when(cuentas.create(any())).thenReturn(Mono.just(AccountMother.tarjetaCreada()));
    }

    private static CreateAccountCommand alta(String nombre, String tipo, String moneda) {
        return new CreateAccountCommand(nombre, tipo, moneda, null, null, null, null, null, null);
    }

    private CreateAccountUseCase useCase() {
        return new CreateAccountUseCase(cuentas, monedas, RELOJ);
    }
}
