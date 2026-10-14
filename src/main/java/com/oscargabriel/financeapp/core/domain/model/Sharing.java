package com.oscargabriel.financeapp.core.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Lo compartido de un movimiento, visto por su dueno (FIN-66). sharedBy es quien se lo compartio; sharedFrom, el
 * gasto del que sale la parte, en la moneda de la parte y mientras exista; shares, las partes del gasto de quien
 * pago. Cada uno es null cuando no aplica.
 */
public record Sharing(Person sharedBy, Source sharedFrom, List<Share> shares) {

    public static final Sharing NONE = new Sharing(null, null, null);

    /** Otra persona, con su correo y nombre actuales. */
    public record Person(String email, String firstName, String lastName) {
    }

    /** La descripcion con que se compartio y el total del gasto. */
    public record Source(String description, BigDecimal amount, String currencyCode) {
    }

    public record Share(UUID id, Person person, BigDecimal amount, String currencyCode, ShareStatus status) {
    }
}
