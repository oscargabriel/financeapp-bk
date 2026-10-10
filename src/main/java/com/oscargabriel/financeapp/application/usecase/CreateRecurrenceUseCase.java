package com.oscargabriel.financeapp.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.CreateRecurrenceCommand;
import com.oscargabriel.financeapp.domain.model.Recurrence;
import com.oscargabriel.financeapp.domain.model.RecurrenceRule;
import com.oscargabriel.financeapp.domain.model.RecurrenceStatus;
import com.oscargabriel.financeapp.domain.model.RecurrenceView;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.UuidV7;
import com.oscargabriel.financeapp.domain.port.in.CreateRecurrencePort;
import com.oscargabriel.financeapp.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.domain.port.out.RecurrenceRepositoryPort;
import com.oscargabriel.financeapp.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class CreateRecurrenceUseCase implements CreateRecurrencePort {

    /** La limitacion de las series que FA-51 no levanta; la comparte el PATCH de la serie. */
    static final String SOLO_EN_COP = "Por ahora las series solo admiten cuentas en COP";

    private final AccountQueryPort cuentas;
    private final CategoryQueryPort categorias;
    private final UserRepositoryPort usuarios;
    private final RecurrenceRepositoryPort series;
    private final Clock clock;

    /**
     * La forma de la regla ya la valido el request. Aqui queda lo que necesita al usuario: sus cuentas y
     * categorias, y su zona, que decide que es "hoy" y a que instante cae cada ocurrencia.
     */
    @Override
    public Mono<RecurrenceView> create(UUID userId, CreateRecurrenceCommand alta) {
        return Mono.zip(ReferenciasDelUsuario.de(userId, cuentas, categorias), ZonaDelUsuario.de(usuarios, userId))
                .map(t -> preparar(userId, alta, t.getT1(), t.getT2()))
                .flatMap(nueva -> series.save(nueva.serie(), nueva.ocurrencias()).thenReturn(nueva.vista()));
    }

    private Nueva preparar(UUID userId, CreateRecurrenceCommand alta, ReferenciasDelUsuario referencias,
            ZoneId zona) {
        List<ErrorDetail> errores = new ArrayList<>();
        UUID cuenta = referencias.cuentaEnCop(alta.accountId(), "accountId", SOLO_EN_COP, errores);
        UUID categoria = referencias.categoria(alta.categoryId(), alta.type(), "categoryId", errores);
        if (!errores.isEmpty()) {
            throw new BadRequestException(HttpStatus.BAD_REQUEST, errores);
        }

        Instant ahora = clock.instant();
        Recurrence serie = new Recurrence(UuidV7.from(ahora), userId, alta.type(), cuenta, categoria, alta.amount(),
                ReferenciasDelUsuario.MONEDA_UNICA, alta.description().trim(),
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
    static Instant proxima(List<Transaction> ocurrencias, Instant ahora) {
        return ocurrencias.stream()
                .map(Transaction::occurredAt)
                .filter(instante -> instante.isAfter(ahora))
                .min(Instant::compareTo)
                .orElse(null);
    }

    static BadRequestException invalido(String descripcion, String campo) {
        return new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR, descripcion, campo);
    }

    private record Nueva(Recurrence serie, List<Transaction> ocurrencias, RecurrenceView vista) {
    }
}
