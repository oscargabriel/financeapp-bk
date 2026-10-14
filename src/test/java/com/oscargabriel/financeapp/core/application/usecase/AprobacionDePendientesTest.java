package com.oscargabriel.financeapp.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.compartido.support.RelojFijo;
import com.oscargabriel.financeapp.core.domain.model.Account;
import com.oscargabriel.financeapp.core.domain.model.Category;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.core.support.TransactionMother;

class AprobacionDePendientesTest {

    private static final Map<UUID, Account> CUENTAS = TransactionMother.cuentasDelUsuario().stream()
            .collect(Collectors.toMap(Account::id, Function.identity()));

    private static final Map<UUID, Category> CATEGORIAS = TransactionMother.categoriasDelUsuario().stream()
            .collect(Collectors.toMap(Category::id, Function.identity()));

    private final AprobacionDePendientes aprobacion = new AprobacionDePendientes(CUENTAS, CATEGORIAS, RelojFijo.AHORA);

    @Test
    void unPendienteConCuentaActivaCategoriaVivaYSaldoSeAprueba() {
        assertThatCode(() -> aprobacion.exigir(gasto(TransactionMother.ORIGEN_ID, "50000")))
                .doesNotThrowAnyException();
    }

    @Test
    void unaCuentaDesactivadaEsUnConflictoSobreLaCuenta() {
        assertThatThrownBy(() -> aprobacion.exigir(gasto(TransactionMother.INACTIVA_ID, "1000")))
                .satisfies(e -> errores(e, HttpStatus.CONFLICT, tuple("accountId", ErrorCodes.INVALID_STATE.getCode())));
    }

    @Test
    void unaCategoriaBorradaEsUnConflictoSobreLaCategoria() {
        Transaction pendiente = conCategoria(gasto(TransactionMother.ORIGEN_ID, "1000"), UUID.randomUUID());

        assertThatThrownBy(() -> aprobacion.exigir(pendiente))
                .satisfies(e -> errores(e, HttpStatus.CONFLICT, tuple("categoryId", ErrorCodes.INVALID_STATE.getCode())));
    }

    @Test
    void unaCategoriaQueNoAplicaAlTipoEsUnConflictoSobreLaCategoria() {
        Transaction pendiente = conCategoria(gasto(TransactionMother.ORIGEN_ID, "1000"), TransactionMother.SALARIO_ID);

        assertThatThrownBy(() -> aprobacion.exigir(pendiente))
                .satisfies(e -> errores(e, HttpStatus.CONFLICT, tuple("categoryId", ErrorCodes.INVALID_STATE.getCode())));
    }

    @Test
    void unaTransferenciaNoLlevaCategoriaQueValidar() {
        Transaction pendiente = TransactionMother.unaTransferenciaPendienteEntre(TransactionMother.ORIGEN_ID,
                TransactionMother.DESTINO_ID);

        assertThatCode(() -> aprobacion.exigir(pendiente)).doesNotThrowAnyException();
    }

    @Test
    void conLaCuentaDesactivadaYSinSaldoSoloSalenLosErroresDeEstado() {
        Transaction pendiente = conCategoria(gasto(TransactionMother.INACTIVA_ID, "900000"), UUID.randomUUID());

        assertThatThrownBy(() -> aprobacion.exigir(pendiente))
                .satisfies(e -> errores(e, HttpStatus.CONFLICT,
                        tuple("accountId", ErrorCodes.INVALID_STATE.getCode()),
                        tuple("categoryId", ErrorCodes.INVALID_STATE.getCode())));
    }

    @Test
    void sinSaldoEsUnConflictoSobreElMonto() {
        assertThatThrownBy(() -> aprobacion.exigir(gasto(TransactionMother.ORIGEN_ID, "1500000")))
                .satisfies(e -> errores(e, HttpStatus.CONFLICT, tuple("amount", ErrorCodes.INSUFFICIENT_FUNDS.getCode())));
    }

    @Test
    void enGrupoCadaErrorDeEstadoLlevaElIndiceDelPendiente() {
        List<Transaction> grupo = List.of(gasto(TransactionMother.ORIGEN_ID, "1000"),
                conCategoria(gasto(TransactionMother.INACTIVA_ID, "1000"), UUID.randomUUID()));

        assertThatThrownBy(() -> aprobacion.exigirGrupo(grupo))
                .satisfies(e -> errores(e, HttpStatus.CONFLICT,
                        tuple("[1].accountId", ErrorCodes.INVALID_STATE.getCode()),
                        tuple("[1].categoryId", ErrorCodes.INVALID_STATE.getCode())));
    }

    @Test
    void enGrupoElSaldoSeValidaSobreElEfectoConjunto() {
        List<Transaction> grupo = List.of(gasto(TransactionMother.ORIGEN_ID, "600000"),
                gasto(TransactionMother.ORIGEN_ID, "600000"));

        assertThatThrownBy(() -> aprobacion.exigirGrupo(grupo))
                .satisfies(e -> errores(e, HttpStatus.CONFLICT,
                        tuple("[1].amount", ErrorCodes.INSUFFICIENT_FUNDS.getCode())));
    }

    @Test
    void enGrupoUnIngresoCompensaUnGasto() {
        Transaction ingreso = TransactionMother.conEstado(TransactionMother.en(TransactionMother.unIngresoGuardado(),
                TransactionMother.ORIGEN_ID, null, "600000"), TransactionStatus.PENDING);
        List<Transaction> grupo = List.of(gasto(TransactionMother.ORIGEN_ID, "1500000"), ingreso);

        assertThatCode(() -> aprobacion.exigirGrupo(grupo)).doesNotThrowAnyException();
    }

    private static Transaction gasto(UUID cuenta, String monto) {
        return TransactionMother.conEstado(TransactionMother.en(TransactionMother.unGastoGuardado(), cuenta, null, monto),
                TransactionStatus.PENDING);
    }

    private static Transaction conCategoria(Transaction t, UUID categoria) {
        return TransactionMother.conCategoria(t, categoria);
    }

    private static void errores(Throwable error, HttpStatus status, Tuple... esperados) {
        BadRequestException bre = (BadRequestException) error;
        assertThat(bre.getHttpStatus()).isEqualTo(status);
        assertThat(bre.getErrorResponse().getErrors())
                .extracting(ErrorDetail::getField, ErrorDetail::getCode)
                .containsExactlyInAnyOrder(esperados);
    }
}
