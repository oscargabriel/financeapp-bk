package com.oscargabriel.financeapp.application.usecase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.Account;
import com.oscargabriel.financeapp.domain.model.AccountType;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.TransactionStatus;

/**
 * Ninguna peticion baja el saldo vigente de una cuenta que no es CREDIT por debajo de cero (FA-133). Solo se
 * rechaza lo que baja el saldo: una cuenta que ya estaba en negativo sigue recibiendo dinero. La regla vive aqui y
 * no en la base porque SeriesAlDia inserta ocurrencias desde las lecturas, y esas nunca se validan (design.md).
 *
 * Las cuentas son las del usuario con su saldo vigente; una que no este (borrada) no se valida.
 */
final class SaldoNoNegativo {

    private final Map<UUID, Account> cuentas;

    SaldoNoNegativo(Map<UUID, Account> cuentas) {
        this.cuentas = cuentas;
    }

    /** Lo que el movimiento suma al saldo vigente de cada cuenta: nada si esta pendiente o programado. */
    static Map<UUID, BigDecimal> efecto(Transaction t, Instant ahora) {
        if (t.status() != TransactionStatus.CONFIRMED || t.scheduledAt(ahora)) {
            return Map.of();
        }
        return switch (t.type()) {
            case EXPENSE -> Map.of(t.accountId(), t.amount().negate());
            case INCOME -> Map.of(t.accountId(), t.amount());
            case TRANSFER -> Map.of(t.accountId(), t.amount().negate(), t.destinationAccountId(),
                    t.destinationAmount() == null ? t.amount() : t.destinationAmount());
        };
    }

    /** despues - antes, cuenta por cuenta: lo que una modificacion o un borrado le cambia a cada una. */
    static Map<UUID, BigDecimal> diferencia(Map<UUID, BigDecimal> despues, Map<UUID, BigDecimal> antes) {
        Map<UUID, BigDecimal> cambio = new HashMap<>(despues);
        antes.forEach((cuenta, monto) -> cambio.merge(cuenta, monto.negate(), BigDecimal::add));
        return cambio;
    }

    /** Lanza un 409 con un error por cada cuenta que el cambio baja por debajo de cero, todos en el mismo campo. */
    void exigir(Map<UUID, BigDecimal> cambio, String campo) {
        List<ErrorDetail> errores = new ArrayList<>();
        cambio.forEach((cuenta, monto) -> {
            if (quedaEnNegativo(cuenta, monto)) {
                errores.add(insuficiente(cuenta, monto, campo));
            }
        });
        rechazarSiHay(errores);
    }

    /**
     * El lote es todo o nada, asi que se valida su efecto neto por cuenta y no depende del orden: un ingreso del
     * mismo lote compensa un gasto. El orden solo ubica el error, en el primer elemento que saca dinero de la cuenta
     * y deja el acumulado en negativo. Si el neto la deja en negativo, ese elemento siempre existe.
     */
    void exigirLote(List<Transaction> lote, Instant ahora) {
        List<Map<UUID, BigDecimal>> efectos = lote.stream().map(t -> efecto(t, ahora)).toList();
        Map<UUID, BigDecimal> neto = new HashMap<>();
        efectos.forEach(efecto -> efecto.forEach((cuenta, monto) -> neto.merge(cuenta, monto, BigDecimal::add)));
        neto.keySet().removeIf(cuenta -> !quedaEnNegativo(cuenta, neto.get(cuenta)));
        if (neto.isEmpty()) {
            return;
        }

        Map<UUID, BigDecimal> acumulado = new HashMap<>();
        Map<UUID, Integer> dondeCruza = new LinkedHashMap<>();
        for (int i = 0; i < efectos.size(); i++) {
            for (Map.Entry<UUID, BigDecimal> efecto : efectos.get(i).entrySet()) {
                UUID cuenta = efecto.getKey();
                if (!neto.containsKey(cuenta) || dondeCruza.containsKey(cuenta)) {
                    continue;
                }
                BigDecimal saldo = acumulado.merge(cuenta, efecto.getValue(), BigDecimal::add)
                        .add(cuentas.get(cuenta).currentBalance());
                if (efecto.getValue().signum() < 0 && saldo.signum() < 0) {
                    dondeCruza.put(cuenta, i);
                }
            }
        }
        rechazarSiHay(dondeCruza.entrySet().stream()
                .map(cruce -> insuficiente(cruce.getKey(), neto.get(cruce.getKey()),
                        "[" + cruce.getValue() + "].amount"))
                .toList());
    }

    private boolean quedaEnNegativo(UUID cuenta, BigDecimal cambio) {
        Account guardada = cuentas.get(cuenta);
        return guardada != null
                && guardada.type() != AccountType.CREDIT
                && cambio.signum() < 0
                && guardada.currentBalance().add(cambio).signum() < 0;
    }

    private ErrorDetail insuficiente(UUID cuenta, BigDecimal cambio, String campo) {
        Account guardada = cuentas.get(cuenta);
        String saldo = guardada.currentBalance().add(cambio).stripTrailingZeros().toPlainString();
        return ErrorDetail.of(ErrorCodes.INSUFFICIENT_FUNDS.getCode(),
                "Saldo insuficiente en " + guardada.name() + ": quedaría en " + saldo + " " + guardada.currencyCode(),
                campo);
    }

    private static void rechazarSiHay(List<ErrorDetail> errores) {
        if (!errores.isEmpty()) {
            throw new BadRequestException(HttpStatus.CONFLICT, errores);
        }
    }
}
