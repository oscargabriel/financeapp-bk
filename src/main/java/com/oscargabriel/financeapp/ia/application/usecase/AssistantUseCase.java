package com.oscargabriel.financeapp.ia.application.usecase;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.ia.domain.model.AssistantBalance;
import com.oscargabriel.financeapp.ia.domain.model.AssistantChannel;
import com.oscargabriel.financeapp.ia.domain.model.AssistantContext;
import com.oscargabriel.financeapp.ia.domain.model.AssistantContext.CategoriaConAmbito;
import com.oscargabriel.financeapp.ia.domain.model.AssistantDecision;
import com.oscargabriel.financeapp.ia.domain.model.AssistantMessage;
import com.oscargabriel.financeapp.ia.domain.model.AssistantReply;
import com.oscargabriel.financeapp.ia.domain.model.AssistantReport;
import com.oscargabriel.financeapp.ia.domain.model.CategoryScope;
import com.oscargabriel.financeapp.ia.domain.model.CreatedGroup;
import com.oscargabriel.financeapp.ia.domain.model.CreatedMovement;
import com.oscargabriel.financeapp.ia.domain.model.NewMovement;
import com.oscargabriel.financeapp.ia.domain.model.RegisteredMovement;
import com.oscargabriel.financeapp.ia.domain.model.ReportFilter;
import com.oscargabriel.financeapp.ia.domain.model.TransactionType;
import com.oscargabriel.financeapp.ia.domain.model.UserAccount;
import com.oscargabriel.financeapp.ia.domain.model.UserCategory;
import com.oscargabriel.financeapp.ia.domain.port.in.AssistantPort;
import com.oscargabriel.financeapp.ia.domain.port.out.AssistantModelPort;
import com.oscargabriel.financeapp.ia.domain.port.out.BalanceQueryPort;
import com.oscargabriel.financeapp.ia.domain.port.out.NamesQueryPort;
import com.oscargabriel.financeapp.ia.domain.port.out.PendingMovementPort;
import com.oscargabriel.financeapp.ia.domain.port.out.ReportQueryPort;
import com.oscargabriel.financeapp.ia.domain.port.out.SeriesCatchUpPort;

import reactor.core.publisher.Mono;

