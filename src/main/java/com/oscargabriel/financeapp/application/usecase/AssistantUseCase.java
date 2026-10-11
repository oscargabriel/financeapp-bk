package com.oscargabriel.financeapp.application.usecase;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.Account;
import com.oscargabriel.financeapp.domain.model.AssistantContext;
import com.oscargabriel.financeapp.domain.model.AssistantContext.CategoriaConAmbito;
import com.oscargabriel.financeapp.domain.model.AssistantDecision;
import com.oscargabriel.financeapp.domain.model.AssistantReply;
import com.oscargabriel.financeapp.domain.model.Balance;
import com.oscargabriel.financeapp.domain.model.Category;
import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.model.CreateTransactionCommand;
import com.oscargabriel.financeapp.domain.model.TransactionOrigin;
import com.oscargabriel.financeapp.domain.model.TransactionReport;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.port.in.AssistantPort;
import com.oscargabriel.financeapp.domain.port.in.CreateTransactionsPort;
import com.oscargabriel.financeapp.domain.port.in.GetBalancePort;
import com.oscargabriel.financeapp.domain.port.in.GetTransactionReportPort;
import com.oscargabriel.financeapp.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.domain.port.out.AssistantModelPort;
import com.oscargabriel.financeapp.domain.port.out.CategoryQueryPort;

import reactor.core.publisher.Mono;

/**
 * Pasa el texto al modelo y ejecuta lo que decida con los casos de uso existentes (design.md de FA-77).
 * El modelo solo aporta nombres y valores: el usuario sale del token, los nombres se resuelven entre
 * los suyos y el formato se valida aqui, porque el modelo no pasa por ningun DTO. Lo que no se pueda
 * resolver se le pide aclarar, sin crear ni consultar nada.
 */
@Service
@AllArgsConstructor
public class AssistantUseCase implements AssistantPort {

    static final String NO_SOPORTADO = "Solo puedo registrar un gasto, un ingreso o una transferencia, y "
            + "consultar tus movimientos o tu saldo.";

    private static final int MAX_DESCRIPCION = 255;
    private static final int MAX_DECIMALES = 4;
    private static final int MAX_ENTEROS = 13;
    private static final Locale COLOMBIA = Locale.forLanguageTag("es-CO");
    private static final String AMBAS_FECHAS = "Indica las dos fechas del rango, o ninguna para el mes en curso.";

    private final AssistantModelPort modelo;
    private final AccountQueryPort cuentas;
    private final CategoryQueryPort categorias;
    private final CreateTransactionsPort createTransactions;
    private final GetTransactionReportPort getTransactionReport;
    private final GetBalancePort getBalance;
    private final Clock clock;

    @Override
    public Mono<AssistantReply> atender(UUID userId, String texto) {
        return Mono.zip(cuentas.findByUser(userId, false).collectList(),
                        categorias.findActiveByUser(userId, EnumSet.allOf(CategoryScope.class)).collectList())
                .flatMap(t -> {
                    DelUsuario suyo = new DelUsuario(t.getT1(), t.getT2());
                    return modelo.interpretar(texto, suyo.contexto(LocalDate.now(clock)))
                            .flatMap(decision -> ejecutar(userId, decision, suyo)
                                    .onErrorResume(Aclaracion.class,
                                            a -> Mono.just(AssistantReply.aclaracion(a.getMessage())))
                                    .onErrorResume(AssistantUseCase::esDeValidacion,
                                            e -> Mono.just(AssistantReply.aclaracion(descripciones(e)))));
                });
    }

    private Mono<AssistantReply> ejecutar(UUID userId, AssistantDecision decision, DelUsuario suyo) {
        return switch (decision) {
            case AssistantDecision.CrearMovimiento c -> Mono.defer(() -> crear(userId, c, suyo));
            case AssistantDecision.ConsultarMovimientos q -> Mono.defer(() -> consultarMovimientos(userId, q, suyo));
            case AssistantDecision.ConsultarSaldo s -> Mono.defer(() -> consultarSaldo(userId, s));
            case AssistantDecision.SinFuncion ignorada -> Mono.just(AssistantReply.noSoportado(NO_SOPORTADO));
        };
    }

