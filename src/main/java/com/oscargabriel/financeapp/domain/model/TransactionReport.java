package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * El reporte de movimientos de un rango: la lista y sus totales. Los totales se calculan aqui, sobre
 * la misma lista que se devuelve, para que cuadren con ella por construccion (ver el design.md de
 * FA-63). Suman amountBase, la moneda base del usuario, nunca amount, y dejan fuera lo que a asOf
 * todavia esta programado.
 */
public record TransactionReport(
        String currencyCode,
        TransactionReportFilter filter,
        List<ReportedTransaction> transactions,
        List<TypeTotal> totalsByType,
        List<CategoryTotal> totalsByCategory,
        Instant asOf) {

    private static final Comparator<ReportedTransaction> MAS_RECIENTE_PRIMERO =
            Comparator.comparing(ReportedTransaction::occurredAt).reversed();

    private static final Comparator<CategoryTotal> MAYOR_PRIMERO =
            Comparator.comparing(CategoryTotal::total).reversed().thenComparing(CategoryTotal::categoryName);

    public record TypeTotal(TransactionType type, BigDecimal total, long count) {
    }

    public record CategoryTotal(UUID categoryId, String categoryName, BigDecimal total, long count) {
    }

    public static TransactionReport of(String currencyCode, TransactionReportFilter filter,
            List<ReportedTransaction> transactions, Instant asOf) {
        List<ReportedTransaction> ordenados = transactions.stream().sorted(MAS_RECIENTE_PRIMERO).toList();
        List<ReportedTransaction> vigentes = ordenados.stream().filter(t -> !t.scheduledAt(asOf)).toList();
        return new TransactionReport(currencyCode, filter, ordenados, porTipo(filter, vigentes),
                porCategoria(vigentes), asOf);
    }

    /** Un programado (FA-106) sale en la lista pero no en los totales: todavia no ha ocurrido. */
    public boolean scheduled(ReportedTransaction transaction) {
        return transaction.scheduledAt(asOf);
    }

    /** Ingresos menos gastos. Un tipo que el filtro deja fuera no tiene entrada y cuenta como cero. */
    public BigDecimal net() {
        return totalDe(TransactionType.INCOME).subtract(totalDe(TransactionType.EXPENSE));
    }

    private BigDecimal totalDe(TransactionType tipo) {
        return totalsByType.stream()
                .filter(t -> t.type() == tipo)
                .map(TypeTotal::total)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    /** Una entrada por tipo consultado, aunque no tenga movimientos: un tipo sin entrada no dice si es cero. */
    private static List<TypeTotal> porTipo(TransactionReportFilter filter, List<ReportedTransaction> movimientos) {
        return filter.typesToReport().stream()
                .map(tipo -> {
                    List<ReportedTransaction> delTipo = movimientos.stream().filter(t -> t.type() == tipo).toList();
                    return new TypeTotal(tipo, suma(delTipo), delTipo.size());
                })
                .toList();
    }

    /** Las transferencias no tienen categoria: no entran aqui. */
    private static List<CategoryTotal> porCategoria(List<ReportedTransaction> movimientos) {
        Map<UUID, List<ReportedTransaction>> porId = new LinkedHashMap<>();
        movimientos.stream()
                .filter(t -> t.categoryId() != null)
                .forEach(t -> porId.computeIfAbsent(t.categoryId(), id -> new ArrayList<>()).add(t));
        return porId.entrySet().stream()
                .map(e -> new CategoryTotal(e.getKey(), e.getValue().getFirst().categoryName(), suma(e.getValue()),
                        e.getValue().size()))
                .sorted(MAYOR_PRIMERO)
                .toList();
    }

    private static BigDecimal suma(List<ReportedTransaction> movimientos) {
        return movimientos.stream().map(ReportedTransaction::amountBase).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
