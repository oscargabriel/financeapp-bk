package com.oscargabriel.financeapp.core.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.ApprovalChoice;
import com.oscargabriel.financeapp.core.domain.model.Currency;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.core.domain.model.TransactionType;
import com.oscargabriel.financeapp.core.domain.model.UuidV7;
import com.oscargabriel.financeapp.core.domain.port.in.ApprovePendingTransactionPort;
import com.oscargabriel.financeapp.core.domain.port.in.ResolveExchangeRatePort;
import com.oscargabriel.financeapp.core.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.AccountRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.TransactionRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class ApprovePendingTransactionUseCase implements ApprovePendingTransactionPort {

    private final TransactionRepositoryPort repositorio;
    private final AccountRepositoryPort cuentas;
    private final CategoryRepositoryPort categorias;
    private final AccountQueryPort cuentasDelUsuario;
    private final CategoryQueryPort categoriasDelUsuario;
    private final CurrencyQueryPort monedas;
    private final ResolveExchangeRatePort tasas;
    private final Clock clock;

    /**
     * Los saldos los mueve el trigger al ver el paso de PENDING a CONFIRMED (design.md de FA-76). Antes de confirmar
     * se exige lo mismo que al confirmar un grupo (AprobacionDePendientes): cuentas activas, categoria viva y saldo.
     * Un pendiente que llego sin cuenta se aprueba con la de eleccion (FIN-66).
     */
    @Override
    public Mono<Transaction> approve(UUID userId, UUID transactionId, ApprovalChoice eleccion) {
        return PendienteDelUsuario.buscar(repositorio, transactionId, userId)
                .flatMap(pendiente -> pendiente.accountId() == null
                        ? conLaEleccion(userId, pendiente, eleccion)
                        : Mono.fromRunnable(() -> sinEleccion(eleccion)).then(comoEsta(userId, pendiente)));
    }

    private Mono<Transaction> comoEsta(UUID userId, Transaction pendiente) {
        return AprobacionDePendientes.de(List.of(pendiente), userId, cuentas, categorias, clock.instant())
                .doOnNext(aprobacion -> aprobacion.exigir(pendiente))
                .then(Mono.defer(() -> repositorio.confirm(pendiente.id(), userId)))
                .flatMap(PendienteDelUsuario::exigirEscritura)
                .thenReturn(AprobacionDePendientes.confirmado(pendiente));
    }

    /** Cambiar la cuenta o la categoria de un pendiente que ya las tiene sigue siendo un PATCH. */
    private static void sinEleccion(ApprovalChoice eleccion) {
        List<ErrorDetail> errores = new ArrayList<>();
        if (eleccion.accountId() != null) {
            errores.add(ReferenciasDelUsuario.detalle("El pendiente ya tiene cuenta: se cambia con PATCH", "accountId"));
        }
        if (eleccion.categoryId() != null) {
            errores.add(ReferenciasDelUsuario.detalle("El pendiente ya tiene categoria: se cambia con PATCH",
                    "categoryId"));
        }
        rechazarSiHay(errores);
    }

    /**
     * Las referencias se validan como en el alta (400) y el saldo como en toda aprobacion (409), sobre el monto ya
     * convertido a la moneda de la cuenta con la tasa de la fecha del pendiente (design.md de FIN-66, decision 6).
     * Si es la parte de un gasto compartido, aprobarla es pagarla.
     */
    private Mono<Transaction> conLaEleccion(UUID userId, Transaction pendiente, ApprovalChoice eleccion) {
        List<ErrorDetail> faltan = new ArrayList<>();
        if (vacio(eleccion.accountId())) {
            faltan.add(ReferenciasDelUsuario.detalle("La cuenta es obligatoria para aprobar un pendiente sin cuenta",
                    "accountId"));
        }
        if (vacio(eleccion.categoryId())) {
            faltan.add(ReferenciasDelUsuario.detalle(
                    "La categoria es obligatoria para aprobar un pendiente sin categoria", "categoryId"));
        }
        rechazarSiHay(faltan);
        Instant ahora = clock.instant();
        return Mono.zip(ReferenciasDelUsuario.de(userId, cuentasDelUsuario, categoriasDelUsuario),
                        monedas.findActive().collectMap(Currency::code))
                .flatMap(t -> {
                    ReferenciasDelUsuario referencias = t.getT1();
                    return convertido(con(pendiente, eleccion, referencias), t.getT2())
                            .map(AprobacionDePendientes::confirmado)
                            .doOnNext(confirmado -> referencias.saldos()
                                    .exigir(SaldoNoNegativo.efecto(confirmado, ahora), "amount"));
                })
                .flatMap(confirmado -> tasaDeHoy(confirmado)
                        .then(Mono.defer(() -> escribir(userId, pendiente, confirmado, ahora)))
                        .flatMap(PendienteDelUsuario::exigirEscritura)
                        .thenReturn(confirmado));
    }

    /** El pendiente con la cuenta y la categoria elegidas, todavia en su moneda: el borrador dice si falta convertir. */
    private static Borrador con(Transaction p, ApprovalChoice eleccion, ReferenciasDelUsuario referencias) {
        List<ErrorDetail> errores = new ArrayList<>();
        UUID cuenta = referencias.cuentaPropia(eleccion.accountId(), "accountId", "La cuenta", errores);
        UUID categoria = referencias.categoria(eleccion.categoryId(), p.type(), "categoryId", errores);
        rechazarSiHay(errores);
        String moneda = referencias.moneda(cuenta);
        Transaction conCuenta = new Transaction(p.id(), p.userId(), p.type(), cuenta, null, categoria, p.amount(),
                moneda, p.description(), p.notes(), p.occurredAt(), p.status(), p.origin(), p.recurrenceId(),
                p.installment(), null, null, p.groupId(), p.sharedByUserId(), p.shareId());
        return new Borrador(conCuenta, moneda.equals(p.currencyCode()) ? null : p.currencyCode(), null, List.of(),
                false);
    }

    private Mono<Transaction> convertido(Borrador borrador, Map<String, Currency> activas) {
        return new ConversorDeMontos(tasas, activas, clock.getZone()).convertir(borrador);
    }

    /** El equivalente en USD lo congela la base al escribir, con la ultima tasa guardada (FA-122). */
    private Mono<Void> tasaDeHoy(Transaction confirmado) {
        return LocalDate.ofInstant(confirmado.occurredAt(), clock.getZone()).isBefore(LocalDate.now(clock))
                ? Mono.empty()
                : tasas.refreshToday(Set.of(confirmado.currencyCode()));
    }

    private Mono<Boolean> escribir(UUID userId, Transaction pendiente, Transaction confirmado, Instant ahora) {
        if (pendiente.shareId() == null || pendiente.type() != TransactionType.EXPENSE) {
            return repositorio.confirmWith(confirmado);
        }
        Transaction ingreso = new Transaction(UuidV7.from(ahora), pendiente.sharedByUserId(), TransactionType.INCOME,
                null, null, null, pendiente.amount(), pendiente.currencyCode(), pendiente.description(), null, ahora,
                TransactionStatus.PENDING, pendiente.origin(), null, null, null, null, UuidV7.from(ahora), userId,
                pendiente.shareId());
        return repositorio.payShare(confirmado, ingreso);
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static void rechazarSiHay(List<ErrorDetail> errores) {
        if (!errores.isEmpty()) {
            throw new BadRequestException(HttpStatus.BAD_REQUEST, errores);
        }
    }
}