    private Mono<AssistantReply> crear(UUID userId, AssistantDecision.CrearMovimiento c, DelUsuario suyo) {
        TransactionType tipo = tipo(c.tipo(), true);
        BigDecimal monto = monto(c.monto());
        String descripcion = descripcion(c.descripcion());
        Account cuenta = suyo.cuenta(c.cuenta(), "la cuenta");
        Account destino = tipo == TransactionType.TRANSFER ? suyo.cuenta(c.cuentaDestino(), "la cuenta destino") : null;
        Category categoria = tipo == TransactionType.TRANSFER ? null : suyo.categoriaPara(tipo, c.categoria());
        String fecha = instante(c.fecha());

        CreateTransactionCommand comando = new CreateTransactionCommand(tipo.name(), cuenta.id().toString(),
                destino == null ? null : destino.id().toString(), categoria == null ? null : categoria.id().toString(),
                monto, null, cuenta.currencyCode(), descripcion, null, fecha);
        String mensaje = switch (tipo) {
            case EXPENSE -> "Registre un gasto de " + dinero(monto, cuenta.currencyCode()) + " en " + cuenta.name()
                    + " (" + categoria.name() + ").";
            case INCOME -> "Registre un ingreso de " + dinero(monto, cuenta.currencyCode()) + " en " + cuenta.name()
                    + " (" + categoria.name() + ").";
            case TRANSFER -> "Registre una transferencia de " + dinero(monto, cuenta.currencyCode()) + " de "
                    + cuenta.name() + " a " + destino.name() + ".";
        };
        return createTransactions.create(userId, TransactionOrigin.TELEGRAM, List.of(comando))
                .next()
                .map(t -> AssistantReply.creado(mensaje + " Queda pendiente de tu aprobacion.", t));
    }

    private Mono<AssistantReply> consultarMovimientos(UUID userId, AssistantDecision.ConsultarMovimientos q,
            DelUsuario suyo) {
        Rango rango = rangoOMes(q.desde(), q.hasta());
        TransactionType tipo = tipo(q.tipo(), false);
        Set<UUID> categoria = q.categoria() == null ? Set.of() : Set.of(suyo.categoria(q.categoria()).id());
        Set<UUID> cuenta = q.cuenta() == null ? Set.of() : Set.of(suyo.cuenta(q.cuenta(), "la cuenta").id());
        return getTransactionReport.get(userId, rango.desde(), rango.hasta(), categoria, cuenta,
                        tipo == null ? Set.of() : Set.of(tipo))
                .map(r -> AssistantReply.reporte(resumen(r, rango), r));
    }

    private Mono<AssistantReply> consultarSaldo(UUID userId, AssistantDecision.ConsultarSaldo s) {
        LocalDate desde = dia(s.desde());
        LocalDate hasta = dia(s.hasta());
        if ((desde == null) != (hasta == null)) {
            throw new Aclaracion(AMBAS_FECHAS);
        }
        return getBalance.get(userId, desde, hasta).map(b -> AssistantReply.saldo(resumen(b), b));
    }

    /** Sin rango, el mes en curso, como el resto de los reportes. */
    private Rango rangoOMes(String desde, String hasta) {
        LocalDate inicio = dia(desde);
        LocalDate fin = dia(hasta);
        if ((inicio == null) != (fin == null)) {
            throw new Aclaracion(AMBAS_FECHAS);
        }
        if (inicio == null) {
            YearMonth mes = YearMonth.now(clock);
            return new Rango(mes.atDay(1), mes.atEndOfMonth());
        }
        return new Rango(inicio, fin);
    }

    /** Sin fecha o con la de hoy, el instante de la peticion; otro dia, a mediodia para que ningun corte de zona lo mueva. */
    private String instante(String fecha) {
        LocalDate dia = dia(fecha);
        if (dia == null || dia.equals(LocalDate.now(clock))) {
            return null;
        }
        return dia.atTime(12, 0).atZone(clock.getZone()).toOffsetDateTime().toString();
    }

