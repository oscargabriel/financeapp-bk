package com.oscargabriel.financeapp.core.application.usecase;

import static com.oscargabriel.financeapp.core.support.AccountMother.BOLSILLO_ID;
import static com.oscargabriel.financeapp.core.support.AccountMother.NOMINA_ID;
import static com.oscargabriel.financeapp.core.support.AccountMother.VISA_ID;
import static com.oscargabriel.financeapp.core.support.TransactionMother.conEstado;
import static com.oscargabriel.financeapp.core.support.TransactionMother.conFecha;
import static com.oscargabriel.financeapp.core.support.TransactionMother.en;
import static com.oscargabriel.financeapp.core.support.TransactionMother.haciaOtraMoneda;
import static com.oscargabriel.financeapp.core.support.TransactionMother.unGastoGuardado;
import static com.oscargabriel.financeapp.core.support.TransactionMother.unIngresoGuardado;
import static com.oscargabriel.financeapp.core.support.TransactionMother.unaTransferenciaGuardada;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.Account;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.core.support.AccountMother;
import com.oscargabriel.financeapp.compartido.support.RelojFijo;

class SaldoNoNegativoTest {

    private static final Instant AHORA = RelojFijo.AHORA;

    private static SaldoNoNegativo con(Account... cuentas) {
        return new SaldoNoNegativo(Stream.of(cuentas).collect(Collectors.toMap(Account::id, Function.identity())));
    }

    private static Map<UUID, BigDecimal> cambio(UUID cuenta, String monto) {
        return Map.of(cuenta, new BigDecimal(monto));
    }

    @Nested
    class Efecto {

        @Test
        void unGastoConfirmadoRestaSuMontoDeLaCuenta() {
            assertThat(SaldoNoNegativo.efecto(en(unGastoGuardado(), NOMINA_ID, null, "50000"), AHORA))
                    .containsExactly(entry(NOMINA_ID, new BigDecimal("-50000")));
        }

        @Test
        void unIngresoConfirmadoSumaSuMonto() {
            assertThat(SaldoNoNegativo.efecto(en(unIngresoGuardado(), NOMINA_ID, null, "80000"), AHORA))
                    .containsExactly(entry(NOMINA_ID, new BigDecimal("80000")));
        }

        @Test
        void unaTransferenciaSacaDelOrigenYLlevaElMismoMontoAlDestino() {
            assertThat(SaldoNoNegativo.efecto(en(unaTransferenciaGuardada(), NOMINA_ID, BOLSILLO_ID, "70000"), AHORA))
                    .containsOnly(entry(NOMINA_ID, new BigDecimal("-70000")), entry(BOLSILLO_ID, new BigDecimal("70000")));
        }

        @Test
        void unaTransferenciaEntreMonedasLlevaAlDestinoSuMontoDeDestino() {
            assertThat(SaldoNoNegativo.efecto(haciaOtraMoneda(en(unaTransferenciaGuardada(), NOMINA_ID, null, "400000"),
                    BOLSILLO_ID, new BigDecimal("100")), AHORA))
                    .containsOnly(entry(NOMINA_ID, new BigDecimal("-400000")), entry(BOLSILLO_ID, new BigDecimal("100")));
        }

        @Test
        void unPendienteNoTieneEfecto() {
            assertThat(SaldoNoNegativo.efecto(conEstado(unGastoGuardado(), TransactionStatus.PENDING), AHORA)).isEmpty();
        }

        @Test
        void unProgramadoNoTieneEfecto() {
            assertThat(SaldoNoNegativo.efecto(conFecha(unGastoGuardado(), RelojFijo.DESPUES), AHORA)).isEmpty();
        }

        @Test
        void laDiferenciaRestaElEfectoAnteriorCuentaPorCuenta() {
            assertThat(SaldoNoNegativo.diferencia(cambio(NOMINA_ID, "-60000"),
                    Map.of(NOMINA_ID, new BigDecimal("-30000"), BOLSILLO_ID, new BigDecimal("-5000"))))
                    .containsOnly(entry(NOMINA_ID, new BigDecimal("-30000")), entry(BOLSILLO_ID, new BigDecimal("5000")));
        }
    }

    @Nested
    class Exigir {

        @Test
        void rechazaCon409ElCambioQueDejaLaCuentaEnNegativo() {
            BadRequestException error = catchThrowableOfType(BadRequestException.class,
                    () -> con(AccountMother.nomina("100000")).exigir(cambio(NOMINA_ID, "-150000"), "amount"));

            assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(error.getErrorResponse().getErrors())
                    .extracting(ErrorDetail::getCode, ErrorDetail::getDescription, ErrorDetail::getField)
                    .containsExactly(tuple(ErrorCodes.INSUFFICIENT_FUNDS.getCode(),
                            "Saldo insuficiente en Nómina: quedaría en -50000 COP", "amount"));
        }

        @Test
        void admiteDejarLaCuentaExactamenteEnCero() {
            assertThatCode(() -> con(AccountMother.nomina("100000")).exigir(cambio(NOMINA_ID, "-100000"), "amount"))
                    .doesNotThrowAnyException();
        }

