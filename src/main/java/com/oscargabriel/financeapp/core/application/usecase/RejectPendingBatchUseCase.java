package com.oscargabriel.financeapp.core.application.usecase;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.core.domain.port.in.RejectPendingBatchPort;
import com.oscargabriel.financeapp.core.domain.port.out.TransactionRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class RejectPendingBatchUseCase implements RejectPendingBatchPort {

    private final TransactionRepositoryPort repositorio;

    /**
     * Todo o nada (FIN-68). Lo que no es del usuario (no existe, ya se rechazo o es ajeno) sale primero, solo, como en
     * el rechazo individual, que busca antes de mirar el estado; si no hay, los confirmados. Rechazar es borrar, con
     * el mismo arrastre de partes que el individual.
     */
    @Override
    public Mono<Void> reject(UUID userId, List<UUID> ids) {
        return repositorio.findByIdsAndUser(ids, userId)
                .collectMap(Transaction::id)
                .flatMap(suyos -> {
                    List<ErrorDetail> ausentes = errores(ids, i -> !suyos.containsKey(ids.get(i)),
                            ErrorCodes.NOT_FOUND, "El movimiento no existe");
                    if (!ausentes.isEmpty()) {
                        return Mono.error(new BadRequestException(HttpStatus.NOT_FOUND, ausentes));
                    }
                    List<ErrorDetail> confirmados = errores(ids, i -> confirmado(suyos, ids.get(i)),
                            ErrorCodes.INVALID_STATE, "El movimiento ya esta confirmado");
                    if (!confirmados.isEmpty()) {
                        return Mono.error(new BadRequestException(HttpStatus.CONFLICT, confirmados));
                    }
                    return repositorio.deleteAllPending(ids, userId);
                })
                .flatMap(borrados -> borrados ? Mono.<Void>empty() : Mono.error(cambiado()));
    }

    private static boolean confirmado(Map<UUID, Transaction> suyos, UUID id) {
        return suyos.get(id).status() != TransactionStatus.PENDING;
    }

    private static List<ErrorDetail> errores(List<UUID> ids, IntPredicate falla, ErrorCodes codigo,
            String descripcion) {
        return IntStream.range(0, ids.size())
                .filter(falla)
                .mapToObj(i -> ErrorDetail.of(codigo.getCode(), descripcion, "ids[" + i + "]"))
                .toList();
    }

    /** La escritura condicionada borro menos: otro request aprobo o rechazo alguno en medio. */
    private static BadRequestException cambiado() {
        return new BadRequestException(HttpStatus.NOT_FOUND, List.of(ErrorDetail.of(ErrorCodes.NOT_FOUND.getCode(),
                "Algun movimiento del lote ya no esta pendiente", "ids")));
    }
}
