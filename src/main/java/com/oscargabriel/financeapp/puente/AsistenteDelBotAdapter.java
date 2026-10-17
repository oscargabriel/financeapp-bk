package com.oscargabriel.financeapp.puente;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.ia.domain.model.AssistantBalance;
import com.oscargabriel.financeapp.ia.domain.model.AssistantChannel;
import com.oscargabriel.financeapp.ia.domain.model.AssistantFile;
import com.oscargabriel.financeapp.ia.domain.model.AssistantFileType;
import com.oscargabriel.financeapp.ia.domain.model.AssistantMessage;
import com.oscargabriel.financeapp.ia.domain.model.AssistantReply;
import com.oscargabriel.financeapp.ia.domain.model.AssistantReport;
import com.oscargabriel.financeapp.ia.domain.model.CreatedMovement;
import com.oscargabriel.financeapp.ia.domain.port.in.AssistantPort;
import com.oscargabriel.financeapp.telegram.domain.model.RespuestaDelAsistente;
import com.oscargabriel.financeapp.telegram.domain.port.out.AsistenteDelBotPort;

import reactor.core.publisher.Mono;

/**
 * Puente telegram → ia (FIN-61): el mensaje del chat entra al asistente por el canal de Telegram. El tipo del archivo
 * lo sigue decidiendo ia (design.md de FIN-61, decision 2).
 */
@Component
@AllArgsConstructor
public class AsistenteDelBotAdapter implements AsistenteDelBotPort {

    private final AssistantPort asistente;

    @Override
    public Mono<RespuestaDelAsistente> atender(UUID usuario, String texto, String mime, byte[] archivo) {
        return Mono.defer(() -> {
            List<AssistantFile> archivos = List.of();
            if (archivo != null) {
                Optional<AssistantFile> admitido = admitido(mime, archivo);
                if (admitido.isEmpty()) {
                    return Mono.just(new RespuestaDelAsistente.ArchivoNoAdmitido());
                }
                archivos = List.of(admitido.get());
            }
            String conTexto = texto == null || texto.isBlank() ? null : texto;
            return asistente.atender(usuario, AssistantChannel.TELEGRAM, new AssistantMessage(conTexto, archivos))
                    .map(AsistenteDelBotAdapter::respuesta);
        });
    }

    /** Vacio si el tipo no se reconoce o AssistantFile rechaza el tamano. */
    private static Optional<AssistantFile> admitido(String mime, byte[] archivo) {
        try {
            return AssistantFileType.de(mime, archivo).map(tipo -> new AssistantFile(tipo, archivo));
        } catch (IllegalArgumentException rechazado) {
            return Optional.empty();
        }
    }

    /** Sin default: un intent nuevo sin traduccion no compila. */
    private static RespuestaDelAsistente respuesta(AssistantReply respuesta) {
        return switch (respuesta.intent()) {
            case CREATE_TRANSACTION -> new RespuestaDelAsistente.Registro(respuesta.group().id(),
                    respuesta.group().transactions().stream().map(AsistenteDelBotAdapter::movimiento).toList());
            case LIST_TRANSACTIONS -> movimientos(respuesta.report());
            case GET_BALANCE -> saldo(respuesta.balance());
            case NEEDS_CLARIFICATION, UNSUPPORTED -> new RespuestaDelAsistente.Texto(respuesta.message());
        };
    }

    private static RespuestaDelAsistente.Movimiento movimiento(CreatedMovement m) {
        return new RespuestaDelAsistente.Movimiento(m.type().name(), m.account(), m.destinationAccount(),
                m.category(), m.amount(), m.currencyCode(), m.description(), m.occurredAt());
    }

    private static RespuestaDelAsistente.Movimientos movimientos(AssistantReport r) {
        return new RespuestaDelAsistente.Movimientos(r.from(), r.to(), r.currencyCode(), r.income(), r.expense(),
                r.net(), r.transactions().stream()
                        .map(t -> new RespuestaDelAsistente.Movimiento(t.type().name(), t.account(), null,
                                t.category(), t.amount(), t.currencyCode(), t.description(), t.occurredAt()))
                        .toList());
    }

    private static RespuestaDelAsistente.Saldo saldo(AssistantBalance b) {
        return new RespuestaDelAsistente.Saldo(b.from(), b.to(), b.currencyCode(), b.income(), b.expense(), b.net(),
                b.allTimeNet(), b.accounts().stream()
                        .map(c -> new RespuestaDelAsistente.Cuenta(c.name(), c.currencyCode(), c.currentBalance()))
                        .toList());
    }
}
