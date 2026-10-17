package com.oscargabriel.financeapp.telegram.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lo que el asistente contesto a un mensaje del chat, en los terminos del bot (design.md de FIN-61, decision 1):
 * el puente traduce la respuesta del asistente a uno de estos y nada mas.
 */
public sealed interface RespuestaDelAsistente {

    /** Una aclaracion o lo que el asistente no hace: se envia el texto tal cual. */
    record Texto(String mensaje) implements RespuestaDelAsistente {
    }

    /** Lo que registro, sin confirmar: el grupo que confirman o descartan los botones. */
    record Registro(UUID grupo, List<Movimiento> movimientos) implements RespuestaDelAsistente {

        public Registro {
            movimientos = List.copyOf(movimientos);
        }
    }

    /** Los totales en la moneda del usuario; cada movimiento en la suya. */
    record Movimientos(LocalDate desde, LocalDate hasta, String moneda, BigDecimal ingresos, BigDecimal gastos,
            BigDecimal neto, List<Movimiento> movimientos) implements RespuestaDelAsistente {

        public Movimientos {
            movimientos = List.copyOf(movimientos);
        }
    }

    record Saldo(LocalDate desde, LocalDate hasta, String moneda, BigDecimal ingresos, BigDecimal gastos,
            BigDecimal neto, BigDecimal netoHistorico, List<Cuenta> cuentas) implements RespuestaDelAsistente {

        public Saldo {
            cuentas = List.copyOf(cuentas);
        }
    }

    /** El archivo no es de un tipo que el asistente admite, o pasa del tope: el modelo no lo recibio. */
    record ArchivoNoAdmitido() implements RespuestaDelAsistente {
    }

    /** {@code tipo} es EXPENSE, INCOME o TRANSFER; {@code cuentaDestino} solo en una transferencia. */
    record Movimiento(String tipo, String cuenta, String cuentaDestino, String categoria, BigDecimal monto,
            String moneda, String descripcion, Instant fecha) {
    }

    record Cuenta(String nombre, String moneda, BigDecimal saldo) {
    }
}
