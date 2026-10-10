package com.oscargabriel.financeapp.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.InstallmentRef;
import com.oscargabriel.financeapp.domain.model.ReportedTransaction;
import com.oscargabriel.financeapp.domain.model.TransactionReportFilter;
import com.oscargabriel.financeapp.domain.model.TransactionType;

/** Movimientos y filtros del reporte. Los montos base son los que suman los totales. */
public final class ReportMother {

    public static final UUID USER_ID = UUID.fromString("10000000-0000-7000-8000-000000000001");
    public static final UUID CUENTA_ID = UUID.fromString("20000000-0000-7000-8000-000000000001");
    public static final UUID DESTINO_ID = UUID.fromString("20000000-0000-7000-8000-000000000002");

    public static final UUID MERCADO_ID = UUID.fromString("40000000-0000-7000-8000-000000000001");
    public static final UUID SALARIO_ID = UUID.fromString("40000000-0000-7000-8000-000000000002");
    public static final UUID RESTAURANTES_ID = UUID.fromString("40000000-0000-7000-8000-000000000003");

    public static final LocalDate DESDE = LocalDate.of(2026, 9, 1);
    public static final LocalDate HASTA = LocalDate.of(2026, 9, 30);

    /** El instante de los reportes de prueba: despues de todo el mes, asi nada sale programado. */
    public static final Instant AHORA = Instant.parse("2026-10-01T00:00:00Z");

    private ReportMother() {
    }

    public static TransactionReportFilter sinFiltros() {
        return new TransactionReportFilter(DESDE, HASTA, Set.of(), Set.of(), Set.of());
    }

    public static TransactionReportFilter conTipos(TransactionType... tipos) {
        return new TransactionReportFilter(DESDE, HASTA, Set.of(), Set.of(), Set.of(tipos));
    }

    public static TransactionReportFilter conCuentas(UUID... cuentas) {
        return new TransactionReportFilter(DESDE, HASTA, Set.of(), Set.of(cuentas), Set.of());
    }

    public static ReportedTransaction unGasto(UUID categoria, String nombre, String montoBase, String instante) {
        return new ReportedTransaction(UUID.randomUUID(), TransactionType.EXPENSE, CUENTA_ID, null, categoria, nombre,
                new BigDecimal(montoBase), "COP", new BigDecimal(montoBase), "Gasto", null, Instant.parse(instante), null, null);
    }

    /** El mismo movimiento como cuota 1 de 3 de una compra en cuotas (FA-108). */
    public static ReportedTransaction cuota(ReportedTransaction t, UUID compra) {
        return new ReportedTransaction(t.id(), t.type(), t.accountId(), t.destinationAccountId(), t.categoryId(),
                t.categoryName(), t.amount(), t.currencyCode(), t.amountBase(), t.description(), t.notes(),
                t.occurredAt(), t.recurrenceId(), new InstallmentRef(compra, 1, 3, new BigDecimal("400000")));
    }

    /** El mismo movimiento como ocurrencia de una serie (FA-107). */    public static ReportedTransaction deLaSerie(ReportedTransaction t, UUID serie) {
        return new ReportedTransaction(t.id(), t.type(), t.accountId(), t.destinationAccountId(), t.categoryId(),
                t.categoryName(), t.amount(), t.currencyCode(), t.amountBase(), t.description(), t.notes(),
                t.occurredAt(), serie, t.installment());
    }

    public static ReportedTransaction unIngreso(String montoBase, String instante) {
        return new ReportedTransaction(UUID.randomUUID(), TransactionType.INCOME, CUENTA_ID, null, SALARIO_ID,
                "Salario", new BigDecimal(montoBase), "COP", new BigDecimal(montoBase), "Ingreso", null,
                Instant.parse(instante), null, null);
    }

    /** 100 USD que valen 410.000 COP: amount y amountBase distintos a proposito. */
    public static ReportedTransaction unaTransferenciaEnDolares(String instante) {
        return new ReportedTransaction(UUID.randomUUID(), TransactionType.TRANSFER, CUENTA_ID, DESTINO_ID, null, null,
                new BigDecimal("100.0000"), "USD", new BigDecimal("410000.0000"), "Cambio de dolares", null,
                Instant.parse(instante), null, null);
    }
}
