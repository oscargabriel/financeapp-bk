package com.oscargabriel.financeapp.ia.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.oscargabriel.financeapp.ia.domain.model.AssistantBalance;
import com.oscargabriel.financeapp.ia.domain.model.AssistantReport;
import com.oscargabriel.financeapp.ia.domain.model.CategoryScope;
import com.oscargabriel.financeapp.ia.domain.model.TransactionType;
import com.oscargabriel.financeapp.ia.domain.model.UserAccount;
import com.oscargabriel.financeapp.ia.domain.model.UserCategory;

/** El usuario del asistente: tres cuentas activas y una categoria de cada alcance. */
public final class AsistenteMother {

    public static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
    public static final UUID EFECTIVO_ID = UUID.fromString("10000000-0000-7000-8000-000000000001");
    public static final UUID AHORROS_ID = UUID.fromString("10000000-0000-7000-8000-000000000002");
    public static final UUID AHORROS_USD_ID = UUID.fromString("10000000-0000-7000-8000-000000000003");
    public static final UUID MERCADO_ID = UUID.fromString("20000000-0000-7000-8000-000000000001");
    public static final UUID SALARIO_ID = UUID.fromString("20000000-0000-7000-8000-000000000002");
    public static final UUID REINTEGROS_ID = UUID.fromString("20000000-0000-7000-8000-000000000003");

    private AsistenteMother() {
    }

    public static List<UserAccount> cuentas() {
        return List.of(
                new UserAccount(EFECTIVO_ID, "Efectivo", "COP"),
                new UserAccount(AHORROS_ID, "Ahorros", "COP"),
                new UserAccount(AHORROS_USD_ID, "Ahorros USD", "USD"));
    }

    public static List<UserCategory> categorias() {
        return List.of(
                new UserCategory(MERCADO_ID, "Mercado", CategoryScope.EXPENSE),
                new UserCategory(SALARIO_ID, "Salario", CategoryScope.INCOME),
                new UserCategory(REINTEGROS_ID, "Reintegros", CategoryScope.BOTH));
    }

    /** amountBase distinto de amount para notar si un total sale de la columna equivocada. */
    public static AssistantReport.Item unMovimiento(TransactionType tipo, String monto, String montoBase) {
        return new AssistantReport.Item(UUID.randomUUID(), tipo, new BigDecimal(monto), "USD",
                new BigDecimal(montoBase), "Un movimiento", Instant.parse("2026-10-05T17:00:00Z"), "Ahorros USD",
                tipo == TransactionType.TRANSFER ? null : "Mercado");
    }

    public static AssistantBalance.Sums sumas() {
        return new AssistantBalance.Sums("COP", new BigDecimal("3000000"), new BigDecimal("1200000"),
                new BigDecimal("9000000"), new BigDecimal("7500000"));
    }

    public static AssistantBalance.AccountBalance saldoDeEfectivo() {
        return new AssistantBalance.AccountBalance("Efectivo", "COP", new BigDecimal("250000"));
    }
}