/**
 * Pasa el mensaje al modelo y ejecuta lo que decida (design.md de FA-77). Un archivo no cambia nada aqui: va al
 * modelo con el texto, y lo que decide se resuelve igual (FIN-57). El modelo solo aporta
 * nombres y valores: el usuario sale del token, los nombres se resuelven entre los suyos y el formato
 * se valida aqui, porque el modelo no pasa por ningun DTO. Lo que no se pueda resolver se le pide
 * aclarar, sin crear ni consultar nada.
 *
 * Lee con consultas propias y registra por el core (design.md de FA-148): el alta y la puesta al dia
 * de las series viven alla, y las lecturas copian las reglas de /reports.
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
    private final NamesQueryPort nombres;
    private final PendingMovementPort pendientes;
    private final SeriesCatchUpPort series;
    private final ReportQueryPort reportes;
    private final BalanceQueryPort saldos;
    private final Clock clock;

    @Override
    public Mono<AssistantReply> atender(UUID userId, AssistantChannel canal, AssistantMessage mensaje) {
        return Mono.zip(nombres.findActiveAccounts(userId).collectList(),
                        nombres.findLiveCategories(userId).collectList())
                .flatMap(t -> {
                    DelUsuario suyo = new DelUsuario(t.getT1(), t.getT2());
                    return modelo.interpretar(mensaje, suyo.contexto(LocalDate.now(clock)))
                            .flatMap(decision -> ejecutar(userId, canal, decision, suyo)
                                    .onErrorResume(Aclaracion.class,
                                            a -> Mono.just(AssistantReply.aclaracion(a.getMessage())))
                                    .onErrorResume(AssistantUseCase::esDeValidacion,
                                            e -> Mono.just(AssistantReply.aclaracion(descripciones(e)))));
                });
    }

    private Mono<AssistantReply> ejecutar(UUID userId, AssistantChannel canal, AssistantDecision decision,
            DelUsuario suyo) {
        return switch (decision) {
            case AssistantDecision.CrearMovimientos c -> Mono.defer(() -> crear(userId, canal, c.movimientos(), suyo));
            case AssistantDecision.ConsultarMovimientos q -> Mono.defer(() -> consultarMovimientos(userId, q, suyo));
            case AssistantDecision.ConsultarSaldo s -> Mono.defer(() -> consultarSaldo(userId, s));
            case AssistantDecision.SinFuncion ignorada -> Mono.just(AssistantReply.noSoportado(NO_SOPORTADO));
        };
    }

    /**
     * Todo o nada (FIN-58): se resuelven todos antes de registrar, y el primero que no se pueda pide aclarar sin crear
     * ninguno. Con varios, la aclaracion dice cual por su posicion en el mensaje. Se registran en un solo grupo.
     */
    private Mono<AssistantReply> crear(UUID userId, AssistantChannel canal,
            List<AssistantDecision.CrearMovimiento> llamadas, DelUsuario suyo) {
        List<Resuelto> resueltos = new ArrayList<>(llamadas.size());
        for (int i = 0; i < llamadas.size(); i++) {
            try {
                resueltos.add(resolver(llamadas.get(i), suyo));
            } catch (Aclaracion a) {
                throw llamadas.size() == 1 ? a : new Aclaracion("Movimiento " + (i + 1) + ": " + a.getMessage());
            }
        }
        return pendientes.register(userId, canal, resueltos.stream().map(Resuelto::nuevo).toList())
                .map(registrado -> AssistantReply.creado(mensaje(resueltos), new CreatedGroup(registrado.groupId(),
                        IntStream.range(0, resueltos.size())
                                .mapToObj(i -> resueltos.get(i).creado(registrado.movements().get(i)))
                                .toList())));
    }

    private Resuelto resolver(AssistantDecision.CrearMovimiento c, DelUsuario suyo) {
        TransactionType tipo = tipo(c.tipo(), true);
        BigDecimal monto = monto(c.monto());
        String descripcion = descripcion(c.descripcion());
        UserAccount cuenta = suyo.cuenta(c.cuenta(), "la cuenta");
        UserAccount destino = tipo == TransactionType.TRANSFER ? suyo.cuenta(c.cuentaDestino(), "la cuenta destino") : null;
        UserCategory categoria = tipo == TransactionType.TRANSFER ? null : suyo.categoriaPara(tipo, c.categoria());
        return new Resuelto(tipo, monto, descripcion, cuenta, destino, categoria, instante(c.fecha()));
    }

    private static String mensaje(List<Resuelto> resueltos) {
        if (resueltos.size() == 1) {
            return "Registre " + resueltos.getFirst().frase() + ". Confirmalo o descartalo.";
        }
        return "Registre " + resueltos.size() + " movimientos: "
                + resueltos.stream().map(Resuelto::frase).collect(Collectors.joining("; "))
                + ". Confirmalos o descartalos.";
    }

    /** Las series se ponen al dia despues de validar: una consulta que pide aclaracion no escribe nada. */
    private Mono<AssistantReply> consultarMovimientos(UUID userId, AssistantDecision.ConsultarMovimientos q,
            DelUsuario suyo) {
        Rango rango = rango(q.desde(), q.hasta());
        TransactionType tipo = tipo(q.tipo(), false);
        Set<UUID> categoria = q.categoria() == null ? Set.of() : Set.of(suyo.categoria(q.categoria()).id());
        Set<UUID> cuenta = q.cuenta() == null ? Set.of() : Set.of(suyo.cuenta(q.cuenta(), "la cuenta").id());
        ReportFilter filtro = new ReportFilter(rango.desde(), rango.hasta(), categoria, cuenta,
                tipo == null ? Set.of() : Set.of(tipo));
        return series.catchUp(userId)
                .then(Mono.defer(() -> Mono.zip(reportes.findBaseCurrency(userId),
                        reportes.findMovements(userId, filtro, clock.instant()).collectList())))
                .map(t -> AssistantReport.of(rango.desde(), rango.hasta(), t.getT1(), t.getT2()))
                .map(r -> AssistantReply.reporte(resumen(r), r));
    }

    private Mono<AssistantReply> consultarSaldo(UUID userId, AssistantDecision.ConsultarSaldo s) {
        Rango rango = rango(s.desde(), s.hasta());
        return series.catchUp(userId)
                .then(Mono.defer(() -> Mono.zip(saldos.findSums(userId, rango.desde(), rango.hasta()),
                        saldos.findAccounts(userId).collectList())))
                .map(t -> AssistantBalance.of(rango.desde(), rango.hasta(), t.getT1(), t.getT2()))
                .map(b -> AssistantReply.saldo(resumen(b), b));
    }

    /** Sin rango, el mes en curso, como el resto de los reportes; las mismas reglas que /reports. */
    private Rango rango(String desde, String hasta) {
        LocalDate inicio = dia(desde);
        LocalDate fin = dia(hasta);
        if ((inicio == null) != (fin == null)) {
            throw new Aclaracion(AMBAS_FECHAS);
        }
        if (inicio == null) {
            YearMonth mes = YearMonth.now(clock);
            return new Rango(mes.atDay(1), mes.atEndOfMonth());
        }
        if (inicio.isAfter(fin)) {
            throw new Aclaracion("La fecha inicial (" + inicio + ") es posterior a la fecha final (" + fin + ").");
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

    private static String resumen(AssistantReport r) {
        return "Del " + r.from() + " al " + r.to() + ": " + r.transactions().size() + " movimientos. Ingresos "
                + dinero(r.income(), r.currencyCode()) + ", gastos " + dinero(r.expense(), r.currencyCode())
                + ", neto " + dinero(r.net(), r.currencyCode()) + ".";
    }

    private static String resumen(AssistantBalance b) {
        return "Del " + b.from() + " al " + b.to() + ": ingresos " + dinero(b.income(), b.currencyCode())
                + ", gastos " + dinero(b.expense(), b.currencyCode()) + ", neto " + dinero(b.net(), b.currencyCode())
                + ". Desde siempre, neto " + dinero(b.allTimeNet(), b.currencyCode()) + ".";
    }

    private static String dinero(BigDecimal monto, String moneda) {
        NumberFormat formato = NumberFormat.getNumberInstance(COLOMBIA);
        formato.setMaximumFractionDigits(MAX_DECIMALES);
        return formato.format(monto) + " " + moneda;
    }

    /** Lo que rechaza el alta se le explica al usuario; un 502 del modelo no es de este tipo. */
    private static boolean esDeValidacion(Throwable e) {
        return e instanceof BadRequestException bre && bre.getHttpStatus() == HttpStatus.BAD_REQUEST;
    }

    private static String descripciones(Throwable e) {
        return "No pude completar lo que pediste: " + ((BadRequestException) e).getErrorResponse().getErrors()
                .stream().map(ErrorDetail::getDescription).collect(Collectors.joining("; ")) + ".";
    }

    private record Rango(LocalDate desde, LocalDate hasta) {
    }

    /** Un movimiento con los nombres ya resueltos a los del usuario; fecha es la de NewMovement. */
    private record Resuelto(TransactionType tipo, BigDecimal monto, String descripcion, UserAccount cuenta,
            UserAccount destino, UserCategory categoria, String fecha) {

        NewMovement nuevo() {
            return new NewMovement(tipo, cuenta.id(), destino == null ? null : destino.id(),
                    categoria == null ? null : categoria.id(), monto, cuenta.currencyCode(), descripcion, fecha);
        }

        CreatedMovement creado(RegisteredMovement r) {
            return new CreatedMovement(r.id(), tipo, r.status(), monto, cuenta.currencyCode(), descripcion,
                    r.occurredAt(), cuenta.name(), destino == null ? null : destino.name(),
                    categoria == null ? null : categoria.name());
        }

        String frase() {
            String cuanto = dinero(monto, cuenta.currencyCode());
            return switch (tipo) {
                case EXPENSE -> "un gasto de " + cuanto + " en " + cuenta.name() + " (" + categoria.name() + ")";
                case INCOME -> "un ingreso de " + cuanto + " en " + cuenta.name() + " (" + categoria.name() + ")";
                case TRANSFER -> "una transferencia de " + cuanto + " de " + cuenta.name() + " a " + destino.name();
            };
        }
    }

    /** Lo que el asistente no pudo resolver; sale como NEEDS_CLARIFICATION con el mensaje. */
    private static final class Aclaracion extends RuntimeException {

        Aclaracion(String mensaje) {
            super(mensaje, null, false, false);
        }
    }

    /** Las cuentas activas y las categorias vivas del usuario: lo unico entre lo que se resuelven nombres. */
    private record DelUsuario(List<UserAccount> cuentas, List<UserCategory> categorias) {

        AssistantContext contexto(LocalDate hoy) {
            return new AssistantContext(hoy, cuentas.stream().map(UserAccount::name).toList(),
                    categorias.stream().map(c -> new CategoriaConAmbito(c.name(), c.appliesTo())).toList());
        }

        UserAccount cuenta(String nombre, String sujeto) {
            return resolver(cuentas, UserAccount::name, nombre, sujeto, "Tus cuentas");
        }

        UserCategory categoria(String nombre) {
            return resolver(categorias, UserCategory::name, nombre, "la categoria", "Tus categorias");
        }

        UserCategory categoriaPara(TransactionType tipo, String nombre) {
            CategoryScope alcance = tipo == TransactionType.EXPENSE ? CategoryScope.EXPENSE : CategoryScope.INCOME;
            List<UserCategory> validas = categorias.stream()
                    .filter(c -> alcance.compatibles().contains(c.appliesTo())).toList();
            String para = tipo == TransactionType.EXPENSE ? "Tus categorias de gasto" : "Tus categorias de ingreso";
            return resolver(validas, UserCategory::name, nombre, "la categoria", para);
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
