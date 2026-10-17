package com.oscargabriel.financeapp.telegram.application.usecase;

import static com.oscargabriel.financeapp.telegram.support.TelegramMother.gasto;
import static com.oscargabriel.financeapp.telegram.support.TelegramMother.transferencia;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.oscargabriel.financeapp.telegram.domain.model.RespuestaDelAsistente;

class TextosDelBotTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final UUID GRUPO = UUID.fromString("71000000-0000-7000-8000-000000000001");
    private static final LocalDate DESDE = LocalDate.of(2026, 10, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 10, 31);

    private static RespuestaDelAsistente.Movimientos consulta(List<RespuestaDelAsistente.Movimiento> lista) {
        return new RespuestaDelAsistente.Movimientos(DESDE, HASTA, "COP", BigDecimal.ZERO, new BigDecimal("45000"),
                new BigDecimal("-45000"), lista);
    }

    @Test
    void unGastoRegistradoMuestraCadaCampoConLaFechaEnLaZonaDelUsuario() {
        String texto = TextosDelBot.registro(new RespuestaDelAsistente.Registro(GRUPO, List.of(gasto("Almuerzo"))),
                BOGOTA);

        assertThat(texto).isEqualTo("""
                Registré 1 movimiento sin confirmar:

                1. Gasto
                Cuenta: Billetera
                Categoría: Mercado
                Monto: 45.000 COP
                Descripción: Almuerzo
                Fecha: 17/10/2026

                Si no los confirmas aquí, quedan en Pendientes de la web.""");
    }

    @Test
    void dosMovimientosVanNumeradosYLaTransferenciaLlevaOrigenYDestinoSinCategoria() {
        String texto = TextosDelBot.registro(new RespuestaDelAsistente.Registro(GRUPO,
                List.of(gasto("Almuerzo"), transferencia())), BOGOTA);

        assertThat(texto).startsWith("Registré 2 movimientos sin confirmar:\n\n1. Gasto\n")
                .contains("""
                        2. Transferencia
                        Cuenta: Billetera → Ahorros
                        Monto: 100.000,5 COP
                        Descripción: Al ahorro
                        Fecha: 17/10/2026""")
                .endsWith("Si no los confirmas aquí, quedan en Pendientes de la web.");
        assertThat(texto.split("Categoría", -1)).hasSize(2);
    }

    @Test
    void unIngresoSinDescripcionNoLlevaEsaLinea() {
        RespuestaDelAsistente.Movimiento ingreso = new RespuestaDelAsistente.Movimiento("INCOME", "Billetera", null,
                "Salario", new BigDecimal("1000000"), "COP", null, gasto("x").fecha());

        String texto = TextosDelBot.registro(new RespuestaDelAsistente.Registro(GRUPO, List.of(ingreso)), BOGOTA);

        assertThat(texto).contains("1. Ingreso\nCuenta: Billetera\nCategoría: Salario\nMonto: 1.000.000 COP\nFecha: ")
                .doesNotContain("Descripción");
    }

    @Test
    void unRegistroLargoNoPasaDelLimiteYDiceCuantosFaltan() {
        String larga = "x".repeat(255);
        String texto = TextosDelBot.registro(new RespuestaDelAsistente.Registro(GRUPO,
                Collections.nCopies(40, gasto(larga))), BOGOTA);

        int mostrados = texto.split("Descripción: ", -1).length - 1;
        assertThat(texto.length()).isLessThanOrEqualTo(TextosDelBot.MAX_CARACTERES);
        assertThat(mostrados).isBetween(1, 39);
        assertThat(texto).startsWith("Registré 40 movimientos sin confirmar:")
                .endsWith("y " + (40 - mostrados) + " más.\n\nSi no los confirmas aquí, quedan en Pendientes de la web.");
    }

    @Test
    void elSaldoMuestraLosTotalesYCadaCuenta() {
        String texto = TextosDelBot.saldo(new RespuestaDelAsistente.Saldo(DESDE, HASTA, "COP",
                new BigDecimal("3000000"), new BigDecimal("1200000"), new BigDecimal("1800000"),
                new BigDecimal("1500000"), List.of(new RespuestaDelAsistente.Cuenta("Billetera", "COP",
                                new BigDecimal("120000")),
                        new RespuestaDelAsistente.Cuenta("Ahorros USD", "USD", new BigDecimal("250.75")))));

        assertThat(texto).isEqualTo("""
                Del 01/10/2026 al 31/10/2026:
                Ingresos: 3.000.000 COP
                Gastos: 1.200.000 COP
                Neto: 1.800.000 COP
                Neto desde siempre: 1.500.000 COP

                Cuentas:
                Billetera: 120.000 COP
                Ahorros USD: 250,75 USD""");
    }

    @Test
    void pocosMovimientosVanTodosConSuResumen() {
        String texto = TextosDelBot.movimientos(consulta(List.of(gasto("Almuerzo"), transferencia())), BOGOTA);

        assertThat(texto).isEqualTo("""
                Del 01/10/2026 al 31/10/2026: 2 movimientos.
                Ingresos: 0 COP
                Gastos: 45.000 COP
                Neto: -45.000 COP

                17/10/2026 · Gasto · 45.000 COP · Almuerzo · Billetera · Mercado
                17/10/2026 · Transferencia · 100.000,5 COP · Al ahorro · Billetera → Ahorros""");
    }

    @Test
    void unSoloMovimientoSeDiceEnSingular() {
        assertThat(TextosDelBot.movimientos(consulta(List.of(gasto("Almuerzo"))), BOGOTA))
                .startsWith("Del 01/10/2026 al 31/10/2026: 1 movimiento.\n");
    }

    @Test
    void masDeVeinteMovimientosMuestraVeinteYCuantosFaltan() {
        List<RespuestaDelAsistente.Movimiento> lista = IntStream.rangeClosed(1, 25)
                .mapToObj(i -> gasto("Gasto " + i)).toList();

        String texto = TextosDelBot.movimientos(consulta(lista), BOGOTA);

        assertThat(texto).contains("· Gasto 20 ·").doesNotContain("· Gasto 21 ·")
                .endsWith("\ny 5 más. La lista completa está en la web.");
    }

    @Test
    void unaConsultaLargaNoPasaDelLimite() {
        String texto = TextosDelBot.movimientos(consulta(Collections.nCopies(20, gasto("x".repeat(255)))), BOGOTA);

        assertThat(texto.length()).isLessThanOrEqualTo(TextosDelBot.MAX_CARACTERES);
        assertThat(texto).endsWith(" más. La lista completa está en la web.");
    }

    @Test
    void confirmadosDiceCuantosQuedaronGuardados() {
        assertThat(TextosDelBot.confirmados(1)).isEqualTo("Listo, guardé 1 movimiento.");
        assertThat(TextosDelBot.confirmados(3)).isEqualTo("Listo, guardé 3 movimientos.");
    }
}
