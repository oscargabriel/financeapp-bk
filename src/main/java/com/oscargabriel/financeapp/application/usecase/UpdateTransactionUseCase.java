package com.oscargabriel.financeapp.application.usecase;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.model.UpdateTransactionCommand;
import com.oscargabriel.financeapp.domain.port.in.UpdateTransactionPort;
import com.oscargabriel.financeapp.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.domain.port.out.TransactionRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class UpdateTransactionUseCase implements UpdateTransactionPort {

    private final AccountQueryPort cuentas;
    private final CategoryQueryPort categorias;
    private final TransactionRepositoryPort repositorio;

    /**
     * El formato de cada campo ya lo valido UpdateTransactionRequest. Aqui se aplica el parche sobre
     * lo guardado y se validan las reglas que dependen del tipo resultante y de las referencias del
     * usuario. El UPDATE vuelve a filtrar por usuario: si la fila desaparecio entre la lectura y la
     * escritura, tambien es 404.
     */
    @Override
    public Mono<Transaction> update(UUID userId, UUID transactionId, UpdateTransactionCommand parche) {
        return repositorio.findByIdAndUser(transactionId, userId)
                .switchIfEmpty(Mono.error(UpdateTransactionUseCase::noEncontrado))
                .zipWith(ReferenciasDelUsuario.de(userId, cuentas, categorias))
                .map(guardadoYReferencias -> new ParcheDeMovimiento(guardadoYReferencias.getT2())
                        .aplicar(guardadoYReferencias.getT1(), parche))
                .flatMap(modificado -> repositorio.update(modificado)
                        .flatMap(actualizado -> actualizado
                                ? Mono.just(modificado)
                                : Mono.error(noEncontrado())));
    }

    /** Inexistente y ajeno dan lo mismo: distinguirlos confirmaria que el id existe para otro usuario. */
    static BadRequestException noEncontrado() {
        return new BadRequestException(HttpStatus.NOT_FOUND, List.of(
                ErrorDetail.of(ErrorCodes.NOT_FOUND.getCode(), "El movimiento no existe", "id")));
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
                    guardado.status(), guardado.origin(), guardado.recurrenceId(), guardado.installment());
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
