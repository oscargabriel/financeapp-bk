package com.oscargabriel.financeapp.core.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Lo que muestra la pantalla de Inicio, ya calculado (FIN-107). Los montos de months, spent y debitBalance van
 * en currencyCode, la moneda base; los porcentajes, a 2 decimales con mitad hacia arriba, como percentUsed de
 * v_monthly_spending.
 */
public record HomeSummary(
        String currencyCode,
        YearMonth month,
        List<Month> months,
        BigDecimal spent,
        BigDecimal spentChangePercent,
        List<ReportedTransaction> latestExpenses,
        List<TopExpense> topExpenses,
        BigDecimal debitBalance,
        List<AccountUsage> accounts) {

    /** Los meses de la serie, el en curso incluido. */
    public static final int MESES = 6;

    /** El tamano de las dos listas de gastos. */
    public static final int GASTOS = 5;

    private static final BigDecimal CIEN = new BigDecimal("100");

    public record Month(YearMonth periodMonth, BigDecimal totalIncome, BigDecimal totalSpent) {

        static final Function<YearMonth, Month> VACIO = mes -> new Month(mes, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public record TopExpense(ReportedTransaction expense, BigDecimal shareOfTop) {
    }

    /** creditUsedPercent es null fuera de una tarjeta con cupo mayor que 0. */
    public record AccountUsage(Account account, BigDecimal creditUsedPercent) {
    }

    /**
     * mesesConDatos son las filas de v_monthly_spending, que no trae los meses vacios; masCostosos llega ya
     * ordenado de mayor a menor amountBase.
     */
    public static HomeSummary of(YearMonth mes, Balance.Sums sumas, List<MonthlySpending> mesesConDatos,
            List<ReportedTransaction> ultimos, List<ReportedTransaction> masCostosos, List<Account> cuentas) {
        List<Month> serie = serie(mes, mesesConDatos);
        BigDecimal gastado = serie.getLast().totalSpent();
        BigDecimal anterior = serie.get(MESES - 2).totalSpent();
        return new HomeSummary(sumas.currencyCode(), mes, serie, gastado, variacion(gastado, anterior),
                List.copyOf(ultimos), proporciones(masCostosos), sumas.debitBalance(),
                cuentas.stream().map(c -> new AccountUsage(c, cupoUsado(c))).toList());
    }

    private static List<Month> serie(YearMonth mes, List<MonthlySpending> mesesConDatos) {
        Map<YearMonth, Month> porMes = mesesConDatos.stream()
                .collect(Collectors.toMap(MonthlySpending::periodMonth,
                        m -> new Month(m.periodMonth(), m.totalIncome(), m.totalSpent())));
        return IntStream.range(0, MESES)
                .mapToObj(i -> mes.minusMonths(MESES - 1L - i))
                .map(m -> porMes.getOrDefault(m, Month.VACIO.apply(m)))
                .toList();
    }

    /** Sin gasto el mes anterior no hay base contra la que comparar: null, no cero ni infinito. */
    private static BigDecimal variacion(BigDecimal gastado, BigDecimal anterior) {
        if (anterior.signum() == 0) {
            return null;
        }
        return porcentaje(gastado.subtract(anterior), anterior);
    }

    private static List<TopExpense> proporciones(List<ReportedTransaction> masCostosos) {
        if (masCostosos.isEmpty()) {
            return List.of();
        }
        BigDecimal mayor = masCostosos.getFirst().amountBase();
        return masCostosos.stream().map(t -> new TopExpense(t, porcentaje(t.amountBase(), mayor))).toList();
    }

    /** No descuenta las cuotas futuras (decision del epic FIN-2, 16-10-2026): solo la deuda vigente. */
    private static BigDecimal cupoUsado(Account cuenta) {
        if (cuenta.type() != AccountType.CREDIT || cuenta.creditLimit() == null || cuenta.creditLimit().signum() <= 0) {
            return null;
        }
        return porcentaje(cuenta.currentBalance().negate(), cuenta.creditLimit()).max(BigDecimal.ZERO).min(CIEN);
    }

    private static BigDecimal porcentaje(BigDecimal parte, BigDecimal total) {
        return parte.multiply(CIEN).divide(total, 2, RoundingMode.HALF_UP);
    }
}
