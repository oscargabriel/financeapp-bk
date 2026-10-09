package com.oscargabriel.financeapp.domain.port.out;

import reactor.core.publisher.Mono;

public interface RegistrationAllowlistPort {

    /**
     * TRUE si la lista de admitidos tiene el correo exacto o su dominio completo. Recibe el correo ya
     * normalizado (recortado y en minusculas), que es como se guarda la lista.
     */
    Mono<Boolean> isAllowed(String email);
}
