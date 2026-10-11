package com.oscargabriel.financeapp.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.InstallmentRef;

import io.r2dbc.spi.Row;

/**
 * La cuota de un movimiento leida de una consulta que trae t.installment_* y el installment_count de su
 * compra con un LEFT JOIN (FA-108). Lo comparten las lecturas de movimientos y el reporte.
 */
final class CuotaDeLaFila {

    /** Las columnas, sobre el alias t de transactions y ip de installment_purchases. */
    static final String COLUMNAS = """
            t.installment_purchase_id, t.installment_number, t.installment_principal, ip.installment_count""";

    static final String JOIN = """
            LEFT JOIN finance.installment_purchases ip ON ip.id = t.installment_purchase_id""";

    private CuotaDeLaFila() {
    }

    static InstallmentRef de(Row row) {
        UUID compra = row.get("installment_purchase_id", UUID.class);
        if (compra == null) {
            return null;
        }
        return new InstallmentRef(compra, row.get("installment_number", Number.class).intValue(),
                row.get("installment_count", Number.class).intValue(),
                row.get("installment_principal", BigDecimal.class));
    }
}