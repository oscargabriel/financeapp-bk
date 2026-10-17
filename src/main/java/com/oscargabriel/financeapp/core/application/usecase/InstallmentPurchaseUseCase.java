package com.oscargabriel.financeapp.core.application.usecase;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.Account;
import com.oscargabriel.financeapp.core.domain.model.CreateInstallmentPurchaseCommand;
import com.oscargabriel.financeapp.core.domain.model.CreatedInstallmentPurchase;
import com.oscargabriel.financeapp.core.domain.model.Currency;
import com.oscargabriel.financeapp.core.domain.model.InstallmentPlan;
import com.oscargabriel.financeapp.core.domain.model.InstallmentPreview;
import com.oscargabriel.financeapp.core.domain.model.InstallmentPurchase;
import com.oscargabriel.financeapp.core.domain.model.InstallmentPurchaseView;
import com.oscargabriel.financeapp.core.domain.model.InstallmentRef;
import com.oscargabriel.financeapp.core.domain.model.RecurrenceRule;
import com.oscargabriel.financeapp.core.domain.model.ScheduledInstallment;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.model.TransactionOrigin;
import com.oscargabriel.financeapp.core.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.core.domain.model.TransactionType;
import com.oscargabriel.financeapp.core.domain.model.UpdateInstallmentPurchaseCommand;
import com.oscargabriel.financeapp.core.domain.model.UuidV7;
import com.oscargabriel.financeapp.core.domain.port.in.InstallmentPurchasePort;
import com.oscargabriel.financeapp.core.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.InstallmentPurchaseRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class InstallmentPurchaseUseCase implements InstallmentPurchasePort {

    private final AccountQueryPort cuentas;
    private final CategoryQueryPort categorias;
    private final CurrencyQueryPort monedas;
    private final UserRepositoryPort usuarios;
    private final InstallmentPurchaseRepositoryPort compras;
    private final Clock clock;

    /** Las mismas validaciones del alta y el mismo plan, sin escribir nada. */
    @Override
    public Mono<InstallmentPreview> preview(UUID userId, CreateInstallmentPurchaseCommand compra) {
        return PlanDeCompra.de(userId, compra, cuentas, categorias, monedas, usuarios, clock)
                .map(listo -> new InstallmentPreview(listo.tarjeta().id(), compra.amount(),
                        listo.tarjeta().currencyCode(), compra.purchaseDate(), compra.installmentCount(),
                        PlanDeCompra.tasa(listo.tarjeta()), listo.cuotas(null)));
    }

    /**
     * Cada cuota es un gasto confirmado de la tarjeta, programado hasta su fecha (FA-106); las ya vencidas
     * cuentan de una vez. El resumen sale del plan: recien creada, ninguna cuota se ha editado ni borrado.
     */
    @Override
    public Mono<CreatedInstallmentPurchase> create(UUID userId, CreateInstallmentPurchaseCommand alta) {
        return PlanDeCompra.de(userId, alta, cuentas, categorias, monedas, usuarios, clock)
                .flatMap(listo -> {
                    Instant ahora = listo.ahora();
                    InstallmentPurchase compra = new InstallmentPurchase(UuidV7.from(ahora), userId,
                            listo.tarjeta().id(), listo.categoria(), alta.amount(), listo.tarjeta().currencyCode(),
                            alta.description().trim(), alta.purchaseDate(), alta.installmentCount(),
                            PlanDeCompra.tasa(listo.tarjeta()));
                    List<ScheduledInstallment> plan = listo.cuotas(IntStream.range(0, alta.installmentCount())
                            .mapToObj(k -> UuidV7.from(ahora))
                            .toList());
                    List<Transaction> movimientos = plan.stream().map(c -> movimiento(compra, c)).toList();
                    return compras.save(compra, movimientos)
                            .thenReturn(new CreatedInstallmentPurchase(resumen(compra, plan, ahora), plan));
                });
    }

    /** El filtro de activas y el orden los resuelve la consulta: son las cuotas que existen hoy. */
    @Override
    public Flux<InstallmentPurchaseView> list(UUID userId) {
        return compras.findActiveByUser(userId);
    }

    /**
     * El formato y que el parche traiga algo ya los valido el request. Aqui queda que la categoria sea del
     * usuario y admita gastos. El update filtra por usuario y estado: cero filas es 404, sin lectura previa.
     */
    @Override
    public Mono<InstallmentPurchaseView> update(UUID userId, UUID purchaseId, UpdateInstallmentPurchaseCommand parche) {
        String descripcion = parche.description() == null ? null : parche.description().trim();
        return categoria(userId, parche.categoryId())
                .flatMap(categoria -> compras.update(purchaseId, userId, parche.scope(), clock.instant(),
                        descripcion, categoria.orElse(null)))
                .flatMap(actualizada -> actualizada
                        ? compras.findByIdAndUser(purchaseId, userId)
                        : Mono.<InstallmentPurchaseView>empty())
                .switchIfEmpty(Mono.error(InstallmentPurchaseUseCase::noEncontrada));
    }

    /** Una sola escritura filtrada por usuario y estado: cero filas es 404, sin una lectura previa. */
    @Override
    public Mono<Void> cancel(UUID userId, UUID purchaseId) {
        return Mono.defer(() -> compras.cancel(purchaseId, userId, clock.instant()))
                .flatMap(cancelada -> cancelada
                        ? Mono.<Void>empty()
                        : Mono.error(noEncontrada()));
    }

    private static Transaction movimiento(InstallmentPurchase compra, ScheduledInstallment cuota) {
        return new Transaction(cuota.transactionId(), compra.userId(), TransactionType.EXPENSE, compra.accountId(),
                null, compra.categoryId(), cuota.amount(), compra.currencyCode(), compra.description(), null,
                cuota.dueAt(), TransactionStatus.CONFIRMED, TransactionOrigin.WEB, null,
                new InstallmentRef(compra.id(), cuota.number(), compra.installmentCount(), cuota.principal()), null, null, null, null, null);
    }

    private static InstallmentPurchaseView resumen(InstallmentPurchase compra, List<ScheduledInstallment> plan,
            Instant ahora) {
        List<ScheduledInstallment> futuras = plan.stream().filter(c -> c.dueAt().isAfter(ahora)).toList();
        InstallmentPurchaseView.NextInstallment proxima = futuras.stream()
                .findFirst()
                .map(c -> new InstallmentPurchaseView.NextInstallment(c.number(), c.transactionId(), c.dueAt(),
                        c.amount()))
                .orElse(null);
        return new InstallmentPurchaseView(compra, plan.size() - futuras.size(),
                futuras.stream().map(ScheduledInstallment::principal).reduce(BigDecimal.ZERO, BigDecimal::add),
                futuras.stream().map(ScheduledInstallment::amount).reduce(BigDecimal.ZERO, BigDecimal::add),
                proxima);
    }

    /** Sin categoria en el parche no hace falta leer nada del usuario. */
    private Mono<Optional<UUID>> categoria(UUID userId, String categoryId) {
        if (categoryId == null) {
            return Mono.just(Optional.empty());
        }
        return ReferenciasDelUsuario.de(userId, cuentas, categorias).map(referencias -> {
            List<ErrorDetail> errores = new ArrayList<>();
            UUID id = referencias.categoria(categoryId, TransactionType.EXPENSE, "categoryId", errores);
            if (!errores.isEmpty()) {
                throw new BadRequestException(HttpStatus.BAD_REQUEST, errores);
            }
            return Optional.of(id);
        });
    }

    /** Inexistente, ajena y cancelada dan lo mismo: distinguirlas confirmaria que el id existe. */
    private static BadRequestException noEncontrada() {
        return new BadRequestException(HttpStatus.NOT_FOUND, List.of(
                ErrorDetail.of(ErrorCodes.NOT_FOUND.getCode(), "La compra no existe", "id")));
    }

    /**
     * Lo que la simulacion y el alta de una compra en cuotas comparten (FA-108): validar la tarjeta, la
     * categoria y la fecha contra el usuario, y calcular el plan con la tasa de la tarjeta y los decimales de
     * su moneda. El formato del cuerpo ya lo valido el request.
     */
    private record PlanDeCompra(Account tarjeta, UUID categoria, InstallmentPlan plan, ZoneId zona, Instant ahora) {

        private static Mono<PlanDeCompra> de(UUID userId, CreateInstallmentPurchaseCommand compra,
                AccountQueryPort cuentas, CategoryQueryPort categorias, CurrencyQueryPort monedas,
                UserRepositoryPort usuarios, Clock clock) {
            return Mono.zip(ReferenciasDelUsuario.de(userId, cuentas, categorias),
                            monedas.findActive().collectMap(Currency::code), ZonaDelUsuario.de(usuarios, userId))
                    .map(t -> preparar(compra, t.getT1(), t.getT2(), t.getT3(), clock.instant()));
        }

        private static PlanDeCompra preparar(CreateInstallmentPurchaseCommand compra,
                ReferenciasDelUsuario referencias, Map<String, Currency> activas, ZoneId zona, Instant ahora) {
            List<ErrorDetail> errores = new ArrayList<>();
            Account tarjeta = referencias.tarjetaParaCuotas(compra.accountId(), "accountId", activas, errores);
            UUID categoria = referencias.categoria(compra.categoryId(), TransactionType.EXPENSE, "categoryId",
                    errores);
            Currency moneda = tarjeta == null ? null : activas.get(tarjeta.currencyCode());
            if (moneda != null) {
                capitalMinimo(compra, moneda, errores);
            }
            if (compra.purchaseDate().isAfter(LocalDate.ofInstant(ahora, zona))) {
                errores.add(ReferenciasDelUsuario.detalle("La fecha de compra no puede ser futura", "purchaseDate"));
            }
            if (!errores.isEmpty()) {
                throw new BadRequestException(HttpStatus.BAD_REQUEST, errores);
            }
            InstallmentPlan plan = new InstallmentPlan(compra.amount(), compra.installmentCount(), tasa(tarjeta),
                    compra.purchaseDate(), tarjeta.statementDay(), tarjeta.paymentDueDay(),
                    Boolean.TRUE.equals(tarjeta.chargesSingleInstallmentInterest()), moneda.decimalPlaces());
            return new PlanDeCompra(tarjeta, categoria, plan, zona, ahora);
        }

        /**
         * El capital de cada cuota se reparte hacia abajo a los decimales de la moneda: con menos de una unidad
         * minima por cuota, alguna quedaria en cero (FA-123). Necesita la moneda de la tarjeta, por eso no lo
         * valida el request.
         */
        private static void capitalMinimo(CreateInstallmentPurchaseCommand compra, Currency moneda,
                List<ErrorDetail> errores) {
            BigDecimal unidad = BigDecimal.ONE.movePointLeft(moneda.decimalPlaces());
            if (compra.amount().compareTo(unidad.multiply(BigDecimal.valueOf(compra.installmentCount()))) < 0) {
                errores.add(ReferenciasDelUsuario.detalle("El monto tiene que alcanzar para al menos "
                        + unidad.toPlainString() + " " + moneda.code() + " de capital por cuota", "amount"));
            }
        }

        /** Una tarjeta sin tasa cargada no cobra interes. */
        private static BigDecimal tasa(Account tarjeta) {
            return Objects.requireNonNullElse(tarjeta.monthlyInterestRate(), BigDecimal.ZERO);
        }

        /** Las cuotas a medianoche de su dia en la zona del usuario, con los ids de movimiento dados o null. */
        private List<ScheduledInstallment> cuotas(List<UUID> transactionIds) {
            return plan.cuotas().stream()
                    .map(c -> new ScheduledInstallment(c.number(),
                            transactionIds == null ? null : transactionIds.get(c.number() - 1),
                            RecurrenceRule.medianoche(c.dueDate(), zona), c.principal(), c.interest()))
                    .toList();
        }
    }
}
