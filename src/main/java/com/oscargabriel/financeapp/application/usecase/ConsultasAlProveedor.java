package com.oscargabriel.financeapp.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cuando se puede volver a consultar al proveedor de tasas (design.md de FA-121): una consulta exitosa
 * por dia de la app, y tras un fallo, esperar el intervalo. Vive en memoria: un reinicio lo borra.
 */
@Component
public class ConsultasAlProveedor {

    private record Intentos(LocalDate diaExitoso, Instant ultimoFallo) {
    }

    private final Clock clock;
    private final Duration reintentoTrasFallo;
    private final AtomicReference<Intentos> intentos = new AtomicReference<>(new Intentos(null, null));

    public ConsultasAlProveedor(Clock clock, @Value("${tasas.reintento-tras-fallo}") Duration reintentoTrasFallo) {
        this.clock = clock;
        this.reintentoTrasFallo = reintentoTrasFallo;
    }

    public boolean sePuedeConsultar() {
        Intentos actuales = intentos.get();
        boolean exitosaHoy = LocalDate.now(clock).equals(actuales.diaExitoso());
        boolean esperando = actuales.ultimoFallo() != null
                && clock.instant().isBefore(actuales.ultimoFallo().plus(reintentoTrasFallo));
        return !exitosaHoy && !esperando;
    }

    public void anotarExito() {
        LocalDate hoy = LocalDate.now(clock);
        intentos.updateAndGet(previos -> new Intentos(hoy, previos.ultimoFallo()));
    }

    public void anotarFallo() {
        Instant ahora = clock.instant();
        intentos.updateAndGet(previos -> new Intentos(previos.diaExitoso(), ahora));
    }
}
