package com.oscargabriel.financeapp.ia.domain.model;

import java.util.UUID;

/** Una categoria viva del usuario, con el tipo de movimiento para el que sirve. */
public record UserCategory(UUID id, String name, CategoryScope appliesTo) {
}
