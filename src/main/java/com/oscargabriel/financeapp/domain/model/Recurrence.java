package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Una serie de gastos o ingresos que se repiten (FA-107): la plantilla de sus ocurrencias, su regla
 * de fechas y el fin, por fecha (endDate) o por numero (occurrenceLimit), o ninguno. generatedCount
 * cuenta las ocurrencias de la regla actual ya creadas y priorCount las de reglas anteriores, que
 * siguen contando contra occurrenceLimit.
 */
public record Recurrence(
        UUID id,
        UUID userId,
        TransactionType type,
        UUID accountId,
        UUID categoryId,
        BigDecimal amount,
        String currencyCode,
        String description,
        RecurrenceRule rule,
        LocalDate endDate,
        Integer occurrenceLimit,
        int generatedCount,
        int priorCount,
        RecurrenceStatus status) {

    /** Lo mas que una serie crea de una vez, el mismo tope del alta en lote (FA-26). */
    public static final int TOPE = 500;

    public boolean openEnded() {
        return endDate == null && occurrenceLimit == null;
    }

    /**
     * Las fechas que faltan por crear, desde la siguiente al contador: no desde las ocurrencias que
     * existen, asi que una borrada a mano no vuelve. Con fin, todas las que quedan; sin fin, las de
     * hoy o antes y una mas. Corta en TOPE + 1 para que quien llama detecte el exceso.
     */
    public List<LocalDate> pendientes(LocalDate hoy) {
        List<LocalDate> fechas = new ArrayList<>();
        if (openEnded() && generatedCount > 0 && rule.occurrence(generatedCount).isAfter(hoy)) {
            return fechas;
        }
        for (int k = generatedCount + 1; fechas.size() <= TOPE; k++) {
            if (occurrenceLimit != null && priorCount + k > occurrenceLimit) {
                break;
            }
            LocalDate fecha = rule.occurrence(k);
            if (endDate != null && fecha.isAfter(endDate)) {
                break;
            }
            fechas.add(fecha);
            if (openEnded() && fecha.isAfter(hoy)) {
                break;
            }
        }
        return fechas;
    }

    public Recurrence conGeneradas(int cuantas) {
        return new Recurrence(id, userId, type, accountId, categoryId, amount, currencyCode, description, rule,
                endDate, occurrenceLimit, generatedCount + cuantas, priorCount, status);
    }

    /** La ocurrencia de ese dia: un movimiento confirmado, a medianoche en la zona del usuario. */
    public Transaction ocurrencia(UUID transactionId, LocalDate dia, ZoneId zona) {
        return new Transaction(transactionId, userId, type, accountId, null, categoryId, amount, currencyCode,
                description, null, RecurrenceRule.medianoche(dia, zona), TransactionStatus.CONFIRMED,
                TransactionOrigin.WEB, id, null, null, null);
    }

    /** La fecha de la ocurrencia que la serie va a crear a continuacion. */
    public LocalDate siguienteACrear() {
        return rule.occurrence(generatedCount + 1);
    }

    /**
     * Cambia la periodicidad: la regla nueva empieza manana, y las ocurrencias de la actual con fecha de
     * hoy o antes pasan a priorCount. Las futuras las borra quien llama.
     */
    public Recurrence conRegla(RecurrenceRule nueva, LocalDate hoy) {
        int ocurridas = (int) IntStream.rangeClosed(1, generatedCount)
                .filter(k -> !rule.occurrence(k).isAfter(hoy))
                .count();
        return new Recurrence(id, userId, type, accountId, categoryId, amount, currencyCode, description,
                nueva.desde(hoy.plusDays(1)), endDate, occurrenceLimit, 0, priorCount + ocurridas, status);
    }

    public Recurrence conPlantilla(UUID cuenta, UUID categoria, BigDecimal monto, String descripcion) {
        return new Recurrence(id, userId, type, cuenta, categoria, monto, currencyCode, descripcion, rule, endDate,
                occurrenceLimit, generatedCount, priorCount, status);
    }
}
