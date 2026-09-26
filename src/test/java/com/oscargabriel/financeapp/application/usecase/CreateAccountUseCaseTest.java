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
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
                        "  Billetera  ", "cash", " cop ", new BigDecimal("150000"), null, null, null, null)))
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
                        new CreateAccountCommand("Billetera", "CASH", "COP", null, null, null, null, null)))
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
    }

    @Test
    void devuelveLaCuentaComoLaDejoLaBase() {
        altaPosible();

        StepVerifier.create(useCase().create(AccountMother.USER_ID, AccountMother.altaTarjeta()))
                .assertNext(cuenta -> assertThat(cuenta).isEqualTo(AccountMother.tarjetaCreada()))
                .verifyComplete();
    }

    static Stream<Arguments> unCampoInvalido() {
        return Stream.of(
                Arguments.of("sin nombre", alta(null, "CASH", "COP"), "name"),
                Arguments.of("nombre en blanco", alta("   ", "CASH", "COP"), "name"),
                Arguments.of("nombre de 81", alta("x".repeat(81), "CASH", "COP"), "name"),
                Arguments.of("sin tipo", alta("Billetera", null, "COP"), "type"),
                Arguments.of("tipo desconocido", alta("Billetera", "WALLET", "COP"), "type"),
                Arguments.of("sin moneda", alta("Billetera", "CASH", null), "currencyCode"),
                Arguments.of("moneda de dos letras", alta("Billetera", "CASH", "CO"), "currencyCode"),
                Arguments.of("moneda con digito", alta("Billetera", "CASH", "C0P"), "currencyCode"),
                Arguments.of("saldo con cinco decimales", new CreateAccountCommand("Billetera", "CASH", "COP",
                        new BigDecimal("1.00001"), null, null, null, null), "initialBalance"),
                Arguments.of("saldo que desborda NUMERIC(18,4)", new CreateAccountCommand("Billetera", "CASH",
                        "COP", new BigDecimal("100000000000000"), null, null, null, null), "initialBalance"),
                Arguments.of("limite en cero", tarjeta(BigDecimal.ZERO, 20, 5), "creditLimit"),
                Arguments.of("limite negativo", tarjeta(new BigDecimal("-1"), 20, 5), "creditLimit"),
                Arguments.of("limite con cinco decimales", tarjeta(new BigDecimal("0.00001"), 20, 5),
                        "creditLimit"),
                Arguments.of("dia de corte 0", tarjeta(new BigDecimal("1000"), 0, 5), "statementDay"),
                Arguments.of("dia de corte 32", tarjeta(new BigDecimal("1000"), 32, 5), "statementDay"),
                Arguments.of("dia de pago 0", tarjeta(new BigDecimal("1000"), 20, 0), "paymentDueDay"),
                Arguments.of("dia de pago 32", tarjeta(new BigDecimal("1000"), 20, 32), "paymentDueDay"),
                Arguments.of("limite en un debito", new CreateAccountCommand("Debito", "DEBIT", "COP", null,
                        new BigDecimal("1000"), null, null, null), "creditLimit"),
                Arguments.of("dia de corte en un debito", new CreateAccountCommand("Debito", "DEBIT", "COP",
                        null, null, 15, null, null), "statementDay"),
                Arguments.of("dia de pago en un debito", new CreateAccountCommand("Debito", "DEBIT", "COP",
                        null, null, null, 5, null), "paymentDueDay"),
                Arguments.of("saldo vigente en el cuerpo", new CreateAccountCommand("Billetera", "CASH", "COP",
                        BigDecimal.TEN, null, null, null, new BigDecimal("999999")), "currentBalance"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unCampoInvalido")
    void rechazaElCampoInvalidoSinTocarLaBase(String caso, CreateAccountCommand command, String campo) {
        StepVerifier.create(useCase().create(AccountMother.USER_ID, command))
                .expectErrorSatisfies(error -> {
                    BadRequestException bre = (BadRequestException) error;
                    assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(bre.getErrorResponse().getErrors())
                            .extracting(ErrorDetail::getField)
                            .containsExactly(campo);
                    assertThat(bre.getErrorResponse().getErrors())
                            .extracting(ErrorDetail::getCode)
                            .containsOnly(ErrorCodes.VALIDATION_ERROR.getCode());
                })
                .verify();

        verifyNoInteractions(monedas, cuentas);
    }

    @Test
    void reportaTodosLosCamposInvalidosEnUnaSolaRespuesta() {
        CreateAccountCommand command = new CreateAccountCommand(" ", "DEBIT", "pesos", null,
                new BigDecimal("1000"), 15, 40, BigDecimal.ONE);

        StepVerifier.create(useCase().create(AccountMother.USER_ID, command))
                .expectErrorSatisfies(error -> assertThat(((BadRequestException) error)
                        .getErrorResponse().getErrors())
                        .extracting(ErrorDetail::getField)
                        .containsExactlyInAnyOrder("name", "currencyCode", "creditLimit", "statementDay",
                                "paymentDueDay", "currentBalance"))
                .verify();

        verifyNoInteractions(monedas, cuentas);
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
    void noValidaNadaHastaQueAlguienSeSuscribe() {
        useCase().create(AccountMother.USER_ID, alta(null, null, null));

        verifyNoInteractions(monedas, cuentas);
    }

    private void altaPosible() {
        when(monedas.exists(anyString())).thenReturn(Mono.just(true));
        when(cuentas.create(any())).thenReturn(Mono.just(AccountMother.tarjetaCreada()));
    }

    private static CreateAccountCommand alta(String nombre, String tipo, String moneda) {
        return new CreateAccountCommand(nombre, tipo, moneda, null, null, null, null, null);
    }

    private static CreateAccountCommand tarjeta(BigDecimal limite, Integer corte, Integer pago) {
        return new CreateAccountCommand("Tarjeta", "CREDIT", "COP", null, limite, corte, pago, null);
    }

    private CreateAccountUseCase useCase() {
        return new CreateAccountUseCase(cuentas, monedas, RELOJ);
    }
}
