package com.oscargabriel.financeapp.telegram.application.usecase;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oscargabriel.financeapp.telegram.domain.model.RespuestaDelAsistente;

/**
 * Los textos con que el bot muestra lo que contesto el asistente (design.md de FIN-61, decision 6). Van sin
 * parse_mode, en texto plano: una descripcion con * o < no rompe el envio.
 */
final class TextosDelBot {

    /** El tope de sendMessage. */
    static final int MAX_CARACTERES = 4096;

    static final int MAX_MOVIMIENTOS = 20;

    private static final Locale COLOMBIA = Locale.forLanguageTag("es-CO");
    private static final int MAX_DECIMALES = 4;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String SIN_CONFIRMAR = "Si no los confirmas aquí, quedan en Pendientes de la web.";
    private static final String EN_LA_WEB = " La lista completa está en la web.";

    private TextosDelBot() {
    }

    static String registro(RespuestaDelAsistente.Registro registro, ZoneId zona) {
        List<RespuestaDelAsistente.Movimiento> movimientos = registro.movimientos();
        List<String> bloques = Stream.iterate(0, i -> i < movimientos.size(), i -> i + 1)
                .map(i -> bloque(i + 1, movimientos.get(i), zona))
                .toList();
        return recortado("Registré " + cuantos(movimientos.size()) + " sin confirmar:", bloques, bloques.size(),
                "\n\n", faltan -> "y " + faltan + " más.", "\n\n" + SIN_CONFIRMAR);
    }

    static String saldo(RespuestaDelAsistente.Saldo saldo) {
        String cabecera = rango(saldo.desde(), saldo.hasta()) + ":\n"
                + totales(saldo.ingresos(), saldo.gastos(), saldo.neto(), saldo.moneda())
                + "\nNeto desde siempre: " + dinero(saldo.netoHistorico(), saldo.moneda()) + "\n\nCuentas:";
        List<String> cuentas = saldo.cuentas().stream()
                .map(c -> c.nombre() + ": " + dinero(c.saldo(), c.moneda()))
                .toList();
        return recortado(cabecera, cuentas, cuentas.size(), "\n", faltan -> "y " + faltan + " más." + EN_LA_WEB, "");
    }

    static String movimientos(RespuestaDelAsistente.Movimientos consulta, ZoneId zona) {
        List<RespuestaDelAsistente.Movimiento> movimientos = consulta.movimientos();
        String cabecera = rango(consulta.desde(), consulta.hasta()) + ": " + cuantos(movimientos.size()) + ".\n"
                + totales(consulta.ingresos(), consulta.gastos(), consulta.neto(), consulta.moneda()) + "\n";
        List<String> lineas = movimientos.stream().map(m -> linea(m, zona)).toList();
        return recortado(cabecera, lineas, MAX_MOVIMIENTOS, "\n", faltan -> "y " + faltan + " más." + EN_LA_WEB, "");
    }

    static String confirmados(int cuantos) {
        return "Listo, guardé " + cuantos(cuantos) + ".";
    }

    /**
     * La cabecera, hasta {@code tope} elementos mientras quepan junto al aviso de cuantos faltan y el cierre, y el
     * cierre. El aviso solo va si falta alguno.
     */
    private static String recortado(String cabecera, List<String> elementos, int tope, String separador,
            IntFunction<String> faltanTexto, String cierre) {
        StringBuilder cuerpo = new StringBuilder(cabecera);
        int mostrados = 0;
        for (String elemento : elementos.subList(0, Math.min(tope, elementos.size()))) {
            int faltan = elementos.size() - mostrados - 1;
            String aviso = faltan > 0 ? separador + faltanTexto.apply(faltan) : "";
            if (cuerpo.length() + separador.length() + elemento.length() + aviso.length() + cierre.length()
                    > MAX_CARACTERES) {
                break;
            }
            cuerpo.append(separador).append(elemento);
            mostrados++;
        }
        int faltan = elementos.size() - mostrados;
        if (faltan > 0) {
            cuerpo.append(separador).append(faltanTexto.apply(faltan));
        }
        return cuerpo.append(cierre).toString();
    }

    private static String bloque(int numero, RespuestaDelAsistente.Movimiento m, ZoneId zona) {
        return Stream.of(
                        numero + ". " + tipo(m.tipo()),
                        "Cuenta: " + cuenta(m),
                        m.categoria() == null ? null : "Categoría: " + m.categoria(),
                        "Monto: " + dinero(m.monto(), m.moneda()),
                        m.descripcion() == null ? null : "Descripción: " + m.descripcion(),
                        "Fecha: " + FECHA.format(m.fecha().atZone(zona)))
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
    }

    private static String linea(RespuestaDelAsistente.Movimiento m, ZoneId zona) {
        return Stream.of(FECHA.format(m.fecha().atZone(zona)), tipo(m.tipo()), dinero(m.monto(), m.moneda()),
                        m.descripcion(), cuenta(m), m.categoria())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" · "));
    }

    private static String cuenta(RespuestaDelAsistente.Movimiento m) {
        return m.cuentaDestino() == null ? m.cuenta() : m.cuenta() + " → " + m.cuentaDestino();
    }

    private static String tipo(String tipo) {
        return switch (tipo) {
            case "EXPENSE" -> "Gasto";
            case "INCOME" -> "Ingreso";
            case "TRANSFER" -> "Transferencia";
            default -> tipo;
        };
    }

    private static String rango(LocalDate desde, LocalDate hasta) {
        return "Del " + FECHA.format(desde) + " al " + FECHA.format(hasta);
    }

    private static String totales(BigDecimal ingresos, BigDecimal gastos, BigDecimal neto, String moneda) {
        return "Ingresos: " + dinero(ingresos, moneda) + "\nGastos: " + dinero(gastos, moneda) + "\nNeto: "
                + dinero(neto, moneda);
    }

    private static String cuantos(int cuantos) {
        return cuantos + (cuantos == 1 ? " movimiento" : " movimientos");
    }

    /** Como dinero en AssistantUseCase: separadores de es-CO y el codigo de la moneda. */
    private static String dinero(BigDecimal monto, String moneda) {
        NumberFormat formato = NumberFormat.getNumberInstance(COLOMBIA);
        formato.setMaximumFractionDigits(MAX_DECIMALES);
        return formato.format(monto) + " " + moneda;
    }
}
