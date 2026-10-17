package com.oscargabriel.financeapp.core.application.usecase;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.CreateRecurrenceCommand;
import com.oscargabriel.financeapp.core.domain.model.Currency;
import com.oscargabriel.financeapp.core.domain.model.Frequency;
import com.oscargabriel.financeapp.core.domain.model.Recurrence;
import com.oscargabriel.financeapp.core.domain.model.RecurrenceRule;
import com.oscargabriel.financeapp.core.domain.model.RecurrenceStatus;
import com.oscargabriel.financeapp.core.domain.model.RecurrenceTemplateChange;
import com.oscargabriel.financeapp.core.domain.model.RecurrenceView;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.model.UpdateRecurrenceCommand;
import com.oscargabriel.financeapp.core.domain.model.UuidV7;
import com.oscargabriel.financeapp.core.domain.port.in.RecurrencePort;
import com.oscargabriel.financeapp.core.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.RecurrenceRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class RecurrenceUseCase implements RecurrencePort {

    private static final Comparator<RecurrenceView> POR_PROXIMA = Comparator.comparing(
            RecurrenceView::nextOccurrenceAt, Comparator.nullsLast(Comparator.naturalOrder()));

    private final AccountQueryPort cuentas;
    private final CategoryQueryPort categorias;
    private final CurrencyQueryPort monedas;
    private final UserRepositoryPort usuarios;
    private final RecurrenceRepositoryPort series;
    private final SeriesAlDia alDia;
    private final Clock clock;

    /**
     * La forma de la regla ya la valido el request. Aqui queda lo que necesita al usuario: sus cuentas y
     * categorias, las monedas activas, y su zona, que decide que es "hoy" y a que instante cae cada ocurrencia.
     */
    @Override
    public Mono<RecurrenceView> create(UUID userId, CreateRecurrenceCommand alta) {
        return Mono.zip(ReferenciasDelUsuario.de(userId, cuentas, categorias),
                        monedas.findActive().collectMap(Currency::code), ZonaDelUsuario.de(usuarios, userId))
                .map(t -> preparar(userId, alta, t.getT1(), t.getT2(), t.getT3()))
                .flatMap(nueva -> series.save(nueva.serie(), nueva.ocurrencias()).thenReturn(nueva.vista()));
    }

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
                .flatMap(vistas -> vistas.stream().anyMatch(RecurrenceUseCase::sinProxima)
                        ? ZonaDelUsuario.de(usuarios, userId).map(zona -> completar(vistas, zona))
                        : Mono.just(vistas))
                .flatMapIterable(vistas -> vistas);
    }

    /**
     * El formato de cada campo ya lo valido el request, y que el parche traiga algo, el controlador. Aqui
     * se aplica sobre la serie guardada y se valida la resultante. El repositorio vuelve a filtrar por
     * usuario y estado al escribir: si la cancelaron en medio, tambien es 404.
     */
    @Override
    public Mono<RecurrenceView> update(UUID userId, UUID recurrenceId, UpdateRecurrenceCommand parche) {
        return series.findActiveByIdAndUser(recurrenceId, userId)
                .switchIfEmpty(Mono.error(RecurrenceUseCase::noEncontrada))
                .zipWith(Mono.zip(ReferenciasDelUsuario.de(userId, cuentas, categorias),
                        ZonaDelUsuario.de(usuarios, userId)))
                .map(t -> new Edicion(t.getT2().getT1(), t.getT2().getT2(), clock.instant())
                        .aplicar(t.getT1(), parche))
                .flatMap(hecha -> series.update(hecha.serie(), parche.scope(), hecha.ahora(), hecha.cambios(),
                                hecha.rehechas())
                        .flatMap(actualizada -> actualizada
                                ? vista(hecha)
                                : Mono.error(noEncontrada())));
    }

    /** Una sola escritura filtrada por usuario y estado: cero filas es 404, sin una lectura previa. */
    @Override
    public Mono<Void> cancel(UUID userId, UUID recurrenceId) {
        return Mono.defer(() -> series.cancel(recurrenceId, userId, clock.instant()))
                .flatMap(cancelada -> cancelada
                        ? Mono.<Void>empty()
                        : Mono.error(noEncontrada()));
    }

    /** La serie toma la moneda de su cuenta, que ya no cambia (FA-123). */
    private Nueva preparar(UUID userId, CreateRecurrenceCommand alta, ReferenciasDelUsuario referencias,
            Map<String, Currency> activas, ZoneId zona) {
        List<ErrorDetail> errores = new ArrayList<>();
        UUID cuenta = referencias.cuentaEnMonedaActiva(alta.accountId(), "accountId", activas, errores);
        UUID categoria = referencias.categoria(alta.categoryId(), alta.type(), "categoryId", errores);
        if (!errores.isEmpty()) {
            throw new BadRequestException(HttpStatus.BAD_REQUEST, errores);
        }

        Instant ahora = clock.instant();
        Recurrence serie = new Recurrence(UuidV7.from(ahora), userId, alta.type(), cuenta, categoria, alta.amount(),
                referencias.moneda(cuenta), alta.description().trim(),
                new RecurrenceRule(alta.frequency(), alta.interval(), alta.dayOfWeek(), alta.dayOfMonth(),
                        alta.startDate()),
                alta.endDate(), alta.occurrences(), 0, 0, RecurrenceStatus.ACTIVE);

        List<LocalDate> fechas = serie.pendientes(LocalDate.ofInstant(ahora, zona));
        validarCantidad(serie, fechas);
        List<Transaction> ocurrencias = fechas.stream()
                .map(fecha -> serie.ocurrencia(UuidV7.from(ahora), fecha, zona))
                .toList();
        Recurrence creada = serie.conGeneradas(fechas.size());
        return new Nueva(creada, ocurrencias, new RecurrenceView(creada, proxima(ocurrencias, ahora)));
    }

    /** El tope de una serie sin fin se pasa por empezar muy atras; el de una con fin, por terminar muy lejos. */
    private static void validarCantidad(Recurrence serie, List<LocalDate> fechas) {
        if (fechas.size() > Recurrence.TOPE) {
            throw serie.openEnded()
                    ? invalido("La serie crearia mas de " + Recurrence.TOPE
                            + " ocurrencias hasta hoy: acerca la fecha de inicio", "startDate")
                    : invalido("La serie crearia mas de " + Recurrence.TOPE
                            + " ocurrencias: acerca la fecha de fin", "endDate");
        }
        if (fechas.isEmpty()) {
            throw invalido("La serie no tiene ninguna ocurrencia antes de su fecha de fin", "endDate");
        }
    }

    /** La primera ocurrencia posterior a ahora; null si todas ya ocurrieron. */
    private static Instant proxima(List<Transaction> ocurrencias, Instant ahora) {
        return ocurrencias.stream()
                .map(Transaction::occurredAt)
                .filter(instante -> instante.isAfter(ahora))
                .min(Instant::compareTo)
                .orElse(null);
    }

    private static BadRequestException invalido(String descripcion, String campo) {
        return new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR, descripcion, campo);
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

    /**
     * Si se rehicieron las futuras, la proxima esta entre ellas; si no, es la existente mas proxima. Una
     * sin fin sin ninguna existente tiene como proxima la que va a crear.
     */
    private Mono<RecurrenceView> vista(Hecha hecha) {
        Recurrence serie = hecha.serie();
        if (hecha.rehechas() != null) {
            return Mono.just(new RecurrenceView(serie, proxima(hecha.rehechas(), hecha.ahora())));
        }
        return series.findNextOccurrence(serie.id())
                .map(proxima -> new RecurrenceView(serie, proxima))
                .defaultIfEmpty(new RecurrenceView(serie, serie.openEnded()
                        ? RecurrenceRule.medianoche(serie.siguienteACrear(), hecha.zona())
                        : null));
    }

    /** Inexistente, ajena y cancelada dan lo mismo: distinguirlas confirmaria que el id existe. */
    private static BadRequestException noEncontrada() {
        return new BadRequestException(HttpStatus.NOT_FOUND, List.of(
                ErrorDetail.of(ErrorCodes.NOT_FOUND.getCode(), "La serie no existe", "id")));
    }

    private record Nueva(Recurrence serie, List<Transaction> ocurrencias, RecurrenceView vista) {
    }

    /** rehechas es null si la periodicidad no cambio: entonces no se borra ni se crea ninguna ocurrencia. */
    private record Hecha(Recurrence serie, RecurrenceTemplateChange cambios, List<Transaction> rehechas,
            Instant ahora, ZoneId zona) {
    }

    /** Aplica un parche sobre la serie guardada y acumula todos sus errores antes de lanzar. */
    private static final class Edicion {

        private final ReferenciasDelUsuario referencias;
        private final ZoneId zona;
        private final Instant ahora;
        private final List<ErrorDetail> errores = new ArrayList<>();

        Edicion(ReferenciasDelUsuario referencias, ZoneId zona, Instant ahora) {
            this.referencias = referencias;
            this.zona = zona;
            this.ahora = ahora;
        }

        Hecha aplicar(Recurrence guardada, UpdateRecurrenceCommand parche) {
            UUID cuenta = parche.accountId() == null
                    ? null
                    : referencias.cuentaEnMoneda(parche.accountId(), "accountId", guardada.currencyCode(), "la serie",
                            errores);
            UUID categoria = parche.categoryId() == null
                    ? null
                    : referencias.categoria(parche.categoryId(), guardada.type(), "categoryId", errores);
            String descripcion = parche.description() == null ? null : parche.description().trim();
            RecurrenceRule regla = regla(guardada.rule(), parche);

            if (!errores.isEmpty()) {
                throw new BadRequestException(HttpStatus.BAD_REQUEST, errores);
            }

            RecurrenceTemplateChange cambios = new RecurrenceTemplateChange(cuenta, categoria, parche.amount(),
                    descripcion);
            Recurrence editada = guardada.conPlantilla(
                    Objects.requireNonNullElse(cuenta, guardada.accountId()),
                    Objects.requireNonNullElse(categoria, guardada.categoryId()),
                    Objects.requireNonNullElse(parche.amount(), guardada.amount()),
                    Objects.requireNonNullElse(descripcion, guardada.description()));
            if (mismaRegla(guardada.rule(), regla)) {
                return new Hecha(editada, cambios, null, ahora, zona);
            }
            return rehacer(editada.conRegla(regla, LocalDate.ofInstant(ahora, zona)), cambios);
        }

        /**
         * La regla resultante: lo que no trae el parche se hereda de la guardada, salvo el dia que deja de
         * aplicar al cambiar de frecuencia. Un dia enviado que no aplica si es error.
         */
        private RecurrenceRule regla(RecurrenceRule guardada, UpdateRecurrenceCommand parche) {
            Frequency frecuencia = Objects.requireNonNullElse(parche.frequency(), guardada.frequency());
            int interval = Objects.requireNonNullElse(parche.interval(), guardada.interval());
            boolean misma = frecuencia == guardada.frequency();
            DayOfWeek diaSemana = parche.dayOfWeek() != null || !misma ? parche.dayOfWeek() : guardada.dayOfWeek();
            Integer diaMes = parche.dayOfMonth() != null || !misma ? parche.dayOfMonth() : guardada.dayOfMonth();
            errores.addAll(RecurrenceRule.errores(frecuencia, interval, diaSemana, diaMes));
            return new RecurrenceRule(frecuencia, interval, diaSemana, diaMes, guardada.startDate());
        }

        /** Las futuras se borran y se crean de nuevo con la regla nueva, que empieza manana. */
        private Hecha rehacer(Recurrence serie, RecurrenceTemplateChange cambios) {
            List<LocalDate> fechas = serie.pendientes(LocalDate.ofInstant(ahora, zona));
            if (fechas.size() > Recurrence.TOPE) {
                throw invalido("La serie crearia mas de " + Recurrence.TOPE
                        + " ocurrencias con la regla nueva: acerca la fecha de fin", "endDate");
            }
            List<Transaction> rehechas = fechas.stream()
                    .map(fecha -> serie.ocurrencia(UuidV7.from(ahora), fecha, zona))
                    .toList();
            return new Hecha(serie.conGeneradas(rehechas.size()), cambios, rehechas, ahora, zona);
        }

        private static boolean mismaRegla(RecurrenceRule guardada, RecurrenceRule nueva) {
            return guardada.frequency() == nueva.frequency()
                    && guardada.interval() == nueva.interval()
                    && Objects.equals(guardada.dayOfWeek(), nueva.dayOfWeek())
                    && Objects.equals(guardada.dayOfMonth(), nueva.dayOfMonth());
        }
    }
}
