package com.oscargabriel.financeapp.core.application.usecase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.Account;
import com.oscargabriel.financeapp.core.domain.model.Category;
import com.oscargabriel.financeapp.core.domain.model.CategoryScope;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.core.domain.model.TransactionType;
import com.oscargabriel.financeapp.core.domain.port.out.AccountRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Lo que aprobar exige, uno a uno o en grupo (FIN-58): las cuentas activas, la categoria viva y del tipo, y que las
 * cuentas queden con saldo (FA-133). Los errores de estado salen solos, sin los de saldo. El trigger no mira is_active
 * ni la categoria: sin esto, aprobar moveria el saldo de una cuenta que el alta ya no acepta (FA-102).
 */
final class AprobacionDePendientes {

    private final Map<UUID, Account> cuentas;
    private final Map<UUID, Category> categorias;
    private final Instant ahora;

    AprobacionDePendientes(Map<UUID, Account> cuentas, Map<UUID, Category> categorias, Instant ahora) {
        this.cuentas = cuentas;
        this.categorias = categorias;
        this.ahora = ahora;
    }

    /**
     * Las cuentas y las categorias vivas que nombran los pendientes. Una cuenta ausente solo seria una borrada, y esas
     * ya no tienen pendientes; una categoria ausente es una borrada.
     */
    static Mono<AprobacionDePendientes> de(List<Transaction> pendientes, UUID userId, AccountRepositoryPort cuentas,
            CategoryRepositoryPort categorias, Instant ahora) {
        Mono<Map<UUID, Account>> suyas = Flux.fromStream(pendientes.stream()
                        .flatMap(t -> Stream.of(t.accountId(), t.destinationAccountId()))
                        .filter(Objects::nonNull).distinct())
                .concatMap(id -> cuentas.findActiveByIdAndUser(id, userId))
                .collectMap(Account::id);
        Mono<Map<UUID, Category>> vivas = Flux.fromStream(pendientes.stream()
                        .map(Transaction::categoryId).filter(Objects::nonNull).distinct())
                .concatMap(id -> categorias.findActiveByIdAndUser(id, userId))
                .collectMap(Category::id);
        return Mono.zip(suyas, vivas).map(t -> new AprobacionDePendientes(t.getT1(), t.getT2(), ahora));
    }

    void exigir(Transaction pendiente) {
        rechazarSiHay(deEstado(pendiente, ""));
        new SaldoNoNegativo(cuentas).exigir(SaldoNoNegativo.efecto(confirmado(pendiente), ahora), "amount");
    }

    /** Cada error lleva el indice del pendiente en el grupo; el saldo, sobre el efecto de todos juntos. */
    void exigirGrupo(List<Transaction> pendientes) {
        rechazarSiHay(IntStream.range(0, pendientes.size())
                .mapToObj(i -> deEstado(pendientes.get(i), "[" + i + "]."))
                .flatMap(List::stream)
                .toList());
        new SaldoNoNegativo(cuentas).exigirLote(pendientes.stream().map(AprobacionDePendientes::confirmado).toList(),
                ahora);
    }

    static Transaction confirmado(Transaction t) {
        return new Transaction(t.id(), t.userId(), t.type(), t.accountId(), t.destinationAccountId(),
                t.categoryId(), t.amount(), t.currencyCode(), t.description(), t.notes(), t.occurredAt(),
                TransactionStatus.CONFIRMED, t.origin(), t.recurrenceId(), t.installment(), t.destinationAmount(),
                t.original(), t.groupId(), t.sharedByUserId(), t.shareId());
    }

    private List<ErrorDetail> deEstado(Transaction t, String prefijo) {
        List<ErrorDetail> errores = new ArrayList<>();
        // Un pendiente compartido llega sin cuenta ni categoria: se aprueba uno a uno, eligiendolas (FIN-66).
        if (t.accountId() == null) {
            errores.add(invalido("El pendiente no tiene cuenta: se aprueba uno a uno, con la cuenta y la categoria",
                    prefijo + "accountId"));
            return errores;
        }
        if (desactivada(t.accountId())) {
            errores.add(invalido("La cuenta esta desactivada", prefijo + "accountId"));
        }
        if (desactivada(t.destinationAccountId())) {
            errores.add(invalido("La cuenta destino esta desactivada", prefijo + "destinationAccountId"));
        }
        if (t.type() != TransactionType.TRANSFER) {
            Category categoria = categorias.get(t.categoryId());
            if (categoria == null) {
                errores.add(invalido("La categoria esta borrada", prefijo + "categoryId"));
            } else if (!alcance(t.type()).compatibles().contains(categoria.appliesTo())) {
                errores.add(invalido("La categoria no aplica a un movimiento de tipo " + t.type(),
                        prefijo + "categoryId"));
            }
        }
        return errores;
    }

    private boolean desactivada(UUID cuenta) {
        Account guardada = cuenta == null ? null : cuentas.get(cuenta);
        return guardada != null && !guardada.active();
    }

    private static CategoryScope alcance(TransactionType tipo) {
        return tipo == TransactionType.EXPENSE ? CategoryScope.EXPENSE : CategoryScope.INCOME;
    }

    private static ErrorDetail invalido(String descripcion, String campo) {
        return ErrorDetail.of(ErrorCodes.INVALID_STATE.getCode(), descripcion, campo);
    }

    private static void rechazarSiHay(List<ErrorDetail> errores) {
        if (!errores.isEmpty()) {
            throw new BadRequestException(HttpStatus.CONFLICT, errores);
        }
    }
}
