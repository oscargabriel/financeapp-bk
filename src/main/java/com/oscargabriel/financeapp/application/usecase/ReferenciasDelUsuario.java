package com.oscargabriel.financeapp.application.usecase;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.Account;
import com.oscargabriel.financeapp.domain.model.Category;
import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.model.TransactionType;

/**
 * Las cuentas y categorias del usuario contra las que se valida un movimiento, en el alta y en la
 * modificacion. Comparten esta clase para que un mismo id invalido de el mismo mensaje en los dos.
 * Cada metodo devuelve null si el valor no sirve y deja el error, sobre el campo recibido, en la lista.
 */
final class ReferenciasDelUsuario {

    /** Solo COP hasta que la etapa 9 cargue tasas: con eso amount_base = amount y exchange_rate = 1. */
    static final String MONEDA_UNICA = "COP";

    private final Map<UUID, Account> cuentas;
    private final Map<UUID, Category> categorias;

    ReferenciasDelUsuario(Map<UUID, Account> cuentas, Map<UUID, Category> categorias) {
        this.cuentas = cuentas;
        this.categorias = categorias;
    }

    /**
     * Una cuenta ajena, una inexistente y un id mal formado dan el mismo mensaje: distinguirlas le
     * diria a un usuario que ese id existe en otra parte.
     */
    UUID cuentaPropia(String valor, String campo, String sujeto, List<ErrorDetail> errores) {
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

    UUID categoria(String valor, TransactionType tipo, String campo, List<ErrorDetail> errores) {
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

    static ErrorDetail detalle(String descripcion, String campo) {
        return ErrorDetail.of(ErrorCodes.VALIDATION_ERROR.getCode(), descripcion, campo);
    }

    private static UUID uuid(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return UUID.fromString(valor.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
