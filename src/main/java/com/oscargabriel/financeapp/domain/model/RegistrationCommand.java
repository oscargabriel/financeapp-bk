package com.oscargabriel.financeapp.domain.model;

/**
 * Datos crudos del alta, tal como llegan del cliente y antes de validarse. Es el unico tipo del
 * dominio que transporta la contrasena en claro.
 *
 * baseCurrencyCode y timezone admiten null: el caso de uso completa los que falten con los mismos
 * valores que finance.users declara como DEFAULT.
 */
public record RegistrationCommand(
        String email,
        String password,
        String firstName,
        String lastName,
        String baseCurrencyCode,
        String timezone) {

    public static final String MONEDA_POR_DEFECTO = "COP";
    public static final String ZONA_POR_DEFECTO = "America/Bogota";

    public String baseCurrencyCodeOrDefault() {
        return baseCurrencyCode == null || baseCurrencyCode.isBlank()
                ? MONEDA_POR_DEFECTO
                : baseCurrencyCode.trim().toUpperCase();
    }

    public String timezoneOrDefault() {
        return timezone == null || timezone.isBlank() ? ZONA_POR_DEFECTO : timezone.trim();
    }
}
