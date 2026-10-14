package com.oscargabriel.financeapp.core.domain.model;

/** El registro con Google (FIN-52): el ID token y, opcional, la zona del navegador. */
public record GoogleRegistrationCommand(String idToken, String timezone) {

    public String timezoneOrDefault() {
        return timezone == null || timezone.isBlank() ? RegistrationCommand.ZONA_POR_DEFECTO : timezone.trim();
    }
}
