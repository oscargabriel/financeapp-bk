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
 * mes siguiente si no. Capital fijo: amount / count en pesos enteros hacia abajo, con el resto en la
 * ultima. Interes: la tasa mensual, en porcentaje, sobre el capital pendiente antes de cada cuota.
 */
public record InstallmentPlan(BigDecimal amount, int count, BigDecimal monthlyInterestRate, LocalDate purchaseDate,
        int statementDay, int paymentDueDay) {

    public static final int MAXIMO = 48;

    /** Pesos enteros: decimal_places de COP, la unica moneda de los movimientos. */
    private static final int ESCALA = 0;

    private static final BigDecimal CIEN = new BigDecimal("100");

    public record Cuota(int number, LocalDate dueDate, BigDecimal principal, BigDecimal interest) {

        public BigDecimal amount() {
            return principal.add(interest);
        }
    }

    public List<Cuota> cuotas() {
        BigDecimal base = amount.divide(BigDecimal.valueOf(count), ESCALA, RoundingMode.DOWN);
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
        if (count == 1) {
            return BigDecimal.ZERO;
        }
        return pendiente.multiply(monthlyInterestRate).divide(CIEN, ESCALA, RoundingMode.HALF_UP);
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
