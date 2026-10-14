package com.oscargabriel.financeapp.ia.domain.model;

import java.util.List;

/**
 * Lo que el modelo decidio hacer con un mensaje. Los argumentos viajan como texto, tal como los
 * escribio el modelo: el caso de uso los valida igual que el alta valida un cuerpo, porque el modelo
 * no pasa por ningun DTO.
 */
public sealed interface AssistantDecision {

    /** Uno o varios movimientos de un mismo mensaje, en el orden de las llamadas: se registran juntos (FIN-58). */
    record CrearMovimientos(List<CrearMovimiento> movimientos) implements AssistantDecision {
    }

    /** Una llamada a crear_movimiento. fecha es un dia (YYYY-MM-DD) o null para "ahora". */
    record CrearMovimiento(String tipo, String monto, String cuenta, String cuentaDestino, String categoria,
            String descripcion, String fecha) {
    }

    /** desde y hasta, los dos o ninguno; los filtros en null no filtran. */
    record ConsultarMovimientos(String desde, String hasta, String tipo, String categoria, String cuenta)
            implements AssistantDecision {
    }

    record ConsultarSaldo(String desde, String hasta) implements AssistantDecision {
    }

    /** El modelo respondio texto o pidio una funcion que no existe: no se hace nada. */
    record SinFuncion() implements AssistantDecision {
    }
}
