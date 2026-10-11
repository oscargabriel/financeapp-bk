package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import com.oscargabriel.financeapp.domain.model.UserProfile;

/**
 * El perfil en las respuestas de GET y PATCH /users/me. Se construye campo por campo, como
 * RegisterUserResponse, para que una columna nueva del usuario no salga sin que nadie lo decida.
 */
public record UserProfileResponse(
        String id,
        String email,
        String firstName,
        String lastName,
        String phone,
        String baseCurrencyCode,
        String timezone) {

    public static UserProfileResponse from(UserProfile perfil) {
        return new UserProfileResponse(
                perfil.id().toString(),
                perfil.email(),
                perfil.firstName(),
                perfil.lastName(),
                perfil.phone(),
                perfil.baseCurrencyCode(),
                perfil.timezone());
    }
}
