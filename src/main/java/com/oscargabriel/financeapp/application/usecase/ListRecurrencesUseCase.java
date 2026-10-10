package com.oscargabriel.financeapp.application.usecase;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.model.RecurrenceRule;
import com.oscargabriel.financeapp.domain.model.RecurrenceView;
import com.oscargabriel.financeapp.domain.port.in.ListRecurrencesPort;
import com.oscargabriel.financeapp.domain.port.out.RecurrenceRepositoryPort;
import com.oscargabriel.financeapp.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class ListRecurrencesUseCase implements ListRecurrencesPort {

    private static final Comparator<RecurrenceView> POR_PROXIMA = Comparator.comparing(
            RecurrenceView::nextOccurrenceAt, Comparator.nullsLast(Comparator.naturalOrder()));

    private final RecurrenceRepositoryPort series;
    private final UserRepositoryPort usuarios;
    private final SeriesAlDia alDia;

    /**
     * Pone al dia antes de leer, para que la proxima de una serie sin fin no sea una que ya paso. Una
     * sin fin cuya proxima borro el usuario a mano no tiene ninguna futura: su proxima es la que va a
     * crear, y solo entonces hace falta la zona del perfil.
     */
    @Override
    public Flux<RecurrenceView> list(UUID userId) {
        return alDia.ponerAlDia(userId)
                .thenMany(Flux.defer(() -> series.findActiveByUser(userId)))
                .collectList()
                .flatMap(vistas -> vistas.stream().anyMatch(ListRecurrencesUseCase::sinProxima)
                        ? ZonaDelUsuario.de(usuarios, userId).map(zona -> completar(vistas, zona))
                        : Mono.just(vistas))
                .flatMapIterable(vistas -> vistas);
    }

    private static boolean sinProxima(RecurrenceView vista) {
        return vista.nextOccurrenceAt() == null && vista.recurrence().openEnded();
    }

    private static List<RecurrenceView> completar(List<RecurrenceView> vistas, ZoneId zona) {
        return vistas.stream()
                .map(vista -> sinProxima(vista)
                        ? new RecurrenceView(vista.recurrence(),
                                RecurrenceRule.medianoche(vista.recurrence().siguienteACrear(), zona))
                        : vista)
                .sorted(POR_PROXIMA)
                .toList();
    }
}