    private static LocalDate dia(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valor.strip());
        } catch (DateTimeParseException e) {
            throw new Aclaracion("La fecha «" + valor.strip() + "» no es un dia valido: usa AAAA-MM-DD.");
        }
    }

    private static TransactionType tipo(String valor, boolean obligatorio) {
        if (valor == null || valor.isBlank()) {
            if (obligatorio) {
                throw new Aclaracion("Falta el tipo de movimiento: un gasto, un ingreso o una transferencia.");
            }
            return null;
        }
        try {
            return TransactionType.valueOf(valor.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new Aclaracion("No entendi el tipo de movimiento: puede ser un gasto, un ingreso o una transferencia.");
        }
    }

    /** Las mismas reglas que CreateTransactionRequest le pone al monto del alta. */
    private static BigDecimal monto(String valor) {
        String regla = "El monto debe ser un numero mayor que cero, con hasta " + MAX_DECIMALES + " decimales.";
        BigDecimal monto;
        try {
            monto = new BigDecimal(valor == null ? "" : valor.strip());
        } catch (NumberFormatException e) {
            throw new Aclaracion(regla);
        }
        if (monto.signum() <= 0 || monto.scale() > MAX_DECIMALES || monto.precision() - monto.scale() > MAX_ENTEROS) {
            throw new Aclaracion(regla);
        }
        return monto;
    }

    private static String descripcion(String valor) {
        String texto = valor == null ? "" : valor.strip();
        if (texto.isEmpty() || texto.length() > MAX_DESCRIPCION) {
            throw new Aclaracion("La descripcion es obligatoria y admite hasta " + MAX_DESCRIPCION + " caracteres.");
        }
        return texto;
    }

    private static String resumen(TransactionReport r, Rango rango) {
        return "Del " + rango.desde() + " al " + rango.hasta() + ": " + r.transactions().size() + " movimientos. Ingresos "
                + dinero(total(r, TransactionType.INCOME), r.currencyCode()) + ", gastos "
                + dinero(total(r, TransactionType.EXPENSE), r.currencyCode()) + ", neto "
                + dinero(r.net(), r.currencyCode()) + ".";
    }

    private static String resumen(Balance b) {
        return "Del " + b.from() + " al " + b.to() + ": ingresos " + dinero(b.period().income(), b.currencyCode())
                + ", gastos " + dinero(b.period().expense(), b.currencyCode()) + ", neto "
                + dinero(b.period().net(), b.currencyCode()) + ". Desde siempre, neto "
                + dinero(b.allTime().net(), b.currencyCode()) + ".";
    }

    private static BigDecimal total(TransactionReport r, TransactionType tipo) {
        return r.totalsByType().stream().filter(t -> t.type() == tipo).map(TransactionReport.TypeTotal::total)
                .findFirst().orElse(BigDecimal.ZERO);
    }

    private static String dinero(BigDecimal monto, String moneda) {
        NumberFormat formato = NumberFormat.getNumberInstance(COLOMBIA);
        formato.setMaximumFractionDigits(MAX_DECIMALES);
        return formato.format(monto) + " " + moneda;
    }

    /** Lo que rechazan el alta o el reporte se le explica al usuario; un 502 del modelo no es de este tipo. */
    private static boolean esDeValidacion(Throwable e) {
        return e instanceof BadRequestException bre && bre.getHttpStatus() == HttpStatus.BAD_REQUEST;
    }

    private static String descripciones(Throwable e) {
        return "No pude completar lo que pediste: " + ((BadRequestException) e).getErrorResponse().getErrors()
                .stream().map(ErrorDetail::getDescription).collect(Collectors.joining("; ")) + ".";
    }

    private record Rango(LocalDate desde, LocalDate hasta) {
    }

    /** Lo que el asistente no pudo resolver; sale como NEEDS_CLARIFICATION con el mensaje. */
    private static final class Aclaracion extends RuntimeException {

        Aclaracion(String mensaje) {
            super(mensaje, null, false, false);
        }
    }

    /** Las cuentas activas y las categorias vivas del usuario: lo unico entre lo que se resuelven nombres. */
    private record DelUsuario(List<Account> cuentas, List<Category> categorias) {

        AssistantContext contexto(LocalDate hoy) {
            return new AssistantContext(hoy, cuentas.stream().map(Account::name).toList(),
                    categorias.stream().map(c -> new CategoriaConAmbito(c.name(), c.appliesTo())).toList());
        }

        Account cuenta(String nombre, String sujeto) {
            return resolver(cuentas, Account::name, nombre, sujeto, "Tus cuentas");
        }

        Category categoria(String nombre) {
            return resolver(categorias, Category::name, nombre, "la categoria", "Tus categorias");
        }

        Category categoriaPara(TransactionType tipo, String nombre) {
            CategoryScope alcance = tipo == TransactionType.EXPENSE ? CategoryScope.EXPENSE : CategoryScope.INCOME;
            List<Category> validas = categorias.stream()
                    .filter(c -> alcance.compatibles().contains(c.appliesTo())).toList();
            String para = tipo == TransactionType.EXPENSE ? "Tus categorias de gasto" : "Tus categorias de ingreso";
            return resolver(validas, Category::name, nombre, "la categoria", para);
        }

        private static <T> T resolver(List<T> elementos, Function<T, String> nombre, String buscado, String sujeto,
                String titulo) {
            String opciones = " " + titulo + ": " + elementos.stream().map(nombre).collect(Collectors.joining(", "))
                    + ".";
            if (buscado == null || buscado.isBlank()) {
                throw new Aclaracion("Falta " + sujeto + "." + opciones);
            }
            return switch (new ResolutorDeNombres<>(elementos, nombre).buscar(buscado)) {
                case ResolutorDeNombres.Unico<T> unico -> unico.valor();
                case ResolutorDeNombres.Ninguno<T> ninguno ->
                        throw new Aclaracion("No encontre " + sujeto + " «" + buscado.strip() + "»." + opciones);
                case ResolutorDeNombres.Varios<T> varios -> throw new Aclaracion("Hay varias opciones para " + sujeto
                        + " «" + buscado.strip() + "»; usa el nombre completo." + opciones);
            };
        }
    }
}
