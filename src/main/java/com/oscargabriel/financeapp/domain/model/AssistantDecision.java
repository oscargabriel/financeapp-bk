package com.oscargabriel.financeapp.domain.model;

/**
 * Lo que el modelo decidio hacer con un mensaje. Los argumentos viajan como texto, tal como los
 * escribio el modelo: el caso de uso los valida igual que el alta valida un cuerpo, porque el modelo
 * no pasa por ningun DTO.
 */
public sealed interface AssistantDecision {

    /** fecha es un dia (YYYY-MM-DD) o null para "ahora". */
    record CrearMovimiento(String tipo, String monto, String cuenta, String cuentaDestino, String categoria,
            String descripcion, String fecha) implements AssistantDecision {
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
