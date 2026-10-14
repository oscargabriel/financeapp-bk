package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.validation.Formatos;

/** Cuerpo de una solicitud de afiliacion. Las mismas reglas de formato que el correo del registro. */
public record SendAffiliationRequest(

        @NotBlank(message = "El correo es obligatorio")
        @Size(max = 255, message = "El correo no tiene un formato valido")
        @Pattern(regexp = Formatos.EMAIL, message = "El correo no tiene un formato valido")
        String email) {
}
