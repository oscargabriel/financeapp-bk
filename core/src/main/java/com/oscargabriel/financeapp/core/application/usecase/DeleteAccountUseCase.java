package com.oscargabriel.financeapp.core.application.usecase;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.Account;
import com.oscargabriel.financeapp.core.domain.port.in.DeleteAccountPort;
import com.oscargabriel.financeapp.core.domain.port.out.AccountRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.RecurrenceRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class DeleteAccountUseCase implements DeleteAccountPort {

    private final AccountRepositoryPort cuentas;
    private final RecurrenceRepositoryPort series;

    /**
     * A diferencia de una categoria, el borrado depende de la fila: una cuenta con saldo sacaria ese
     * dinero, o esa deuda, del listado sin que el usuario lo decida. Por eso lee antes de marcar. Lo
     * programado tampoco cuenta en el saldo vigente y se quedaria en la cuenta borrada (FA-115).
     */
    @Override
    public Mono<Void> delete(UUID userId, UUID accountId) {
        return cuentas.findActiveByIdAndUser(accountId, userId)
                .switchIfEmpty(Mono.error(UpdateAccountUseCase::noEncontrada))
                .flatMap(cuenta -> Mono.zip(cuentas.countScheduled(accountId),
                                series.countActiveByAccount(accountId, userId))
                        .flatMap(enUso -> {
                            List<ErrorDetail> errores = impedimentos(cuenta, enUso.getT1(), enUso.getT2());
                            return errores.isEmpty()
                                    ? cuentas.softDelete(accountId, userId)
                                    : Mono.error(new BadRequestException(HttpStatus.CONFLICT, errores));
                        }))
                .flatMap(borrada -> borrada
                        ? Mono.<Void>empty()
                        : Mono.error(UpdateAccountUseCase.noEncontrada()));
    }

    /** Los dos juntos: el usuario no descubre el segundo despues de resolver el primero. */
    private static List<ErrorDetail> impedimentos(Account cuenta, long programados, long seriesActivas) {
        List<ErrorDetail> errores = new ArrayList<>();
        if (cuenta.currentBalance().signum() != 0) {
            errores.add(enUso("La cuenta tiene saldo: dejalo en cero antes de borrarla", "currentBalance"));
        }
        if (programados > 0 || seriesActivas > 0) {
            errores.add(enUso(enUsoPor(programados, seriesActivas), "id"));
        }
        return errores;
    }

    /**
     * Un solo error para los dos: toda serie sin fin activa tiene una programada, y dos errores en id dirian
     * lo mismo. Una serie cuya programada se borro a mano bloquea igual (FA-116), o seguiria creando
     * ocurrencias en la cuenta borrada.
     */
    private static String enUsoPor(long programados, long seriesActivas) {
        List<String> partes = new ArrayList<>();
        if (seriesActivas > 0) {
            partes.add(UpdateAccountUseCase.seriesActivas(seriesActivas));
        }
        if (programados > 0) {
            partes.add(programados + (programados == 1 ? " movimiento programado" : " movimientos programados"));
        }
        String solucion = programados > 0
                ? "cancela la serie o la compra en cuotas, o borra esos movimientos, antes de borrarla"
                : "cancela " + (seriesActivas == 1 ? "la serie" : "las series") + " antes de borrarla";
        return "La cuenta tiene " + String.join(" y ", partes) + ": " + solucion;
    }

    private static ErrorDetail enUso(String descripcion, String campo) {
        return ErrorDetail.of(ErrorCodes.RESOURCE_IN_USE.getCode(), descripcion, campo);
    }
}
