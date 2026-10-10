package com.oscargabriel.financeapp.application.usecase;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.Frequency;
import com.oscargabriel.financeapp.domain.model.Recurrence;
import com.oscargabriel.financeapp.domain.model.RecurrenceRule;
import com.oscargabriel.financeapp.domain.model.RecurrenceTemplateChange;
import com.oscargabriel.financeapp.domain.model.RecurrenceView;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.UpdateRecurrenceCommand;
import com.oscargabriel.financeapp.domain.model.UuidV7;
import com.oscargabriel.financeapp.domain.port.in.UpdateRecurrencePort;
import com.oscargabriel.financeapp.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.domain.port.out.RecurrenceRepositoryPort;
import com.oscargabriel.financeapp.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class UpdateRecurrenceUseCase implements UpdateRecurrencePort {

    private final AccountQueryPort cuentas;
    private final CategoryQueryPort categorias;
    private final UserRepositoryPort usuarios;
    private final RecurrenceRepositoryPort series;
    private final Clock clock;

    /**
     * El formato de cada campo ya lo valido el request, y que el parche traiga algo, el controlador. Aqui
     * se aplica sobre la serie guardada y se valida la resultante. El repositorio vuelve a filtrar por
     * usuario y estado al escribir: si la cancelaron en medio, tambien es 404.
     */
    @Override
    public Mono<RecurrenceView> update(UUID userId, UUID recurrenceId, UpdateRecurrenceCommand parche) {
        return series.findActiveByIdAndUser(recurrenceId, userId)
                .switchIfEmpty(Mono.error(UpdateRecurrenceUseCase::noEncontrada))
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

    /**
     * Si se rehicieron las futuras, la proxima esta entre ellas; si no, es la existente mas proxima. Una
     * sin fin sin ninguna existente tiene como proxima la que va a crear.
     */
    private Mono<RecurrenceView> vista(Hecha hecha) {
        Recurrence serie = hecha.serie();
        if (hecha.rehechas() != null) {
            return Mono.just(new RecurrenceView(serie, CreateRecurrenceUseCase.proxima(hecha.rehechas(), hecha.ahora())));
        }
        return series.findNextOccurrence(serie.id())
                .map(proxima -> new RecurrenceView(serie, proxima))
                .defaultIfEmpty(new RecurrenceView(serie, serie.openEnded()
                        ? RecurrenceRule.medianoche(serie.siguienteACrear(), hecha.zona())
                        : null));
    }

    /** Inexistente, ajena y cancelada dan lo mismo: distinguirlas confirmaria que el id existe. */
    static BadRequestException noEncontrada() {
        return new BadRequestException(HttpStatus.NOT_FOUND, List.of(
                ErrorDetail.of(ErrorCodes.NOT_FOUND.getCode(), "La serie no existe", "id")));
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
                    : referencias.cuentaPropia(parche.accountId(), "accountId", "La cuenta", errores);
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
                throw CreateRecurrenceUseCase.invalido("La serie crearia mas de " + Recurrence.TOPE
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
