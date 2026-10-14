package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.util.List;

import com.oscargabriel.financeapp.core.domain.model.Sharing;

/**
 * Las piezas de lo compartido que llevan la respuesta de un movimiento y la fila del reporte (FIN-66): quien lo
 * compartio, el gasto del que sale la parte, y las partes del gasto de quien pago.
 */
public final class SharingResponse {

    private SharingResponse() {
    }

    public record Person(String email, String firstName, String lastName) {

        static Person from(Sharing.Person persona) {
            return persona == null ? null : new Person(persona.email(), persona.firstName(), persona.lastName());
        }
    }

    public record SharedFrom(String description, BigDecimal amount, String currencyCode) {

        static SharedFrom from(Sharing.Source origen) {
            return origen == null ? null : new SharedFrom(origen.description(), origen.amount(), origen.currencyCode());
        }
    }

    public record Share(String id, Person person, BigDecimal amount, String currencyCode, String status) {

        static List<Share> from(List<Sharing.Share> partes) {
            return partes == null ? null : partes.stream()
                    .map(p -> new Share(p.id().toString(), Person.from(p.person()), p.amount(), p.currencyCode(),
                            p.status().name()))
                    .toList();
        }
    }
}