        @Test
        void unaTarjetaDeCreditoNoTieneTope() {
            assertThatCode(() -> con(AccountMother.visa()).exigir(cambio(VISA_ID, "-9000000"), "amount"))
                    .doesNotThrowAnyException();
        }

        @Test
        void unaCuentaYaNegativaRecibeDineroAunqueNoSalgaDeCero() {
            assertThatCode(() -> con(AccountMother.bolsillo("-30000")).exigir(cambio(BOLSILLO_ID, "10000"), "amount"))
                    .doesNotThrowAnyException();
        }

        @Test
        void unaCuentaYaNegativaNoAceptaSalidas() {
            BadRequestException error = catchThrowableOfType(BadRequestException.class,
                    () -> con(AccountMother.bolsillo("-20000")).exigir(cambio(BOLSILLO_ID, "-1000"), "id"));

            assertThat(error.getErrorResponse().getErrors())
                    .extracting(ErrorDetail::getField, ErrorDetail::getDescription)
                    .containsExactly(tuple("id", "Saldo insuficiente en Bolsillo: quedaría en -21000 COP"));
        }

        @Test
        void unErrorPorCadaCuentaQueQuedaEnNegativo() {
            BadRequestException error = catchThrowableOfType(BadRequestException.class,
                    () -> con(AccountMother.nomina("10000"), AccountMother.bolsillo("5000")).exigir(
                            Map.of(NOMINA_ID, new BigDecimal("-20000"), BOLSILLO_ID, new BigDecimal("-6000.5")),
                            "amount"));

            assertThat(error.getErrorResponse().getErrors())
                    .extracting(ErrorDetail::getDescription)
                    .containsExactlyInAnyOrder("Saldo insuficiente en Nómina: quedaría en -10000 COP",
                            "Saldo insuficiente en Bolsillo: quedaría en -1000.5 COP");
        }

        @Test
        void ignoraLasCuentasQueNoConoce() {
            assertThatCode(() -> con().exigir(cambio(NOMINA_ID, "-1"), "amount")).doesNotThrowAnyException();
        }
    }

    @Nested
    class Lote {

        private static List<ErrorDetail> errores(SaldoNoNegativo regla, List<Transaction> lote) {
            BadRequestException error = catchThrowableOfType(BadRequestException.class,
                    () -> regla.exigirLote(lote, AHORA));
            assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
            return error.getErrorResponse().getErrors();
        }

        @Test
        void unIngresoDelMismoLoteCompensaUnGasto() {
            assertThatCode(() -> con(AccountMother.nomina("100000")).exigirLote(List.of(
                    en(unGastoGuardado(), NOMINA_ID, null, "150000"),
                    en(unIngresoGuardado(), NOMINA_ID, null, "80000")), AHORA))
                    .doesNotThrowAnyException();
        }

        @Test
        void elErrorVaEnElElementoQueCruzaElCero() {
            assertThat(errores(con(AccountMother.nomina("100000")), List.of(
                    en(unGastoGuardado(), NOMINA_ID, null, "60000"),
                    en(unGastoGuardado(), NOMINA_ID, null, "70000"),
                    en(unGastoGuardado(), NOMINA_ID, null, "10000"))))
                    .extracting(ErrorDetail::getCode, ErrorDetail::getField, ErrorDetail::getDescription)
                    .containsExactly(tuple(ErrorCodes.INSUFFICIENT_FUNDS.getCode(), "[1].amount",
                            "Saldo insuficiente en Nómina: quedaría en -40000 COP"));
        }

        @Test
        void unErrorPorCuentaEnElOrdenDelLote() {
            assertThat(errores(con(AccountMother.nomina("100000"), AccountMother.bolsillo("0")), List.of(
                    en(unaTransferenciaGuardada(), NOMINA_ID, VISA_ID, "120000"),
                    en(unIngresoGuardado(), NOMINA_ID, null, "1000"),
                    en(unGastoGuardado(), BOLSILLO_ID, null, "5000"))))
                    .extracting(ErrorDetail::getField)
                    .containsExactly("[0].amount", "[2].amount");
        }

        @Test
        void losProgramadosYLosPendientesNoCuentan() {
            assertThatCode(() -> con(AccountMother.nomina("100000")).exigirLote(List.of(
                    conFecha(en(unGastoGuardado(), NOMINA_ID, null, "150000"), RelojFijo.DESPUES),
                    conEstado(en(unGastoGuardado(), NOMINA_ID, null, "150000"), TransactionStatus.PENDING)), AHORA))
                    .doesNotThrowAnyException();
        }

        @Test
        void unaCuentaYaNegativaQueRecibeMasDeLoQueGastaPasa() {
            assertThatCode(() -> con(AccountMother.bolsillo("-30000")).exigirLote(List.of(
                    en(unGastoGuardado(), BOLSILLO_ID, null, "5000"),
                    en(unIngresoGuardado(), BOLSILLO_ID, null, "10000")), AHORA))
                    .doesNotThrowAnyException();
        }
    }
}
