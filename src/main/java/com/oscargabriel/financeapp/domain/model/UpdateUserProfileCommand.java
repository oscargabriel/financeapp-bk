package com.oscargabriel.financeapp.domain.model;

/**
 * El parche del perfil tal como llega, antes de normalizarse. Null no cambia; lastName y phone en
 * blanco se borran. currentPassword solo se mira si el correo cambia.
 */
public record UpdateUserProfileCommand(
        String firstName,
        String lastName,
        String email,
        String phone,
        String timezone,
        String currentPassword) {
}
