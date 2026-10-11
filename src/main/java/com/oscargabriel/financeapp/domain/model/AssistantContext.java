package com.oscargabriel.financeapp.domain.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Lo que el modelo sabe del usuario para elegir: nombres, nunca ids ni saldos (design.md de FA-77,
 * decision 3).
 */
public record AssistantContext(LocalDate hoy, List<String> cuentas, List<CategoriaConAmbito> categorias) {

    public AssistantContext {
        cuentas = List.copyOf(cuentas);
        categorias = List.copyOf(categorias);
    }

    public record CategoriaConAmbito(String nombre, CategoryScope ambito) {
    }
}
