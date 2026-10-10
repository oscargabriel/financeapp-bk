package com.oscargabriel.financeapp.application.usecase;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.Account;
import com.oscargabriel.financeapp.domain.model.AccountType;
import com.oscargabriel.financeapp.domain.model.Category;
import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.domain.port.out.CategoryQueryPort;

import reactor.core.publisher.Mono;

/**
 * Las cuentas y categorias del usuario contra las que se valida un movimiento, en el alta y en la
 * modificacion de movimientos y de series. Comparten esta clase para que un mismo id invalido de el mismo mensaje en los dos.
 * Cada metodo devuelve null si el valor no sirve y deja el error, sobre el campo recibido, en la lista.
 */
final class ReferenciasDelUsuario {

    /**
     * La unica moneda de las series y las cuotas: los movimientos sueltos admiten cualquiera desde FA-51, pero
     * InstallmentPlan redondea a pesos enteros y una serie en otra moneda necesita sus propias reglas.
     */
    static final String MONEDA_UNICA = "COP";

    private final Map<UUID, Account> cuentas;
    private final Map<UUID, Category> categorias;

    ReferenciasDelUsuario(Map<UUID, Account> cuentas, Map<UUID, Category> categorias) {
        this.cuentas = cuentas;
        this.categorias = categorias;
    }

    /** Las cuentas activas o no y las categorias vivas del usuario, leidas una vez. */
    static Mono<ReferenciasDelUsuario> de(UUID userId, AccountQueryPort cuentas, CategoryQueryPort categorias) {
        return Mono.zip(
                        cuentas.findByUser(userId, true).collectMap(Account::id),
                        categorias.findActiveByUser(userId, EnumSet.allOf(CategoryScope.class))
                                .collectMap(Category::id))
                .map(mapas -> new ReferenciasDelUsuario(mapas.getT1(), mapas.getT2()));
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
        return id;
    }

    /** Una cuenta propia en COP, la unica moneda que admiten las series y las cuotas. */
    UUID cuentaEnCop(String valor, String campo, String limitacion, List<ErrorDetail> errores) {
        UUID id = cuentaPropia(valor, campo, "La cuenta", errores);
        if (id != null && !MONEDA_UNICA.equals(moneda(id))) {
            errores.add(detalle(limitacion, campo));
            return null;
        }
        return id;
    }

    /** La moneda de una cuenta del usuario, activa o no. */
    String moneda(UUID cuenta) {
        return cuentas.get(cuenta).currencyCode();
    }

    /**
     * Una cuenta propia que ademas es una tarjeta con corte y pago, sin los cuales no hay fechas de cuotas
     * (FA-108). Devuelve la cuenta entera: el plan necesita sus dias y su tasa.
     */
    Account tarjetaParaCuotas(String valor, String campo, List<ErrorDetail> errores) {
        UUID id = cuentaEnCop(valor, campo, "Por ahora las cuotas solo se registran en tarjetas en COP", errores);
        if (id == null) {
            return null;
        }
        Account cuenta = cuentas.get(id);
        if (cuenta.type() != AccountType.CREDIT) {
            errores.add(detalle("Las cuotas solo se registran sobre una tarjeta de credito", campo));
            return null;
        }
        if (cuenta.statementDay() == null || cuenta.paymentDueDay() == null) {
            errores.add(detalle("La tarjeta necesita dia de corte y dia de pago para calcular las cuotas", campo));
            return null;
        }
        return cuenta;
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
