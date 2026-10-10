package com.oscargabriel.financeapp.application.usecase;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.model.CreateInstallmentPurchaseCommand;
import com.oscargabriel.financeapp.domain.model.CreatedInstallmentPurchase;
import com.oscargabriel.financeapp.domain.model.InstallmentPurchase;
import com.oscargabriel.financeapp.domain.model.InstallmentPurchaseView;
import com.oscargabriel.financeapp.domain.model.InstallmentRef;
import com.oscargabriel.financeapp.domain.model.ScheduledInstallment;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.TransactionOrigin;
import com.oscargabriel.financeapp.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.model.UuidV7;
import com.oscargabriel.financeapp.domain.port.in.CreateInstallmentPurchasePort;
import com.oscargabriel.financeapp.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.domain.port.out.InstallmentPurchaseRepositoryPort;
import com.oscargabriel.financeapp.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class CreateInstallmentPurchaseUseCase implements CreateInstallmentPurchasePort {

    private final AccountQueryPort cuentas;
    private final CategoryQueryPort categorias;
    private final UserRepositoryPort usuarios;
    private final InstallmentPurchaseRepositoryPort compras;
    private final Clock clock;

    /**
     * Cada cuota es un gasto confirmado de la tarjeta, programado hasta su fecha (FA-106); las ya vencidas
     * cuentan de una vez. El resumen sale del plan: recien creada, ninguna cuota se ha editado ni borrado.
     */
    @Override
    public Mono<CreatedInstallmentPurchase> create(UUID userId, CreateInstallmentPurchaseCommand alta) {
        return PlanDeCompra.de(userId, alta, cuentas, categorias, usuarios, clock)
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

    private static Transaction movimiento(InstallmentPurchase compra, ScheduledInstallment cuota) {
        return new Transaction(cuota.transactionId(), compra.userId(), TransactionType.EXPENSE, compra.accountId(),
                null, compra.categoryId(), cuota.amount(), compra.currencyCode(), compra.description(), null,
                cuota.dueAt(), TransactionStatus.CONFIRMED, TransactionOrigin.WEB, null,
                new InstallmentRef(compra.id(), cuota.number(), compra.installmentCount(), cuota.principal()));
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
}