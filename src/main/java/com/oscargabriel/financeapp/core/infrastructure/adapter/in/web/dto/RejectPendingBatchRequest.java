package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.validation.Formatos;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.validation.SinRepetidos;

/**
 * Los pendientes a rechazar en una peticion (FIN-68). Los errores de un id salen sobre su posicion, ids[i]; los del
 * lote entero, sobre ids. Si cada id es un pendiente del usuario lo decide el caso de uso.
 */
public record RejectPendingBatchRequest(

        @NotNull(message = "Los ids son obligatorios")
        @Size(min = 1, max = 100, message = "El lote lleva entre 1 y 100 ids")
        @SinRepetidos
        List<@NotBlank(message = "Cada id es obligatorio")
                @Pattern(regexp = Formatos.UUID, message = "Cada id debe ser un UUID") String> ids) {

    public List<UUID> toIds() {
        return ids.stream().map(id -> UUID.fromString(id.trim())).toList();
    }
}
