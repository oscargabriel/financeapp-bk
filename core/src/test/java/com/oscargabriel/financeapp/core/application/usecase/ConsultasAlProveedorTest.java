package com.oscargabriel.financeapp.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class ConsultasAlProveedorTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Duration UNA_HORA = Duration.ofHours(1);

    /** 10:00 del 10 de octubre en Bogota. */
    private final RelojMovible reloj = new RelojMovible(Instant.parse("2026-10-10T15:00:00Z"));
    private final ConsultasAlProveedor consultas = new ConsultasAlProveedor(reloj, UNA_HORA);

    @Test
    void sinIntentosSePuedeConsultar() {
        assertThat(consultas.sePuedeConsultar()).isTrue();
    }

    @Test
    void trasUnExitoNoSeConsultaElRestoDelDia() {
        consultas.anotarExito();
        reloj.avanzar(Duration.ofHours(13).plusMinutes(59));

        assertThat(consultas.sePuedeConsultar()).isFalse();
    }

    @Test
    void trasUnExitoSeConsultaAlDiaSiguiente() {
        consultas.anotarExito();
        reloj.avanzar(Duration.ofHours(14));

        assertThat(consultas.sePuedeConsultar()).isTrue();
    }

    @Test
    void trasUnFalloNoSeConsultaDentroDelIntervalo() {
        consultas.anotarFallo();
        reloj.avanzar(Duration.ofMinutes(59));

        assertThat(consultas.sePuedeConsultar()).isFalse();
    }

    @Test
    void trasUnFalloSeConsultaAlCumplirseElIntervalo() {
        consultas.anotarFallo();
        reloj.avanzar(UNA_HORA);

        assertThat(consultas.sePuedeConsultar()).isTrue();
    }

    @Test
    void unFalloDespuesDeUnExitoDeAyerSigueBloqueandoElIntervalo() {
        consultas.anotarExito();
        reloj.avanzar(Duration.ofDays(1));
        consultas.anotarFallo();
        reloj.avanzar(Duration.ofMinutes(30));

        assertThat(consultas.sePuedeConsultar()).isFalse();
    }

    /** A las 19:30 en Bogota ya es el dia siguiente en UTC: el corte lo pone la zona de la app. */
    @Test
    void elCambioDeDiaSeMideEnLaZonaDelReloj() {
        consultas.anotarExito();
        reloj.avanzar(Duration.ofHours(9).plusMinutes(30));

        assertThat(consultas.sePuedeConsultar()).isFalse();
    }

    private static final class RelojMovible extends Clock {

        private Instant ahora;

        RelojMovible(Instant inicio) {
            this.ahora = inicio;
        }

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        @Override
        public ZoneId getZone() {
            return BOGOTA;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }
    }
}
