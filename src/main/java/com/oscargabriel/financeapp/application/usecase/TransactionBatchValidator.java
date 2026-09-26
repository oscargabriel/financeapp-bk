package com.oscargabriel.financeapp.application.usecase;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
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

    static final int LARGO_MAXIMO_DESCRIPCION = 255;

    /** notes es TEXT; el tope lo fija FA-27 para que un lote de 500 no pase del MB del codec por las notas. */
    static final int LARGO_MAXIMO_NOTAS = 1000;

    /** NUMERIC(18,4): 4 decimales y 14 digitos enteros. */
    private static final int DECIMALES_MAXIMOS = 4;
    private static final BigDecimal TOPE_MONTO = new BigDecimal("100000000000000");

    private final UUID userId;
    private final Map<UUID, Account> cuentas;
    private final Map<UUID, Category> categorias;
    private final Supplier<UUID> ids;

    TransactionBatchValidator(UUID userId, Map<UUID, Account> cuentas, Map<UUID, Category> categorias,
            Supplier<UUID> ids) {
        this.userId = userId;
        this.cuentas = cuentas;
        this.categorias = categorias;
        this.ids = ids;
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

    /** Devuelve null si el elemento tiene algun error; los errores quedan en la lista. */
    private Transaction aMovimiento(CreateTransactionCommand elemento, String indice, List<ErrorDetail> errores) {
        if (elemento == null) {
            errores.add(detalle("El elemento no puede ser nulo", indice));
            return null;
        }
        int erroresPrevios = errores.size();

        TransactionType tipo = tipo(elemento.type(), indice + ".type", errores);
        UUID cuenta = cuentaPropia(elemento.accountId(), indice + ".accountId", "La cuenta", errores);
        UUID destino = destino(elemento, tipo, cuenta, indice + ".destinationAccountId", errores);
        UUID categoria = categoria(elemento.categoryId(), tipo, indice + ".categoryId", errores);
        monto(elemento.amount(), indice + ".amount", errores);

        if (elemento.destinationAmount() != null) {
            errores.add(detalle("destinationAmount no se admite mientras todas las cuentas sean COP",
                    indice + ".destinationAmount"));
        }
        if (elemento.currencyCode() != null && !MONEDA_UNICA.equalsIgnoreCase(elemento.currencyCode().trim())) {
            errores.add(detalle("Por ahora solo se admiten movimientos en COP", indice + ".currencyCode"));
        }

        String descripcion = descripcion(elemento.description(), indice + ".description", errores);
        if (elemento.notes() != null && elemento.notes().length() > LARGO_MAXIMO_NOTAS) {
            errores.add(detalle("Las notas no pueden superar los " + LARGO_MAXIMO_NOTAS + " caracteres",
                    indice + ".notes"));
        }
        OffsetDateTime fecha = fecha(elemento.occurredAt(), indice + ".occurredAt", errores);

        if (errores.size() > erroresPrevios) {
            return null;
        }
        return new Transaction(ids.get(), userId, tipo, cuenta, destino, categoria, elemento.amount(),
                MONEDA_UNICA, descripcion, elemento.notes(), fecha.toInstant());
    }

    private static TransactionType tipo(String valor, String campo, List<ErrorDetail> errores) {
        if (esVacio(valor)) {
            errores.add(detalle("El tipo es obligatorio", campo));
            return null;
        }
        try {
            return TransactionType.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            errores.add(detalle("El tipo debe ser EXPENSE, INCOME o TRANSFER", campo));
            return null;
        }
    }

    /**
     * Una cuenta ajena y una inexistente dan el mismo mensaje: distinguirlas le diria a un usuario que
     * ese id existe en otra parte.
     */
    private UUID cuentaPropia(String valor, String campo, String sujeto, List<ErrorDetail> errores) {
        if (esVacio(valor)) {
            errores.add(detalle(sujeto + " es obligatoria", campo));
            return null;
        }
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

    private UUID destino(CreateTransactionCommand elemento, TransactionType tipo, UUID origen, String campo,
            List<ErrorDetail> errores) {
        if (tipo == null) {
            return null;
        }
        if (tipo != TransactionType.TRANSFER) {
            if (elemento.destinationAccountId() != null) {
                errores.add(detalle("Solo una transferencia lleva cuenta destino", campo));
            }
            return null;
        }
        UUID destino = cuentaPropia(elemento.destinationAccountId(), campo, "La cuenta destino", errores);
        if (destino != null && destino.equals(origen)) {
            errores.add(detalle("La cuenta destino tiene que ser distinta de la de origen", campo));
            return null;
        }
        return destino;
    }

    private UUID categoria(String valor, TransactionType tipo, String campo, List<ErrorDetail> errores) {
        if (tipo == null) {
            return null;
        }
        if (tipo == TransactionType.TRANSFER) {
            if (valor != null) {
                errores.add(detalle("Una transferencia no lleva categoria", campo));
            }
            return null;
        }
        if (esVacio(valor)) {
            errores.add(detalle("La categoria es obligatoria en un gasto o un ingreso", campo));
            return null;
        }
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

    private static void monto(BigDecimal monto, String campo, List<ErrorDetail> errores) {
        if (monto == null) {
            errores.add(detalle("El monto es obligatorio", campo));
        } else if (monto.signum() <= 0) {
            errores.add(detalle("El monto debe ser mayor que cero: el signo lo da el tipo", campo));
        } else if (monto.stripTrailingZeros().scale() > DECIMALES_MAXIMOS || monto.compareTo(TOPE_MONTO) >= 0) {
            errores.add(detalle("El monto admite hasta " + DECIMALES_MAXIMOS
                    + " decimales y menos de 14 digitos enteros", campo));
        }
    }

    private static String descripcion(String valor, String campo, List<ErrorDetail> errores) {
        if (esVacio(valor)) {
            errores.add(detalle("La descripcion es obligatoria", campo));
            return null;
        }
        String recortada = valor.trim();
        if (recortada.length() > LARGO_MAXIMO_DESCRIPCION) {
            errores.add(detalle("La descripcion no puede superar los " + LARGO_MAXIMO_DESCRIPCION + " caracteres",
                    campo));
            return null;
        }
        return recortada;
    }

    /** Sin offset la hora es ambigua, y el corte de mes de los reportes depende de la zona. */
    private static OffsetDateTime fecha(String valor, String campo, List<ErrorDetail> errores) {
        if (esVacio(valor)) {
            errores.add(detalle("La fecha es obligatoria", campo));
            return null;
        }
        try {
            return OffsetDateTime.parse(valor.trim());
        } catch (DateTimeParseException e) {
            errores.add(detalle("La fecha debe ser ISO-8601 con offset, por ejemplo 2026-09-20T10:15:00-05:00",
                    campo));
            return null;
        }
    }

    private static UUID uuid(String valor) {
        try {
            return UUID.fromString(valor.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static ErrorDetail detalle(String descripcion, String campo) {
        return ErrorDetail.of(ErrorCodes.VALIDATION_ERROR.getCode(), descripcion, campo);
    }
}
