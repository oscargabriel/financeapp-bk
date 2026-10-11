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
import com.oscargabriel.financeapp.domain.model.Currency;
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

    /** Las activas del catalogo, por codigo: contra ellas se valida currencyCode. */
    private final Map<String, Currency> monedas;

    private final Supplier<UUID> ids;

    /** El de los elementos que llegan sin fecha. */
    private final Instant ahora;

    private final TransactionOrigin origen;

    TransactionBatchValidator(UUID userId, Map<UUID, Account> cuentas, Map<UUID, Category> categorias,
            Map<String, Currency> monedas, Supplier<UUID> ids, Instant ahora, TransactionOrigin origen) {
        this.userId = userId;
        this.referencias = new ReferenciasDelUsuario(cuentas, categorias);
        this.monedas = monedas;
        this.ids = ids;
        this.ahora = ahora;
        this.origen = origen;
    }

    List<Borrador> aBorradores(List<CreateTransactionCommand> lote) {
        List<ErrorDetail> errores = new ArrayList<>();
        List<Borrador> borradores = new ArrayList<>(lote.size());

        for (int i = 0; i < lote.size(); i++) {
            borradores.add(aBorrador(lote.get(i), "[" + i + "]", errores));
        }

        if (!errores.isEmpty()) {
            throw new BadRequestException(HttpStatus.BAD_REQUEST, errores);
        }
        return borradores;
    }

    /**
     * Devuelve null si el elemento tiene algun error; los errores quedan en la lista. El formato, los
     * obligatorios y las reglas de transferencia ya los valido CreateTransactionRequest: aqui queda lo
     * que necesita las cuentas, las categorias y las monedas.
     *
     * El movimiento queda en la moneda de su cuenta (FA-51). Si llego en otra, o es una transferencia hacia una
     * cuenta de otra moneda sin destinationAmount, el borrador dice que falta convertir.
     */
    private Borrador aBorrador(CreateTransactionCommand elemento, String indice, List<ErrorDetail> errores) {
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

        String recibida = monedaRecibida(elemento.currencyCode(), indice + ".currencyCode", errores);
        boolean entreMonedas = cuenta != null && destino != null
                && !referencias.moneda(cuenta).equals(referencias.moneda(destino));
        if (cuenta != null && destino != null && !entreMonedas && elemento.destinationAmount() != null) {
            errores.add(ReferenciasDelUsuario.detalle(
                    "El monto de destino solo aplica entre cuentas de monedas distintas", indice + ".destinationAmount"));
        }

        if (errores.size() > erroresPrevios) {
            return null;
        }
        String monedaDeLaCuenta = referencias.moneda(cuenta);
        Transaction movimiento = new Transaction(ids.get(), userId, tipo, cuenta, destino, categoria,
                elemento.amount(), monedaDeLaCuenta, elemento.description().trim(), elemento.notes(),
                instante(elemento.occurredAt()), origen.estadoInicial(), origen, null, null,
                elemento.destinationAmount(), null);
        return new Borrador(movimiento,
                recibida == null || recibida.equals(monedaDeLaCuenta) ? null : recibida,
                entreMonedas && elemento.destinationAmount() == null ? referencias.moneda(destino) : null);
    }

    /** Ausente es la moneda de la cuenta. El formato de 3 letras ya lo valido CreateTransactionRequest. */
    private String monedaRecibida(String valor, String campo, List<ErrorDetail> errores) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String codigo = valor.trim().toUpperCase();
        if (!monedas.containsKey(codigo)) {
            errores.add(ReferenciasDelUsuario.detalle("La moneda no es una moneda activa del catalogo", campo));
            return null;
        }
        return codigo;
    }

    /** El formato ya lo valido CreateTransactionRequest; aqui solo falta decidir el de los vacios. */
    private Instant instante(String fecha) {
        return fecha == null || fecha.isBlank() ? ahora : OffsetDateTime.parse(fecha.trim()).toInstant();
    }
}
