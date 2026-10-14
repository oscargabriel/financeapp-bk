package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto;

import com.oscargabriel.financeapp.core.domain.model.AffiliationView;

/**
 * Una relacion de afiliacion (FIN-65), vista por quien la pide. person es la otra persona; su nombre
 * va en null en una enviada que sigue REQUESTED.
 */
public record AffiliationResponse(
        String id,
        String status,
        String direction,
        Person person,
        String createdAt,
        String updatedAt) {

    public record Person(String email, String firstName, String lastName) {
    }

    public static AffiliationResponse from(AffiliationView vista) {
        return new AffiliationResponse(
                vista.id().toString(),
                vista.status().name(),
                vista.direction().name(),
                new Person(vista.email(), vista.firstName(), vista.lastName()),
                vista.createdAt().toString(),
                vista.updatedAt().toString());
    }
}
