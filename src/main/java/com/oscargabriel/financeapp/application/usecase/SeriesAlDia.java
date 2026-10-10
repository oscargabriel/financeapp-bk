package com.oscargabriel.financeapp.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.domain.model.Recurrence;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.UuidV7;
import com.oscargabriel.financeapp.domain.port.out.RecurrenceRepositoryPort;
import com.oscargabriel.financeapp.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Crea las ocurrencias que les faltan a las series sin fin del usuario (FA-107). No hay proceso que
 * las cree al llegar su fecha, porque el servicio puede estar apagado: toda lectura de saldos o de
 * movimientos llama a esto antes de consultar. Una lectura nueva de ese tipo tiene que llamarlo
 * tambien, o mostrara los saldos sin las ocurrencias atrasadas.
 */
@Component
@AllArgsConstructor
public class SeriesAlDia {

    private final RecurrenceRepositoryPort series;
    private final UserRepositoryPort usuarios;
    private final Clock clock;

    /** Sin series sin fin cuesta una consulta, y el perfil solo se lee si hay alguna. */
    public Mono<Void> ponerAlDia(UUID userId) {
        return series.findOpenActiveByUser(userId)
                .collectList()
                .filter(abiertas -> !abiertas.isEmpty())
                .flatMap(abiertas -> ZonaDelUsuario.de(usuarios, userId)
                        .flatMapMany(zona -> Flux.fromIterable(abiertas).concatMap(serie -> ponerAlDia(serie, zona)))
                        .then());
    }

    /**
     * El repositorio solo escribe si el contador sigue en el que se leyo: si otra peticion la puso al dia
     * en medio, no se inserta nada y no es un error. Una serie que lleva anos sin leerse avanza como
     * mucho el tope por lectura; la siguiente sigue desde ahi.
     */
    private Mono<Boolean> ponerAlDia(Recurrence serie, ZoneId zona) {
        Instant ahora = clock.instant();
        List<Transaction> nuevas = serie.pendientes(LocalDate.ofInstant(ahora, zona)).stream()
                .limit(Recurrence.TOPE)
                .map(fecha -> serie.ocurrencia(UuidV7.from(ahora), fecha, zona))
                .toList();
        if (nuevas.isEmpty()) {
            return Mono.empty();
        }
        return series.append(serie.conGeneradas(nuevas.size()), serie.generatedCount(), nuevas);
    }
}
