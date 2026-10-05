package com.oscargabriel.financeapp.infrastructure.config.startup;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Hace que falte DB_USERNAME o DB_PASSWORD aborte el arranque nombrando la variable, como el resto
 * de secretos (FA-53). El binder que llena las propiedades de R2DBC deja pasar el placeholder sin
 * resolver como texto literal, y el driver intentaria autenticarse con el usuario "${DB_USERNAME}":
 * el fallo llegaria tarde y con un mensaje de red. El @Value, en cambio, no tolera un placeholder
 * sin resolver. Por eso este bean solo pide las dos propiedades y no hace nada con ellas.
 *
 * Pide las propiedades y no las variables, para que un perfil que fije los valores, como local,
 * arranque sin ellas. No depende de startup.db-check.
 */
@Component
public class R2dbcCredentialsCheck {

    public R2dbcCredentialsCheck(
            @Value("${spring.r2dbc.username}") String username,
            @Value("${spring.r2dbc.password}") String password) {
    }
}
