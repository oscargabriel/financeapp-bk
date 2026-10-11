package com.oscargabriel.financeapp.infrastructure.adapter.out.assistant;

import com.oscargabriel.financeapp.domain.model.AssistantDecision;

import tools.jackson.databind.JsonNode;

/**
 * Traduce una llamada de funcion a la decision del dominio. La comparten Gemini y el stub, para que
 * Bruno ejerza la misma traduccion que produccion. Una funcion que no sea de las tres no hace nada.
 */
final class FuncionesDelAsistente {

    private FuncionesDelAsistente() {
    }

    static boolean existe(String nombre) {
        return switch (nombre) {
            case "crear_movimiento", "consultar_movimientos", "consultar_saldo" -> true;
            default -> false;
        };
    }

    static AssistantDecision decision(String nombre, JsonNode args) {
        return switch (nombre) {
            case "crear_movimiento" -> new AssistantDecision.CrearMovimiento(texto(args, "tipo"),
                    texto(args, "monto"), texto(args, "cuenta"), texto(args, "cuenta_destino"),
                    texto(args, "categoria"), texto(args, "descripcion"), texto(args, "fecha"));
            case "consultar_movimientos" -> new AssistantDecision.ConsultarMovimientos(texto(args, "desde"),
                    texto(args, "hasta"), texto(args, "tipo"), texto(args, "categoria"), texto(args, "cuenta"));
            case "consultar_saldo" -> new AssistantDecision.ConsultarSaldo(texto(args, "desde"), texto(args, "hasta"));
            default -> new AssistantDecision.SinFuncion();
        };
    }

    /** Un numero sale en notacion plana: el modelo manda 20000 y el caso de uso lo parsea igual que un texto. */
    private static String texto(JsonNode args, String campo) {
        JsonNode valor = args.path(campo);
        if (valor.isMissingNode() || valor.isNull()) {
            return null;
        }
        return valor.isNumber() ? valor.decimalValue().toPlainString() : valor.asString();
    }
}
