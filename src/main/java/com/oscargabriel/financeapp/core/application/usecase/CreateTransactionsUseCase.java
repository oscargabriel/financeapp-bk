package com.oscargabriel.financeapp.core.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.core.domain.model.Account;
import com.oscargabriel.financeapp.core.domain.model.Category;
import com.oscargabriel.financeapp.core.domain.model.CategoryScope;
import com.oscargabriel.financeapp.core.domain.model.CreateTransactionCommand;
import com.oscargabriel.financeapp.core.domain.model.Currency;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.model.TransactionOrigin;
import com.oscargabriel.financeapp.core.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.core.domain.model.UuidV7;
import com.oscargabriel.financeapp.core.domain.port.in.CreateTransactionsPort;
import com.oscargabriel.financeapp.core.domain.port.in.ResolveExchangeRatePort;
import com.oscargabriel.financeapp.core.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.TransactionRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class CreateTransactionsUseCase implements CreateTransactionsPort {

    private final AccountQueryPort cuentas;
    private final CategoryQueryPort categorias;
    private final CurrencyQueryPort monedas;
    private final TransactionRepositoryPort repositorio;
    private final ResolveExchangeRatePort tasas;
    private final Clock clock;

    /**
     * Lee una sola vez las cuentas y categorias del usuario, valida el lote entero contra ellas y solo
     * entonces escribe. Leer todas las del usuario, en vez de las del lote, cuesta dos consultas fijas
     * sea cual sea el tamano del lote. El tamano y el formato de cada elemento ya vienen validados por
     * el controlador y CreateTransactionRequest.
     *
     * El reloj se lee una vez por lote: los elementos sin fecha comparten el instante de la peticion.
     *
     * Lo que llego en una moneda distinta a la de su cuenta se convierte con la tasa de su fecha y queda pendiente
     * (FA-51, design.md). Antes de guardar se pide la tasa de hoy de las monedas con elementos de hoy o
     * posteriores (FA-122): el equivalente en USD lo congela la base al insertar, con la ultima tasa guardada.
     *
     * El saldo se valida despues de convertir, cuando cada monto ya esta en la moneda de su cuenta y lo convertido ya
     * quedo pendiente (FA-133). Ahi mismo los pendientes del lote reciben su grupo (FIN-58).
     */
    @Override
    public Flux<Transaction> create(UUID userId, TransactionOrigin origen, TransactionStatus estado,
            List<CreateTransactionCommand> lote) {
        return Flux.defer(() -> {
            Mono<Map<UUID, Account>> suyas = cuentas.findByUser(userId, true)
                    .collectMap(Account::id);
            Mono<Map<UUID, Category>> vivas = categorias
                    .findActiveByUser(userId, EnumSet.allOf(CategoryScope.class))
                    .collectMap(Category::id);
            Mono<Map<String, Currency>> activas = monedas.findActive().collectMap(Currency::code);

            Instant ahora = clock.instant();
            Iterator<UUID> ids = idsCrecientes(lote.size());
            return Mono.zip(suyas, vivas, activas)
                    .flatMap(referencias -> convertir(new TransactionBatchValidator(userId, referencias.getT1(),
                            referencias.getT2(), referencias.getT3(), ids::next, ahora,
                            origen, estado).aBorradores(lote), referencias.getT3())
                            .map(this::agruparPendientes)
                            .doOnNext(movimientos -> new SaldoNoNegativo(referencias.getT1())
                                    .exigirLote(movimientos, ahora)))
                    .flatMapMany(movimientos -> tasasDeHoy(movimientos).thenMany(repositorio.saveAll(movimientos)));
        });
    }

    /**
     * Ordenados, para que el orden por id de un grupo sea el del lote: dos UuidV7 del mismo milisegundo salen en orden
     * aleatorio, y los errores de confirmar un grupo llevan la posicion de cada pendiente (FIN-58).
     */
    private Iterator<UUID> idsCrecientes(int cuantos) {
        return Stream.generate(() -> UuidV7.from(clock.instant())).limit(cuantos).sorted().iterator();
    }

    /** Los pendientes de una misma peticion se confirman o se descartan juntos (FIN-58). */
    private List<Transaction> agruparPendientes(List<Transaction> movimientos) {
        if (movimientos.stream().noneMatch(t -> t.status() == TransactionStatus.PENDING)) {
            return movimientos;
        }
        UUID grupo = UuidV7.from(clock.instant());
        return movimientos.stream()
                .map(t -> t.status() == TransactionStatus.PENDING ? enGrupo(t, grupo) : t)
                .toList();
    }

    private static Transaction enGrupo(Transaction t, UUID grupo) {
        return new Transaction(t.id(), t.userId(), t.type(), t.accountId(), t.destinationAccountId(), t.categoryId(),
                t.amount(), t.currencyCode(), t.description(), t.notes(), t.occurredAt(), t.status(), t.origin(),
                t.recurrenceId(), t.installment(), t.destinationAmount(), t.original(), grupo);
    }

    /** En el orden del lote: el lote vuelve en ese orden, y el conversor no pide dos veces la misma tasa. */
    private Mono<List<Transaction>> convertir(List<Borrador> borradores, Map<String, Currency> activas) {
        ConversorDeMontos conversor = new ConversorDeMontos(tasas, activas, clock.getZone());
        return Flux.fromIterable(borradores).concatMap(conversor::convertir).collectList();
    }

    private Mono<Void> tasasDeHoy(List<Transaction> movimientos) {
        LocalDate hoy = LocalDate.now(clock);
        Set<String> monedas = movimientos.stream()
                .filter(t -> !LocalDate.ofInstant(t.occurredAt(), clock.getZone()).isBefore(hoy))
                .map(Transaction::currencyCode)
                .collect(Collectors.toSet());
        return monedas.isEmpty() ? Mono.empty() : tasas.refreshToday(monedas);
    }
}
