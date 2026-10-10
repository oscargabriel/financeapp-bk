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
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.Account;
import com.oscargabriel.financeapp.domain.model.Category;
import com.oscargabriel.financeapp.domain.model.CreateTransactionCommand;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.TransactionOrigin;
import com.oscargabriel.financeapp.domain.model.TransactionType;

/**
 * Valida un lote entero contra las cuentas y categorias del usuario y lo convierte en movimientos.
 * Acumula los errores de todos los elementos, cada uno con su indice ([3].amount), para que quien
 * carga el JSON lo corrija en una sola pasada.
 */
final class TransactionBatchValidator {

    private final UUID userId;
    private final ReferenciasDelUsuario referencias;
    private final Supplier<UUID> ids;

    /** El de los elementos que llegan sin fecha. */
    private final Instant ahora;

    private final TransactionOrigin origen;

    TransactionBatchValidator(UUID userId, Map<UUID, Account> cuentas, Map<UUID, Category> categorias,
            Supplier<UUID> ids, Instant ahora, TransactionOrigin origen) {
        this.userId = userId;
        this.referencias = new ReferenciasDelUsuario(cuentas, categorias);
        this.ids = ids;
        this.ahora = ahora;
        this.origen = origen;
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
        UUID cuenta = referencias.cuentaPropia(elemento.accountId(), indice + ".accountId", "La cuenta", errores);
        UUID destino = tipo == TransactionType.TRANSFER
                ? referencias.cuentaPropia(elemento.destinationAccountId(), indice + ".destinationAccountId",
                        "La cuenta destino", errores)
                : null;
        UUID categoria = tipo == TransactionType.TRANSFER
                ? null
                : referencias.categoria(elemento.categoryId(), tipo, indice + ".categoryId", errores);

        if (errores.size() > erroresPrevios) {
            return null;
        }
        return new Transaction(ids.get(), userId, tipo, cuenta, destino, categoria, elemento.amount(),
                ReferenciasDelUsuario.MONEDA_UNICA, elemento.description().trim(), elemento.notes(),
                instante(elemento.occurredAt()), origen.estadoInicial(), origen, null, null);
    }

    /** El formato ya lo valido CreateTransactionRequest; aqui solo falta decidir el de los vacios. */
    private Instant instante(String fecha) {
        return fecha == null || fecha.isBlank() ? ahora : OffsetDateTime.parse(fecha.trim()).toInstant();
    }
}
