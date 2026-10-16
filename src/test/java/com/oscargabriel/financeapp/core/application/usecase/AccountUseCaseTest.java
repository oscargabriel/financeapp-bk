package com.oscargabriel.financeapp.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
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
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.Account;
import com.oscargabriel.financeapp.core.domain.model.AccountType;
import com.oscargabriel.financeapp.core.domain.model.CreateAccountCommand;
import com.oscargabriel.financeapp.core.domain.model.NewAccount;
import com.oscargabriel.financeapp.core.domain.model.UpdateAccountCommand;
import com.oscargabriel.financeapp.core.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.AccountRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.RecurrenceRepositoryPort;
import com.oscargabriel.financeapp.core.support.AccountMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AccountUseCaseTest {

    private static final Clock RELOJ = Clock.fixed(
            Instant.parse("2026-09-26T15:00:00Z"), ZoneId.of("America/Bogota"));

    @Mock
    private AccountQueryPort query;

    @Mock
    private SeriesAlDia alDia;

    @Mock
    private AccountRepositoryPort cuentas;

    @Mock
    private CurrencyQueryPort monedas;

    @Mock
    private RecurrenceRepositoryPort series;

    @Nested
    class Listar {

        @BeforeEach
        void seriesAlDia() {
            lenient().when(alDia.ponerAlDia(any())).thenReturn(Mono.empty());
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void pasaAlPuertoSiHayQueIncluirLasDesactivadas(boolean includeInactive) {
            when(query.findByUser(eq(AccountMother.USER_ID), anyBoolean())).thenReturn(Flux.empty());

            StepVerifier.create(useCase().list(AccountMother.USER_ID, includeInactive)).verifyComplete();

            verify(query).findByUser(AccountMother.USER_ID, includeInactive);
        }

        @Test
        void emiteLasCuentasEnElOrdenQueEntregaElPuerto() {
            when(query.findByUser(AccountMother.USER_ID, false)).thenReturn(
                    Flux.just(AccountMother.efectivo(), AccountMother.visa()));

            StepVerifier.create(useCase().list(AccountMother.USER_ID, false))
                    .assertNext(cuenta -> assertThat(cuenta.name()).isEqualTo("Efectivo"))
                    .assertNext(cuenta -> assertThat(cuenta.name()).isEqualTo("Visa"))
                    .verifyComplete();
        }

        /** El saldo incluye las ocurrencias atrasadas de las series sin fin: se leen despues de crearlas (FA-107). */
        @Test
        void poneAlDiaLasSeriesAntesDeLeerLasCuentas() {
            when(alDia.ponerAlDia(AccountMother.USER_ID)).thenReturn(Mono.error(new IllegalStateException("sin base")));

            StepVerifier.create(useCase().list(AccountMother.USER_ID, false)).verifyError(IllegalStateException.class);

            verifyNoInteractions(query);
        }
    }

    @Nested
    class Crear {

        @Captor
        private ArgumentCaptor<NewAccount> cuentaGuardada;

        @Test
        void guardaLaCuentaDelUsuarioConNombreYMonedaNormalizados() {
            altaPosible();

            StepVerifier.create(useCase().create(AccountMother.USER_ID, new CreateAccountCommand(
                            "  Billetera  ", "cash", " cop ", new BigDecimal("150000"), null, null, null, null, null, null)))
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
            assertThat(guardada.chargesSingleInstallmentInterest()).isNull();
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
                            new CreateAccountCommand("Billetera", "CASH", "COP", null, null, null, null, null, null, null)))
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
        void unaTarjetaSinLaOpcionNoCobraInteresAUnaCuota() {
            altaPosible();

            StepVerifier.create(useCase().create(AccountMother.USER_ID, AccountMother.altaTarjeta()))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(cuentas).create(cuentaGuardada.capture());
            assertThat(cuentaGuardada.getValue().chargesSingleInstallmentInterest()).isFalse();
        }

        @Test
        void unaTarjetaConservaLaOpcionDeInteresAUnaCuota() {
            altaPosible();

            StepVerifier.create(useCase().create(AccountMother.USER_ID, AccountMother.altaTarjetaConInteresAUnaCuota()))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(cuentas).create(cuentaGuardada.capture());
            assertThat(cuentaGuardada.getValue().chargesSingleInstallmentInterest()).isTrue();
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
            lenient().when(monedas.exists(anyString())).thenReturn(Mono.just(true));
            lenient().when(cuentas.create(any())).thenReturn(Mono.just(AccountMother.tarjetaCreada()));
        }

        private static CreateAccountCommand alta(String nombre, String tipo, String moneda) {
            return new CreateAccountCommand(nombre, tipo, moneda, null, null, null, null, null, null, null);
        }
    }

    @Nested
    class Actualizar {

        @Captor
        private ArgumentCaptor<Account> cuentaGuardada;

        @Test
        void aplicaNombreYSaldoInicialYConservaElResto() {
            guardada(AccountMother.efectivo());
            actualizacionPosible();

            StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                            new UpdateAccountCommand(" Bolsillo ", null, new BigDecimal("200000"), null, null, null, null, null, null)))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(cuentas).update(eq(AccountMother.USER_ID), cuentaGuardada.capture());
            assertThat(cuentaGuardada.getValue()).isEqualTo(new Account(AccountMother.EFECTIVO_ID, "Bolsillo",
                    AccountType.CASH, "COP", new BigDecimal("200000"), new BigDecimal("322500.0000"),
                    null, null, null, null, true));
        }

        @Test
        void aplicaCupoYDiasSobreUnaTarjetaYConservaLoQueVieneEnNull() {
            Account tarjeta = AccountMother.tarjetaCreada();
            guardada(tarjeta);
            actualizacionPosible();

            StepVerifier.create(useCase().update(AccountMother.USER_ID, tarjeta.id(),
                            new UpdateAccountCommand(null, null, null, new BigDecimal("4000000"), 25, null, null, null, null)))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(cuentas).update(eq(AccountMother.USER_ID), cuentaGuardada.capture());
            assertThat(cuentaGuardada.getValue()).isEqualTo(new Account(tarjeta.id(), "Mastercard",
                    AccountType.CREDIT, "COP", new BigDecimal("-200000.0000"), new BigDecimal("-200000.0000"),
                    new BigDecimal("4000000"), 25, 5, new BigDecimal("2.1500"), true));
        }

        @Test
        void aplicaLaTasaSobreUnaTarjeta() {
            Account tarjeta = AccountMother.tarjetaCreada();
            guardada(tarjeta);
            actualizacionPosible();

            StepVerifier.create(useCase().update(AccountMother.USER_ID, tarjeta.id(),
                            new UpdateAccountCommand(null, null, null, null, null, null, new BigDecimal("1.9"), null, null)))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(cuentas).update(eq(AccountMother.USER_ID), cuentaGuardada.capture());
            assertThat(cuentaGuardada.getValue().monthlyInterestRate()).isEqualByComparingTo("1.9");
            assertThat(cuentaGuardada.getValue().creditLimit()).isEqualTo(tarjeta.creditLimit());
        }

        @Test
        void aplicaLaOpcionDeInteresAUnaCuotaSobreUnaTarjeta() {
            Account tarjeta = AccountMother.tarjetaCreada();
            guardada(tarjeta);
            actualizacionPosible();

            StepVerifier.create(useCase().update(AccountMother.USER_ID, tarjeta.id(),
                            new UpdateAccountCommand(null, null, null, null, null, null, null, true, null)))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(cuentas).update(eq(AccountMother.USER_ID), cuentaGuardada.capture());
            assertThat(cuentaGuardada.getValue().chargesSingleInstallmentInterest()).isTrue();
            assertThat(cuentaGuardada.getValue().monthlyInterestRate()).isEqualTo(tarjeta.monthlyInterestRate());
        }

        @Test
        void conservaLaOpcionDeInteresAUnaCuotaSiElParcheNoLaTrae() {
            Account tarjeta = AccountMother.tarjetaConInteresAUnaCuota();
            guardada(tarjeta);
            actualizacionPosible();

            StepVerifier.create(useCase().update(AccountMother.USER_ID, tarjeta.id(),
                            new UpdateAccountCommand("Diners Club", null, null, null, null, null, null, null, null)))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(cuentas).update(eq(AccountMother.USER_ID), cuentaGuardada.capture());
            assertThat(cuentaGuardada.getValue().chargesSingleInstallmentInterest()).isTrue();
        }

        @Test
        void rechazaLaOpcionDeInteresAUnaCuotaSobreUnaCuentaQueNoEsDeCredito() {
            guardada(AccountMother.efectivo());

            StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                            new UpdateAccountCommand(null, null, null, null, null, null, null, false, null)))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                            "chargesSingleInstallmentInterest"))
                    .verify();

            verify(cuentas, never()).update(any(), any());
        }

        @Test
        void desactivaUnaCuentaConSaldoSinTocarElResto() {
            guardada(AccountMother.efectivo());
            conSeries(AccountMother.EFECTIVO_ID, 0L);
            actualizacionPosible();

            StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                            new UpdateAccountCommand(null, null, null, null, null, null, null, null, false)))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(cuentas).update(eq(AccountMother.USER_ID), cuentaGuardada.capture());
            assertThat(cuentaGuardada.getValue())
                    .isEqualTo(new Account(AccountMother.EFECTIVO_ID, "Efectivo", AccountType.CASH, "COP",
                            new BigDecimal("500000.0000"), AccountMother.efectivo().currentBalance(),
                            null, null, null, null, false));
        }

        @Test
        void rechazaDesactivarUnaCuentaConSeriesActivasSinGuardarNada() {
            guardada(AccountMother.efectivo());
            conSeries(AccountMother.EFECTIVO_ID, 1L);

            StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                            new UpdateAccountCommand("Vieja", null, null, null, null, null, null, null, false)))
                    .expectErrorSatisfies(error -> {
                        esError(error, HttpStatus.CONFLICT, ErrorCodes.RESOURCE_IN_USE, "isActive");
                        assertThat(((BadRequestException) error).getErrorResponse().getErrors().getFirst().getDescription())
                                .contains("1 serie activa");
                    })
                    .verify();

            verify(cuentas, never()).update(any(), any());
        }

        @Test
        void elRechazoDiceCuantasSeriesActivasHay() {
            guardada(AccountMother.efectivo());
            conSeries(AccountMother.EFECTIVO_ID, 3L);

            StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                            new UpdateAccountCommand(null, null, null, null, null, null, null, null, false)))
                    .expectErrorSatisfies(error -> assertThat(((BadRequestException) error).getErrorResponse()
                            .getErrors().getFirst().getDescription()).contains("3 series activas"))
                    .verify();
        }

        /** Pedir el estado que ya tiene no es desactivar: no hay nada que comprobar. */
        @Test
        void desactivarUnaCuentaYaDesactivadaNoConsultaLasSeries() {
            Account nequi = AccountMother.inactiva();
            guardada(nequi);
            actualizacionPosible();

            StepVerifier.create(useCase().update(AccountMother.USER_ID, nequi.id(),
                            new UpdateAccountCommand(null, null, null, null, null, null, null, null, false)))
                    .expectNextCount(1)
                    .verifyComplete();

            verifyNoInteractions(series);
        }

        @Test
        void unParcheQueNoDesactivaNoConsultaLasSeries() {
            guardada(AccountMother.efectivo());
            actualizacionPosible();

            StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                            new UpdateAccountCommand("Bolsillo", null, null, null, null, null, null, null, true)))
                    .expectNextCount(1)
                    .verifyComplete();

            verifyNoInteractions(series);
        }

        @Test
        void reactivaUnaCuentaDesactivada() {
            Account nequi = AccountMother.inactiva();
            guardada(nequi);
            actualizacionPosible();

            StepVerifier.create(useCase().update(AccountMother.USER_ID, nequi.id(),
                            new UpdateAccountCommand(null, null, null, null, null, null, null, null, true)))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(cuentas).update(eq(AccountMother.USER_ID), cuentaGuardada.capture());
            assertThat(cuentaGuardada.getValue().active()).isTrue();
        }

        @Test
        void elEstadoEnNullConservaElGuardado() {
            Account nequi = AccountMother.inactiva();
            guardada(nequi);
            actualizacionPosible();

            StepVerifier.create(useCase().update(AccountMother.USER_ID, nequi.id(),
                            new UpdateAccountCommand("Nequi vieja", null, null, null, null, null, null, null, null)))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(cuentas).update(eq(AccountMother.USER_ID), cuentaGuardada.capture());
            assertThat(cuentaGuardada.getValue().active()).isFalse();
        }

        @Test
        void devuelveLaCuentaComoLaDejoLaBase() {
            Account comoQuedo = new Account(AccountMother.EFECTIVO_ID, "Efectivo", AccountType.CASH, "COP",
                    new BigDecimal("600000.0000"), new BigDecimal("422500.0000"), null, null, null, null, true);
            guardada(AccountMother.efectivo());
            when(cuentas.update(any(), any())).thenReturn(Mono.just(comoQuedo));

            StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                            new UpdateAccountCommand(null, null, new BigDecimal("600000"), null, null, null, null, null, null)))
                    .assertNext(cuenta -> assertThat(cuenta).isEqualTo(comoQuedo))
                    .verifyComplete();
        }

        @Test
        void daNotFoundSobreElIdCuandoLaCuentaNoEstaEntreLasVivasDelUsuario() {
            when(cuentas.findActiveByIdAndUser(AccountMother.EFECTIVO_ID, AccountMother.USER_ID))
                    .thenReturn(Mono.empty());

            StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                            new UpdateAccountCommand("Bolsillo", null, null, null, null, null, null, null, null)))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "id"))
                    .verify();

            verify(cuentas, never()).update(any(), any());
        }

        @Test
        void rechazaLosCamposDeCreditoSobreUnaCuentaQueNoEsDeCreditoUnoPorCampo() {
            guardada(AccountMother.efectivo());

            StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                            new UpdateAccountCommand(null, "USD", null, new BigDecimal("1000000"), 3, 10,
                                    new BigDecimal("2"), null, null)))
                    .expectErrorSatisfies(error -> {
                        BadRequestException bre = (BadRequestException) error;
                        assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(bre.getErrorResponse().getErrors())
                                .extracting(ErrorDetail::getCode, ErrorDetail::getField)
                                .containsExactlyInAnyOrder(
                                        tuple(ErrorCodes.VALIDATION_ERROR.getCode(), "creditLimit"),
                                        tuple(ErrorCodes.VALIDATION_ERROR.getCode(), "statementDay"),
                                        tuple(ErrorCodes.VALIDATION_ERROR.getCode(), "paymentDueDay"),
                                        tuple(ErrorCodes.VALIDATION_ERROR.getCode(), "monthlyInterestRate"));
                    })
                    .verify();

            verifyNoInteractions(monedas);
            verify(cuentas, never()).hasTransactions(any());
            verify(cuentas, never()).update(any(), any());
        }

        @Test
        void rechazaLaTasaSobreUnaCuentaQueNoEsDeCredito() {
            guardada(AccountMother.efectivo());

            StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                            new UpdateAccountCommand(null, null, null, null, null, null, new BigDecimal("2"), null, null)))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                            "monthlyInterestRate"))
                    .verify();

            verify(cuentas, never()).update(any(), any());
        }

        @Test
        void unaMonedaNuevaQueNoEstaActivaDaBadRequestSinConsultarMovimientos() {
            guardada(AccountMother.efectivo());
            when(monedas.exists("XYZ")).thenReturn(Mono.just(false));

            StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                            new UpdateAccountCommand(null, " xyz ", null, null, null, null, null, null, null)))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                            "currencyCode"))
                    .verify();

            verify(cuentas, never()).hasTransactions(any());
            verify(cuentas, never()).update(any(), any());
        }

        /** Con el estado en el parche: el 409 tampoco desactiva, porque no hay ninguna escritura. */
        @Test
        void rechazaCambiarLaMonedaDeUnaCuentaConMovimientosSinGuardarNada() {
            guardada(AccountMother.efectivo());
            when(monedas.exists("USD")).thenReturn(Mono.just(true));
            when(cuentas.hasTransactions(AccountMother.EFECTIVO_ID)).thenReturn(Mono.just(true));

            StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                            new UpdateAccountCommand("Otro", "USD", null, null, null, null, null, null, false)))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, ErrorCodes.RESOURCE_IN_USE,
                            "currencyCode"))
                    .verify();

            verify(cuentas, never()).update(any(), any());
        }

        @Test
        void sinMovimientosCambiaLaMonedaEnMayusculas() {
            guardada(AccountMother.efectivo());
            when(monedas.exists("USD")).thenReturn(Mono.just(true));
            when(cuentas.hasTransactions(AccountMother.EFECTIVO_ID)).thenReturn(Mono.just(false));
            actualizacionPosible();

            StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                            new UpdateAccountCommand(null, " usd ", null, null, null, null, null, null, null)))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(cuentas).update(eq(AccountMother.USER_ID), cuentaGuardada.capture());
            assertThat(cuentaGuardada.getValue().currencyCode()).isEqualTo("USD");
        }

        /** Sin stubs de monedas ni de hasTransactions: el modo estricto de Mockito falla si se llaman. */
        @Test
        void laMismaMonedaEnOtraCajaNoConsultaElCatalogoNiLosMovimientos() {
            guardada(AccountMother.efectivo());
            actualizacionPosible();

            StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                            new UpdateAccountCommand(null, "cop", null, null, null, null, null, null, null)))
                    .expectNextCount(1)
                    .verifyComplete();

            verifyNoInteractions(monedas);
            verify(cuentas, never()).hasTransactions(any());
        }

        @Test
        void propagaElConflictoDeNombreQueReportaElRepositorio() {
            guardada(AccountMother.efectivo());
            when(cuentas.update(any(), any())).thenReturn(Mono.error(new BadRequestException(HttpStatus.CONFLICT,
                    ErrorCodes.DUPLICATE_RESOURCE, "Ya hay una cuenta con ese nombre", "name")));

            StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                            new UpdateAccountCommand("Visa", null, null, null, null, null, null, null, null)))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_RESOURCE,
                            "name"))
                    .verify();
        }

        /** Borrada entre la lectura y la escritura. */
        @Test
        void daNotFoundCuandoElUpdateNoEncuentraLaFila() {
            guardada(AccountMother.efectivo());
            when(cuentas.update(any(), any())).thenReturn(Mono.empty());

            StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                            new UpdateAccountCommand("Bolsillo", null, null, null, null, null, null, null, null)))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "id"))
                    .verify();
        }

        @Test
        void unSaldoInicialNegativoEnUnaCuentaQueNoEsDeCreditoEsUn400() {
            guardada(AccountMother.efectivo());

            StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                            new UpdateAccountCommand(null, null, new BigDecimal("-1"), null, null, null, null, null, null)))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                            "initialBalance"))
                    .verify();

            verify(cuentas, never()).update(any(), any());
        }

        @Test
        void bajarElSaldoInicialPorDebajoDeLoGastadoEsUn409SinCambiarNada() {
            guardada(AccountMother.efectivo());

            StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.EFECTIVO_ID,
                            new UpdateAccountCommand("Bolsillo", null, new BigDecimal("100000"), null, null, null, null, null, null)))
                    .expectErrorSatisfies(error -> {
                        esError(error, HttpStatus.CONFLICT, ErrorCodes.INSUFFICIENT_FUNDS, "initialBalance");
                        assertThat(((BadRequestException) error).getErrorResponse().getErrors().get(0).getDescription())
                                .isEqualTo("Saldo insuficiente en Efectivo: quedaría en -77500 COP");
                    })
                    .verify();

            verify(cuentas, never()).update(any(), any());
        }

        @Test
        void unaTarjetaPuedeBajarSuSaldoInicialANegativo() {
            guardada(AccountMother.tarjetaCreada());
            actualizacionPosible();

            StepVerifier.create(useCase().update(AccountMother.USER_ID, AccountMother.tarjetaCreada().id(),
                            new UpdateAccountCommand(null, null, new BigDecimal("-900000"), null, null, null, null, null, null)))
                    .expectNextCount(1)
                    .verifyComplete();
        }

        private void guardada(Account cuenta) {
            when(cuentas.findActiveByIdAndUser(cuenta.id(), AccountMother.USER_ID)).thenReturn(Mono.just(cuenta));
        }

        private void conSeries(UUID cuenta, long cuantas) {
            when(series.countActiveByAccount(cuenta, AccountMother.USER_ID)).thenReturn(Mono.just(cuantas));
        }

        private void actualizacionPosible() {
            when(cuentas.update(any(), any())).thenAnswer(invocacion -> Mono.just(invocacion.getArgument(1)));
        }
    }

    @Nested
    class Borrar {

        @Test
        void borraUnaCuentaEnCeroAunqueTengaHistoria() {
            guardada(AccountMother.cajaChicaEnCero());
            when(cuentas.softDelete(AccountMother.CAJA_CHICA_ID, AccountMother.USER_ID)).thenReturn(Mono.just(true));

            StepVerifier.create(useCase().delete(AccountMother.USER_ID, AccountMother.CAJA_CHICA_ID))
                    .verifyComplete();

            verify(cuentas).softDelete(AccountMother.CAJA_CHICA_ID, AccountMother.USER_ID);
        }

        @Test
        void unaCuentaDesactivadaEnCeroTambienSeBorra() {
            Account inactiva = AccountMother.inactivaEnCero();
            guardada(inactiva);
            when(cuentas.softDelete(inactiva.id(), AccountMother.USER_ID)).thenReturn(Mono.just(true));

            StepVerifier.create(useCase().delete(AccountMother.USER_ID, inactiva.id()))
                    .verifyComplete();
        }

        @Test
        void daNotFoundSobreElIdCuandoLaCuentaNoEstaEntreLasVivasDelUsuario() {
            when(cuentas.findActiveByIdAndUser(AccountMother.CAJA_CHICA_ID, AccountMother.USER_ID))
                    .thenReturn(Mono.empty());

            StepVerifier.create(useCase().delete(AccountMother.USER_ID, AccountMother.CAJA_CHICA_ID))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "id"))
                    .verify();

            verify(cuentas, never()).softDelete(any(), any());
        }

        @Test
        void rechazaUnaCuentaConSaldoAFavorSinBorrarla() {
            guardada(AccountMother.efectivo());

            StepVerifier.create(useCase().delete(AccountMother.USER_ID, AccountMother.EFECTIVO_ID))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, ErrorCodes.RESOURCE_IN_USE,
                            "currentBalance"))
                    .verify();

            verify(cuentas, never()).softDelete(any(), any());
        }

        @Test
        void rechazaUnaTarjetaConDeudaSinBorrarla() {
            guardada(AccountMother.visa());

            StepVerifier.create(useCase().delete(AccountMother.USER_ID, AccountMother.VISA_ID))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.CONFLICT, ErrorCodes.RESOURCE_IN_USE,
                            "currentBalance"))
                    .verify();

            verify(cuentas, never()).softDelete(any(), any());
        }

        @Test
        void rechazaUnaCuentaEnCeroConProgramadosSobreElId() {
            guardada(AccountMother.cajaChicaEnCero());
            conProgramados(AccountMother.CAJA_CHICA_ID, 1L);

            StepVerifier.create(useCase().delete(AccountMother.USER_ID, AccountMother.CAJA_CHICA_ID))
                    .expectErrorSatisfies(error -> {
                        esError(error, HttpStatus.CONFLICT, ErrorCodes.RESOURCE_IN_USE, "id");
                        assertThat(((BadRequestException) error).getErrorResponse().getErrors().getFirst().getDescription())
                                .contains("1 movimiento programado");
                    })
                    .verify();

            verify(cuentas, never()).softDelete(any(), any());
        }

        @Test
        void conSaldoYProgramadosReportaLosDosErrores() {
            guardada(AccountMother.efectivo());
            conProgramados(AccountMother.EFECTIVO_ID, 3L);

            StepVerifier.create(useCase().delete(AccountMother.USER_ID, AccountMother.EFECTIVO_ID))
                    .expectErrorSatisfies(error -> {
                        BadRequestException bre = (BadRequestException) error;
                        assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(bre.getErrorResponse().getErrors())
                                .extracting(ErrorDetail::getCode, ErrorDetail::getField)
                                .containsExactlyInAnyOrder(
                                        tuple(ErrorCodes.RESOURCE_IN_USE.getCode(), "currentBalance"),
                                        tuple(ErrorCodes.RESOURCE_IN_USE.getCode(), "id"));
                    })
                    .verify();

            verify(cuentas, never()).softDelete(any(), any());
        }

        /** Toda serie sin fin activa tiene una programada: los dos van en el mismo error de id. */
        @Test
        void conSerieYProgramadoDaUnSoloErrorEnElIdQueMencionaLosDos() {
            guardada(AccountMother.cajaChicaEnCero());
            conProgramados(AccountMother.CAJA_CHICA_ID, 1L);
            conSeries(AccountMother.CAJA_CHICA_ID, 1L);

            StepVerifier.create(useCase().delete(AccountMother.USER_ID, AccountMother.CAJA_CHICA_ID))
                    .expectErrorSatisfies(error -> {
                        esError(error, HttpStatus.CONFLICT, ErrorCodes.RESOURCE_IN_USE, "id");
                        assertThat(descripcion(error)).contains("1 serie activa", "1 movimiento programado");
                    })
                    .verify();

            verify(cuentas, never()).softDelete(any(), any());
        }

        /** El hueco de FA-115: la programada de la serie se borro a mano y la serie sigue activa. */
        @Test
        void conSerieYSinProgramadosRechazaPorLaSerie() {
            guardada(AccountMother.cajaChicaEnCero());
            conSeries(AccountMother.CAJA_CHICA_ID, 2L);

            StepVerifier.create(useCase().delete(AccountMother.USER_ID, AccountMother.CAJA_CHICA_ID))
                    .expectErrorSatisfies(error -> {
                        esError(error, HttpStatus.CONFLICT, ErrorCodes.RESOURCE_IN_USE, "id");
                        assertThat(descripcion(error)).contains("2 series activas").doesNotContain("programado");
                    })
                    .verify();

            verify(cuentas, never()).softDelete(any(), any());
        }

        @Test
        void sinSeriesElMensajeDeLosProgramadosNoCambia() {
            guardada(AccountMother.cajaChicaEnCero());
            conProgramados(AccountMother.CAJA_CHICA_ID, 2L);

            StepVerifier.create(useCase().delete(AccountMother.USER_ID, AccountMother.CAJA_CHICA_ID))
                    .expectErrorSatisfies(error -> assertThat(descripcion(error)).isEqualTo(
                            "La cuenta tiene 2 movimientos programados: cancela la serie o la compra en cuotas, o borra"
                                    + " esos movimientos, antes de borrarla"))
                    .verify();
        }

        @Test
        void conSaldoYUnaSerieReportaLosDosErrores() {
            guardada(AccountMother.efectivo());
            conSeries(AccountMother.EFECTIVO_ID, 1L);

            StepVerifier.create(useCase().delete(AccountMother.USER_ID, AccountMother.EFECTIVO_ID))
                    .expectErrorSatisfies(error -> assertThat(((BadRequestException) error).getErrorResponse().getErrors())
                            .extracting(ErrorDetail::getCode, ErrorDetail::getField)
                            .containsExactlyInAnyOrder(
                                    tuple(ErrorCodes.RESOURCE_IN_USE.getCode(), "currentBalance"),
                                    tuple(ErrorCodes.RESOURCE_IN_USE.getCode(), "id")))
                    .verify();

            verify(cuentas, never()).softDelete(any(), any());
        }

        /** Borrada entre la lectura y el UPDATE. */
        @Test
        void daNotFoundCuandoElUpdateNoEncuentraLaFila() {
            guardada(AccountMother.cajaChicaEnCero());
            when(cuentas.softDelete(AccountMother.CAJA_CHICA_ID, AccountMother.USER_ID)).thenReturn(Mono.just(false));

            StepVerifier.create(useCase().delete(AccountMother.USER_ID, AccountMother.CAJA_CHICA_ID))
                    .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "id"))
                    .verify();
        }

        private void guardada(Account cuenta) {
            UUID id = cuenta.id();
            when(cuentas.findActiveByIdAndUser(id, AccountMother.USER_ID)).thenReturn(Mono.just(cuenta));
            lenient().when(cuentas.countScheduled(id)).thenReturn(Mono.just(0L));
            lenient().when(series.countActiveByAccount(id, AccountMother.USER_ID)).thenReturn(Mono.just(0L));
        }

        private void conProgramados(UUID id, long cuantos) {
            when(cuentas.countScheduled(id)).thenReturn(Mono.just(cuantos));
        }

        private void conSeries(UUID id, long cuantas) {
            when(series.countActiveByAccount(id, AccountMother.USER_ID)).thenReturn(Mono.just(cuantas));
        }

        private static String descripcion(Throwable error) {
            return ((BadRequestException) error).getErrorResponse().getErrors().stream()
                    .filter(detalle -> "id".equals(detalle.getField()))
                    .findFirst().orElseThrow().getDescription();
        }
    }

    private AccountUseCase useCase() {
        return new AccountUseCase(query, alDia, cuentas, monedas, series, RELOJ);
    }

    private static void esError(Throwable error, HttpStatus status, ErrorCodes codigo, String campo) {
        BadRequestException bre = (BadRequestException) error;
        assertThat(bre.getHttpStatus()).isEqualTo(status);
        assertThat(bre.getErrorResponse().getErrors())
                .extracting(ErrorDetail::getCode, ErrorDetail::getField)
                .containsExactly(tuple(codigo.getCode(), campo));
    }
}
