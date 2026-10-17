package com.oscargabriel.financeapp.core.application.usecase;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.Account;
import com.oscargabriel.financeapp.core.domain.model.ApprovalChoice;
import com.oscargabriel.financeapp.core.domain.model.CreateTransactionCommand;
import com.oscargabriel.financeapp.core.domain.model.Currency;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.model.TransactionOrigin;
import com.oscargabriel.financeapp.core.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.core.domain.model.TransactionType;
import com.oscargabriel.financeapp.core.domain.model.UpdateTransactionCommand;
import com.oscargabriel.financeapp.core.domain.model.UuidV7;
import com.oscargabriel.financeapp.core.domain.port.in.CreateTransactionsPort;
import com.oscargabriel.financeapp.core.domain.port.in.ResolveExchangeRatePort;
import com.oscargabriel.financeapp.core.domain.port.in.TransactionPort;
import com.oscargabriel.financeapp.core.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.AccountRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.TransactionRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class TransactionUseCase implements TransactionPort {

    private final TransactionRepositoryPort repositorio;
    private final AccountRepositoryPort cuentas;
    private final CategoryRepositoryPort categorias;
    private final AccountQueryPort cuentasDelUsuario;
    private final CategoryQueryPort categoriasDelUsuario;
    private final CurrencyQueryPort monedas;
    private final ResolveExchangeRatePort tasas;
    private final CreateTransactionsPort alta;
    private final Clock clock;

    /** El alta sigue en su caso de uso porque el asistente tambien registra por ahi (design.md de FIN-131). */
    @Override
    public Flux<Transaction> create(UUID userId, TransactionOrigin origen, TransactionStatus estado,
            List<CreateTransactionCommand> lote) {
        return alta.create(userId, origen, estado, lote);
    }

    /**
     * El formato de cada campo ya lo valido UpdateTransactionRequest. Aqui se aplica el parche sobre
     * lo guardado y se validan las reglas que dependen del tipo resultante y de las referencias del
     * usuario. El UPDATE vuelve a filtrar por usuario: si la fila desaparecio entre la lectura y la
     * escritura, tambien es 404.
     *
     * Despues de los 400, el saldo (FA-133): lo que el movimiento resultante le hace a cada cuenta, menos lo que le
     * hacia el guardado. Cubre cambiar cuenta, tipo, monto, destino y fecha sin casos aparte.
     */
    @Override
    public Mono<Transaction> update(UUID userId, UUID transactionId, UpdateTransactionCommand parche) {
        return repositorio.findByIdAndUser(transactionId, userId)
                .switchIfEmpty(Mono.error(TransactionUseCase::noEncontrado))
                .flatMap(TransactionUseCase::noCompartido)
                .flatMap(guardado -> sinPartesPendientes(guardado, parche))
                .zipWith(ReferenciasDelUsuario.de(userId, cuentasDelUsuario, categoriasDelUsuario))
                .map(guardadoYReferencias -> {
                    Transaction guardado = guardadoYReferencias.getT1();
                    ReferenciasDelUsuario referencias = guardadoYReferencias.getT2();
                    Transaction modificado = new ParcheDeMovimiento(referencias).aplicar(guardado, parche);
                    Instant ahora = clock.instant();
                    referencias.saldos().exigir(SaldoNoNegativo.diferencia(SaldoNoNegativo.efecto(modificado, ahora),
                            SaldoNoNegativo.efecto(guardado, ahora)), "amount");
                    return modificado;
                })
                .flatMap(modificado -> repositorio.update(modificado)
                        .flatMap(actualizado -> actualizado
                                ? Mono.just(modificado)
                                : Mono.error(noEncontrado())));
    }

    /**
     * Mientras el gasto tenga partes pendientes, su monto y su tipo no cambian (FIN-66): son lo que las otras personas
     * deben. Solo se consulta si el parche los cambia.
     */
    private Mono<Transaction> sinPartesPendientes(Transaction guardado, UpdateTransactionCommand parche) {
        List<ErrorDetail> errores = new ArrayList<>();
        if (parche.amount() != null && parche.amount().compareTo(guardado.amount()) != 0) {
            errores.add(bloqueado("El monto no cambia mientras el gasto tenga partes pendientes", "amount"));
        }
        if (parche.type() != null && TransactionType.valueOf(parche.type().trim().toUpperCase()) != guardado.type()) {
            errores.add(bloqueado("El tipo no cambia mientras el gasto tenga partes pendientes", "type"));
        }
        return errores.isEmpty()
                ? Mono.just(guardado)
                : repositorio.hasPendingShares(guardado.id()).flatMap(conPendientes -> conPendientes
                        ? Mono.error(new BadRequestException(HttpStatus.CONFLICT, errores))
                        : Mono.just(guardado));
    }

    private static ErrorDetail bloqueado(String descripcion, String campo) {
        return ErrorDetail.of(ErrorCodes.INVALID_STATE.getCode(), descripcion, campo);
    }

    /**
     * Lee el movimiento para validar el saldo (FA-133): borrar un ingreso o una transferencia ya gastados no puede dejar
     * una cuenta en negativo. El error va en id, el de la ruta, porque el borrado no tiene cuerpo. El DELETE sigue
     * filtrando por usuario: si la fila desaparecio entre la lectura y la escritura, tambien es 404.
     */
    @Override
    public Mono<Void> delete(UUID userId, UUID transactionId) {
        return repositorio.findByIdAndUser(transactionId, userId)
                .switchIfEmpty(Mono.error(TransactionUseCase::noEncontrado))
                .flatMap(TransactionUseCase::noCompartido)
                .zipWith(cuentasDelUsuario.findByUser(userId, true).collectMap(Account::id))
                .doOnNext(guardadoYCuentas -> new SaldoNoNegativo(guardadoYCuentas.getT2()).exigir(
                        SaldoNoNegativo.diferencia(Map.of(),
                                SaldoNoNegativo.efecto(guardadoYCuentas.getT1(), clock.instant())),
                        "id"))
                .flatMap(validado -> repositorio.deleteByIdAndUser(transactionId, userId))
                .flatMap(borrado -> borrado
                        ? Mono.<Void>empty()
                        : Mono.error(noEncontrado()));
    }

    /** El correo se normaliza como en el registro, que es como se guarda (FIN-68). */
    @Override
    public Flux<Transaction> pending(UUID userId, Optional<String> sharedBy) {
        return sharedBy
                .map(correo -> repositorio.findPendingSharedBy(userId, correo.trim().toLowerCase()))
                .orElseGet(() -> repositorio.findPendingByUser(userId));
    }

    /**
     * Los saldos los mueve el trigger al ver el paso de PENDING a CONFIRMED (design.md de FA-76). Antes de confirmar
     * se exige lo mismo que al confirmar un grupo (AprobacionDePendientes): cuentas activas, categoria viva y saldo.
     * Un pendiente que llego sin cuenta se aprueba con la de eleccion (FIN-66).
     */
    @Override
    public Mono<Transaction> approve(UUID userId, UUID transactionId, ApprovalChoice eleccion) {
        return pendienteDelUsuario(transactionId, userId)
                .flatMap(pendiente -> pendiente.accountId() == null
                        ? conLaEleccion(userId, pendiente, eleccion)
                        : Mono.fromRunnable(() -> sinEleccion(eleccion)).then(comoEsta(userId, pendiente)));
    }

    private Mono<Transaction> comoEsta(UUID userId, Transaction pendiente) {
        return AprobacionDePendientes.de(List.of(pendiente), userId, cuentas, categorias, clock.instant())
                .doOnNext(aprobacion -> aprobacion.exigir(pendiente))
                .then(Mono.defer(() -> repositorio.confirm(pendiente.id(), userId)))
                .flatMap(TransactionUseCase::exigirEscritura)
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
                        .flatMap(TransactionUseCase::exigirEscritura)
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

    /** Rechazar es borrar: un pendiente no movio saldos, asi que no hay nada que revertir. */
    @Override
    public Mono<Void> reject(UUID userId, UUID transactionId) {
        return pendienteDelUsuario(transactionId, userId)
                .flatMap(pendiente -> repositorio.deletePending(transactionId, userId))
                .flatMap(TransactionUseCase::exigirEscritura);
    }

    /**
     * Todo o nada (FIN-68). Lo que no es del usuario (no existe, ya se rechazo o es ajeno) sale primero, solo, como en
     * el rechazo individual, que busca antes de mirar el estado; si no hay, los confirmados. Rechazar es borrar, con
     * el mismo arrastre de partes que el individual.
     */
    @Override
    public Mono<Void> rejectBatch(UUID userId, List<UUID> ids) {
        return repositorio.findByIdsAndUser(ids, userId)
                .collectMap(Transaction::id)
                .flatMap(suyos -> {
                    List<ErrorDetail> ausentes = erroresPorIndice(ids, i -> !suyos.containsKey(ids.get(i)),
                            ErrorCodes.NOT_FOUND, "El movimiento no existe");
                    if (!ausentes.isEmpty()) {
                        return Mono.error(new BadRequestException(HttpStatus.NOT_FOUND, ausentes));
                    }
                    List<ErrorDetail> confirmados = erroresPorIndice(ids, i -> confirmado(suyos, ids.get(i)),
                            ErrorCodes.INVALID_STATE, "El movimiento ya esta confirmado");
                    if (!confirmados.isEmpty()) {
                        return Mono.error(new BadRequestException(HttpStatus.CONFLICT, confirmados));
                    }
                    return repositorio.deleteAllPending(ids, userId);
                })
                .flatMap(borrados -> borrados ? Mono.<Void>empty() : Mono.error(loteCambiado()));
    }

    private static boolean confirmado(Map<UUID, Transaction> suyos, UUID id) {
        return suyos.get(id).status() != TransactionStatus.PENDING;
    }

    private static List<ErrorDetail> erroresPorIndice(List<UUID> ids, IntPredicate falla, ErrorCodes codigo,
            String descripcion) {
        return IntStream.range(0, ids.size())
                .filter(falla)
                .mapToObj(i -> ErrorDetail.of(codigo.getCode(), descripcion, "ids[" + i + "]"))
                .toList();
    }

    /** La escritura condicionada borro menos: otro request aprobo o rechazo alguno en medio. */
    private static BadRequestException loteCambiado() {
        return new BadRequestException(HttpStatus.NOT_FOUND, List.of(ErrorDetail.of(ErrorCodes.NOT_FOUND.getCode(),
                "Algun movimiento del lote ya no esta pendiente", "ids")));
    }

    /**
     * Todo o nada (FIN-58): se validan juntos y se confirman en una sola transaccion. Lo aprobado o rechazado uno a
     * uno ya no esta entre los pendientes y no se toca.
     */
    @Override
    public Flux<Transaction> approveGroup(UUID userId, UUID groupId) {
        return pendientesDelGrupo(groupId, userId)
                .flatMapMany(pendientes -> AprobacionDePendientes.de(pendientes, userId, cuentas, categorias,
                                clock.instant())
                        .doOnNext(aprobacion -> aprobacion.exigirGrupo(pendientes))
                        .then(Mono.defer(() -> repositorio.confirmAll(
                                pendientes.stream().map(Transaction::id).toList(), userId)))
                        .flatMap(escrito -> escrito ? Mono.empty() : Mono.error(grupoNoEncontrado()))
                        .thenMany(Flux.fromIterable(pendientes).map(AprobacionDePendientes::confirmado)));
    }

    /** Descartar es borrar, como rechazar uno: un pendiente no movio saldos (FIN-58). */
    @Override
    public Mono<Void> rejectGroup(UUID userId, UUID groupId) {
        return pendientesDelGrupo(groupId, userId)
                .flatMap(pendientes -> repositorio.deletePendingIn(
                        pendientes.stream().map(Transaction::id).toList(), userId))
                .flatMap(borrados -> borrados > 0 ? Mono.<Void>empty() : Mono.error(grupoNoEncontrado()));
    }

    // ---- La lectura que aprobar y rechazar comparten antes de escribir: el 404 y el 409 ------------------------

    /** El movimiento si es un pendiente del usuario; si no, el error que corresponde. */
    private Mono<Transaction> pendienteDelUsuario(UUID transactionId, UUID userId) {
        return repositorio.findByIdAndUser(transactionId, userId)
                .switchIfEmpty(Mono.error(TransactionUseCase::noEncontrado))
                .flatMap(t -> t.status() == TransactionStatus.PENDING
                        ? Mono.just(t)
                        : Mono.error(movimientoYaConfirmado()));
    }

    /** La escritura condicionada no afecto filas: otro request lo aprobo o lo borro en medio. */
    private static Mono<Void> exigirEscritura(boolean escrito) {
        return escrito ? Mono.empty() : Mono.error(noEncontrado());
    }

    /** Lo que otra persona compartio con el usuario solo se aprueba o se rechaza (FIN-66). */
    private static Mono<Transaction> noCompartido(Transaction t) {
        return t.pendingShared()
                ? Mono.error(new BadRequestException(HttpStatus.CONFLICT, List.of(ErrorDetail.of(
                        ErrorCodes.INVALID_STATE.getCode(),
                        "Un pendiente compartido solo se aprueba o se rechaza", "status"))))
                : Mono.just(t);
    }

    /** Inexistente y ajeno dan lo mismo: distinguirlos confirmaria que el id existe para otro usuario. */
    private static BadRequestException noEncontrado() {
        return new BadRequestException(HttpStatus.NOT_FOUND, List.of(
                ErrorDetail.of(ErrorCodes.NOT_FOUND.getCode(), "El movimiento no existe", "id")));
    }

    private static BadRequestException movimientoYaConfirmado() {
        return new BadRequestException(HttpStatus.CONFLICT, List.of(ErrorDetail.of(
                ErrorCodes.INVALID_STATE.getCode(), "El movimiento ya esta confirmado", "status")));
    }

    // ---- La lectura que confirmar y descartar un grupo comparten antes de escribir (FIN-58) ---------------------

    /**
     * Lo que sigue pendiente del grupo, en el orden en que entro. Sin movimientos del usuario en el grupo (no existe,
     * es de otro o ya se descarto) es 404; con movimientos pero ninguno pendiente, ya se confirmo: 409.
     */
    private Mono<List<Transaction>> pendientesDelGrupo(UUID groupId, UUID userId) {
        return repositorio.findByGroup(groupId, userId).collectList()
                .flatMap(grupo -> {
                    if (grupo.isEmpty()) {
                        return Mono.error(grupoNoEncontrado());
                    }
                    List<Transaction> pendientes = grupo.stream()
                            .filter(t -> t.status() == TransactionStatus.PENDING).toList();
                    return pendientes.isEmpty() ? Mono.error(grupoYaConfirmado()) : Mono.just(pendientes);
                });
    }

    private static BadRequestException grupoNoEncontrado() {
        return new BadRequestException(HttpStatus.NOT_FOUND, List.of(
                ErrorDetail.of(ErrorCodes.NOT_FOUND.getCode(), "El grupo no existe", "groupId")));
    }

    private static BadRequestException grupoYaConfirmado() {
        return new BadRequestException(HttpStatus.CONFLICT, List.of(ErrorDetail.of(
                ErrorCodes.INVALID_STATE.getCode(), "El grupo ya esta confirmado", "status")));
    }

    /** Aplica un parche sobre un movimiento guardado y acumula todos sus errores antes de lanzar. */
    private static final class ParcheDeMovimiento {

        private final ReferenciasDelUsuario referencias;
        private final List<ErrorDetail> errores = new ArrayList<>();

        ParcheDeMovimiento(ReferenciasDelUsuario referencias) {
            this.referencias = referencias;
        }

        Transaction aplicar(Transaction guardado, UpdateTransactionCommand parche) {
            TransactionType tipo = parche.type() == null
                    ? guardado.type()
                    : TransactionType.valueOf(parche.type().trim().toUpperCase());
            UUID cuenta = parche.accountId() == null
                    ? guardado.accountId()
                    : referencias.cuentaPropia(parche.accountId(), "accountId", "La cuenta", errores);

            UUID destino;
            UUID categoria;
            if (tipo == TransactionType.TRANSFER) {
                destino = destino(guardado, parche, cuenta);
                categoria = null;
                if (parche.categoryId() != null) {
                    errores.add(ReferenciasDelUsuario.detalle("Una transferencia no lleva categoria", "categoryId"));
                }
            } else {
                destino = null;
                categoria = categoria(guardado, parche, tipo);
                if (parche.destinationAccountId() != null) {
                    errores.add(ReferenciasDelUsuario.detalle("Solo una transferencia lleva cuenta destino",
                            "destinationAccountId"));
                }
            }

            if (parche.accountId() != null && cuenta != null
                    && !referencias.moneda(cuenta).equals(guardado.currencyCode())) {
                errores.add(ReferenciasDelUsuario.detalle("La cuenta tiene que estar en " + guardado.currencyCode()
                        + ", la moneda del movimiento", "accountId"));
            }
            BigDecimal montoDestino = montoDestino(guardado, parche, tipo, destino);

            if (!errores.isEmpty()) {
                throw new BadRequestException(HttpStatus.BAD_REQUEST, errores);
            }
            return new Transaction(guardado.id(), guardado.userId(), tipo, cuenta, destino, categoria,
                    parche.amount() == null ? guardado.amount() : parche.amount(), guardado.currencyCode(),
                    parche.description() == null ? guardado.description() : parche.description().trim(),
                    guardado.notes(),
                    parche.occurredAt() == null
                            ? guardado.occurredAt()
                            : OffsetDateTime.parse(parche.occurredAt().trim()).toInstant(),
                    guardado.status(), guardado.origin(), guardado.recurrenceId(), guardado.installment(), montoDestino, guardado.original(), guardado.groupId(), guardado.sharedByUserId(), guardado.shareId());
        }

        /**
         * Lo que entra al destino solo existe en una transferencia entre monedas distintas (FA-51). Vale el del parche;
         * si no trae, el guardado, mientras el destino siga en la misma moneda. Sin ninguno, no hay como mover el
         * destino. El original de un convertido no se toca: registra lo que llego.
         */
        private BigDecimal montoDestino(Transaction guardado, UpdateTransactionCommand parche, TransactionType tipo,
                UUID destino) {
            if (tipo == TransactionType.TRANSFER && destino == null) {
                return null;
            }
            boolean entreMonedas = tipo == TransactionType.TRANSFER
                    && !referencias.moneda(destino).equals(guardado.currencyCode());
            if (!entreMonedas) {
                if (parche.destinationAmount() != null) {
                    errores.add(ReferenciasDelUsuario.detalle(
                            "El monto de destino solo aplica a una transferencia entre cuentas de monedas distintas",
                            "destinationAmount"));
                }
                return null;
            }
            if (parche.destinationAmount() != null) {
                return parche.destinationAmount();
            }
            if (guardado.destinationAmount() != null
                    && referencias.moneda(guardado.destinationAccountId()).equals(referencias.moneda(destino))) {
                return guardado.destinationAmount();
            }
            errores.add(ReferenciasDelUsuario.detalle(
                    "Una transferencia entre cuentas de monedas distintas necesita destinationAmount",
                    "destinationAmount"));
            return null;
        }

        /** La guardada sirve si ya era transferencia; si no, el parche tiene que traerla. */
        private UUID destino(Transaction guardado, UpdateTransactionCommand parche, UUID cuenta) {
            UUID destino;
            if (parche.destinationAccountId() != null) {
                destino = referencias.cuentaPropia(parche.destinationAccountId(), "destinationAccountId",
                        "La cuenta destino", errores);
            } else if (guardado.destinationAccountId() != null) {
                destino = guardado.destinationAccountId();
            } else {
                errores.add(ReferenciasDelUsuario.detalle("La cuenta destino es obligatoria", "destinationAccountId"));
                return null;
            }
            if (destino != null && destino.equals(cuenta)) {
                errores.add(ReferenciasDelUsuario.detalle("La cuenta destino tiene que ser distinta de la de origen",
                        "destinationAccountId"));
            }
            return destino;
        }

        /**
         * La guardada se conserva sin revalidar mientras el tipo no cambie. Si cambia entre gasto e
         * ingreso, su alcance puede dejar de servir; si el movimiento era transferencia, no hay ninguna.
         */
        private UUID categoria(Transaction guardado, UpdateTransactionCommand parche, TransactionType tipo) {
            if (parche.categoryId() != null) {
                return referencias.categoria(parche.categoryId(), tipo, "categoryId", errores);
            }
            if (guardado.categoryId() == null) {
                errores.add(ReferenciasDelUsuario.detalle("La categoria es obligatoria en un gasto o un ingreso",
                        "categoryId"));
                return null;
            }
            return tipo == guardado.type()
                    ? guardado.categoryId()
                    : referencias.categoria(guardado.categoryId().toString(), tipo, "categoryId", errores);
        }
    }
}
