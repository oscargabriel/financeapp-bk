package com.oscargabriel.financeapp.application.usecase;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.Account;
import com.oscargabriel.financeapp.domain.model.Category;
import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.model.CreateTransactionCommand;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.TransactionType;

/**
 * Valida un lote entero contra las cuentas y categorias del usuario y lo convierte en movimientos.
 * Acumula los errores de todos los elementos, cada uno con su indice ([3].amount), para que quien
 * carga el JSON lo corrija en una sola pasada.
 */
final class TransactionBatchValidator {

    /** Solo COP hasta que la etapa 9 cargue tasas: con eso amount_base = amount y exchange_rate = 1. */
    static final String MONEDA_UNICA = "COP";

    private final UUID userId;
    private final Map<UUID, Account> cuentas;
    private final Map<UUID, Category> categorias;
    private final Supplier<UUID> ids;

    /** El de los elementos que llegan sin fecha. */
    private final Instant ahora;

    TransactionBatchValidator(UUID userId, Map<UUID, Account> cuentas, Map<UUID, Category> categorias,
            Supplier<UUID> ids, Instant ahora) {
        this.userId = userId;
        this.cuentas = cuentas;
        this.categorias = categorias;
        this.ids = ids;
        this.ahora = ahora;
    }

    List<Transaction> aMovimientos(List<CreateTransactionCommand> lote) {
        List<ErrorDetail> errores = new ArrayList<>();
        List<Transaction> movimientos = new ArrayList<>(lote.size());

        for (int i = 0; i < lote.size(); i++) {
            movimientos.add(aMovimiento(lote.get(i), "[" + i + "]", errores));
        }

        if (!errores.isEmpty()) {
            throw new BadRequestException(HttpStatus.BAD_REQUEST, errores);
        }
        return movimientos;
    }

    /**
     * Devuelve null si el elemento tiene algun error; los errores quedan en la lista. El formato, los
     * obligatorios y las reglas de transferencia ya los valido CreateTransactionRequest: aqui queda lo
     * que necesita las cuentas y categorias del usuario.
     */
    private Transaction aMovimiento(CreateTransactionCommand elemento, String indice, List<ErrorDetail> errores) {
        int erroresPrevios = errores.size();

        TransactionType tipo = TransactionType.valueOf(elemento.type().trim().toUpperCase());
        UUID cuenta = cuentaPropia(elemento.accountId(), indice + ".accountId", "La cuenta", errores);
        UUID destino = tipo == TransactionType.TRANSFER
                ? cuentaPropia(elemento.destinationAccountId(), indice + ".destinationAccountId", "La cuenta destino",
                        errores)
                : null;
        UUID categoria = tipo == TransactionType.TRANSFER
                ? null
                : categoria(elemento.categoryId(), tipo, indice + ".categoryId", errores);

        if (errores.size() > erroresPrevios) {
            return null;
        }
        return new Transaction(ids.get(), userId, tipo, cuenta, destino, categoria, elemento.amount(),
                MONEDA_UNICA, elemento.description().trim(), elemento.notes(),
                instante(elemento.occurredAt()));
    }

    /**
     * Una cuenta ajena, una inexistente y un id mal formado dan el mismo mensaje: distinguirlas le
     * diria a un usuario que ese id existe en otra parte.
     */
    private UUID cuentaPropia(String valor, String campo, String sujeto, List<ErrorDetail> errores) {
        UUID id = uuid(valor);
        Account cuenta = id == null ? null : cuentas.get(id);
        if (cuenta == null) {
            errores.add(detalle(sujeto + " no existe", campo));
            return null;
        }
        if (!cuenta.active()) {
            errores.add(detalle(sujeto + " esta desactivada", campo));
            return null;
        }
        if (!MONEDA_UNICA.equals(cuenta.currencyCode())) {
            errores.add(detalle(sujeto + " no es en COP: por ahora solo se admiten movimientos en COP", campo));
            return null;
        }
        return id;
    }

    private UUID categoria(String valor, TransactionType tipo, String campo, List<ErrorDetail> errores) {
        UUID id = uuid(valor);
        Category categoria = id == null ? null : categorias.get(id);
        if (categoria == null) {
            errores.add(detalle("La categoria no existe", campo));
            return null;
        }
        CategoryScope alcance = tipo == TransactionType.EXPENSE ? CategoryScope.EXPENSE : CategoryScope.INCOME;
        if (!alcance.compatibles().contains(categoria.appliesTo())) {
            errores.add(detalle("La categoria no aplica a un movimiento de tipo " + tipo, campo));
            return null;
        }
        return id;
    }

    /** El formato ya lo valido CreateTransactionRequest; aqui solo falta decidir el de los vacios. */
    private Instant instante(String fecha) {
        return fecha == null || fecha.isBlank() ? ahora : OffsetDateTime.parse(fecha.trim()).toInstant();
    }

    private static UUID uuid(String valor) {
        try {
            return UUID.fromString(valor.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ErrorDetail detalle(String descripcion, String campo) {
        return ErrorDetail.of(ErrorCodes.VALIDATION_ERROR.getCode(), descripcion, campo);
    }
}
