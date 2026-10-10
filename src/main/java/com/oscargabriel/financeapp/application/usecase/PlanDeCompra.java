package com.oscargabriel.financeapp.application.usecase;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.Account;
import com.oscargabriel.financeapp.domain.model.CreateInstallmentPurchaseCommand;
import com.oscargabriel.financeapp.domain.model.InstallmentPlan;
import com.oscargabriel.financeapp.domain.model.RecurrenceRule;
import com.oscargabriel.financeapp.domain.model.ScheduledInstallment;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Mono;

/**
 * Lo que la simulacion y el alta de una compra en cuotas comparten (FA-108): validar la tarjeta, la
 * categoria y la fecha contra el usuario, y calcular el plan con la tasa de la tarjeta. El formato del
 * cuerpo ya lo valido el request.
 */
record PlanDeCompra(Account tarjeta, UUID categoria, InstallmentPlan plan, ZoneId zona, Instant ahora) {

    static Mono<PlanDeCompra> de(UUID userId, CreateInstallmentPurchaseCommand compra, AccountQueryPort cuentas,
            CategoryQueryPort categorias, UserRepositoryPort usuarios, Clock clock) {
        return Mono.zip(ReferenciasDelUsuario.de(userId, cuentas, categorias), ZonaDelUsuario.de(usuarios, userId))
                .map(t -> preparar(compra, t.getT1(), t.getT2(), clock.instant()));
    }

    private static PlanDeCompra preparar(CreateInstallmentPurchaseCommand compra, ReferenciasDelUsuario referencias,
            ZoneId zona, Instant ahora) {
        List<ErrorDetail> errores = new ArrayList<>();
        Account tarjeta = referencias.tarjetaParaCuotas(compra.accountId(), "accountId", errores);
        UUID categoria = referencias.categoria(compra.categoryId(), TransactionType.EXPENSE, "categoryId", errores);
        if (compra.purchaseDate().isAfter(LocalDate.ofInstant(ahora, zona))) {
            errores.add(ReferenciasDelUsuario.detalle("La fecha de compra no puede ser futura", "purchaseDate"));
        }
        if (!errores.isEmpty()) {
            throw new BadRequestException(HttpStatus.BAD_REQUEST, errores);
        }
        InstallmentPlan plan = new InstallmentPlan(compra.amount(), compra.installmentCount(), tasa(tarjeta),
                compra.purchaseDate(), tarjeta.statementDay(), tarjeta.paymentDueDay());
        return new PlanDeCompra(tarjeta, categoria, plan, zona, ahora);
    }

    /** Una tarjeta sin tasa cargada no cobra interes. */
    static BigDecimal tasa(Account tarjeta) {
        return Objects.requireNonNullElse(tarjeta.monthlyInterestRate(), BigDecimal.ZERO);
    }

    /** Las cuotas a medianoche de su dia en la zona del usuario, con los ids de movimiento dados o null. */
    List<ScheduledInstallment> cuotas(List<UUID> transactionIds) {
        return plan.cuotas().stream()
                .map(c -> new ScheduledInstallment(c.number(),
                        transactionIds == null ? null : transactionIds.get(c.number() - 1),
                        RecurrenceRule.medianoche(c.dueDate(), zona), c.principal(), c.interest()))
                .toList();
    }
}
