package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Las cuotas de una compra con tarjeta (FA-108). El primer corte es el del mes de la compra si cae ese
 * dia o antes; la primera cuota vence el dia de pago del mes del corte si es posterior al corte, y del
 * mes siguiente si no. Capital fijo: amount / count hacia abajo a los decimales de la moneda de la tarjeta
 * (FA-123), con el resto en la ultima. Interes: la tasa mensual, en porcentaje, sobre el capital pendiente
 * antes de cada cuota, a esos mismos decimales. Una compra a 1 cuota no lleva interes salvo que la tarjeta lo cobre (FA-134).
 */
public record InstallmentPlan(BigDecimal amount, int count, BigDecimal monthlyInterestRate, LocalDate purchaseDate,
        int statementDay, int paymentDueDay, boolean chargesSingleInstallmentInterest, int decimals) {

    /** Una tarjeta en COP que no cobra interes en las compras a 1 cuota. */
    public InstallmentPlan(BigDecimal amount, int count, BigDecimal monthlyInterestRate, LocalDate purchaseDate,
            int statementDay, int paymentDueDay) {
        this(amount, count, monthlyInterestRate, purchaseDate, statementDay, paymentDueDay, false, 0);
    }

    public static final int MAXIMO = 48;

    private static final BigDecimal CIEN = new BigDecimal("100");

    public record Cuota(int number, LocalDate dueDate, BigDecimal principal, BigDecimal interest) {

        public BigDecimal amount() {
            return principal.add(interest);
        }
    }

    public List<Cuota> cuotas() {
        BigDecimal base = amount.divide(BigDecimal.valueOf(count), decimals, RoundingMode.DOWN);
        BigDecimal ultima = amount.subtract(base.multiply(BigDecimal.valueOf(count - 1L)));
        return IntStream.rangeClosed(1, count)
                .mapToObj(k -> new Cuota(k, vencimiento(k), k == count ? ultima : base,
                        interes(amount.subtract(base.multiply(BigDecimal.valueOf(k - 1L))))))
                .toList();
    }

    public BigDecimal totalInterest() {
        return cuotas().stream().map(Cuota::interest).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalAmount() {
        return amount.add(totalInterest());
    }

    /** Las cuotas que vencen hoy o antes: a medianoche ya ocurrieron. */
    public int vencidas(LocalDate hoy) {
        return (int) IntStream.rangeClosed(1, count).filter(k -> !vencimiento(k).isAfter(hoy)).count();
    }

    private BigDecimal interes(BigDecimal pendiente) {
        if (count == 1 && !chargesSingleInstallmentInterest) {
            return BigDecimal.ZERO;
        }
        return pendiente.multiply(monthlyInterestRate).divide(CIEN, decimals, RoundingMode.HALF_UP);
    }

    private LocalDate vencimiento(int k) {
        YearMonth mesDelCorte = YearMonth.from(purchaseDate);
        if (purchaseDate.isAfter(enElMes(mesDelCorte, statementDay))) {
            mesDelCorte = mesDelCorte.plusMonths(1);
        }
        YearMonth primerPago = paymentDueDay > statementDay ? mesDelCorte : mesDelCorte.plusMonths(1);
        return enElMes(primerPago.plusMonths(k - 1L), paymentDueDay);
    }

    private static LocalDate enElMes(YearMonth mes, int dia) {
        return mes.atDay(Math.min(dia, mes.lengthOfMonth()));
    }
}
