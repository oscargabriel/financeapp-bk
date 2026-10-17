package com.oscargabriel.financeapp.core.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.core.domain.model.AffiliateBalance;
import com.oscargabriel.financeapp.core.domain.model.Affiliation;
import com.oscargabriel.financeapp.core.domain.model.AffiliationAction;
import com.oscargabriel.financeapp.core.domain.model.AffiliationStatus;
import com.oscargabriel.financeapp.core.domain.model.AffiliationView;
import com.oscargabriel.financeapp.core.domain.model.ShareWithAffiliate;
import com.oscargabriel.financeapp.core.domain.model.UserCredentials;
import com.oscargabriel.financeapp.core.domain.model.UuidV7;
import com.oscargabriel.financeapp.core.domain.port.in.AffiliationPort;
import com.oscargabriel.financeapp.core.domain.port.out.AffiliateBalanceQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.AffiliationRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class AffiliationUseCase implements AffiliationPort {

    /** Las que quedaron aceptadas alguna vez: las demas nunca tuvieron a nadie con quien compartir. */
    private static final Set<AffiliationStatus> CON_PARTES = Set.of(AffiliationStatus.ACCEPTED, AffiliationStatus.UNLINKED);

    private final AffiliationRepositoryPort afiliaciones;
    private final UserRepositoryPort usuarios;
    private final AffiliateBalanceQueryPort saldos;
    private final Clock clock;

    @Override
    public Flux<AffiliationView> list(UUID userId) {
        return Flux.defer(() -> afiliaciones.findOpenByUser(userId));
    }

    @Override
    public Flux<AffiliateBalance> balances(UUID userId) {
        return Flux.defer(() -> saldos.findBalances(userId));
    }

    /** Las partes no guardan la afiliacion: salen todas las de los dos usuarios, sin importar con cual se crearon. */
    @Override
    public Flux<ShareWithAffiliate> shares(UUID userId, UUID affiliationId) {
        return Mono.defer(() -> afiliaciones.findForParticipant(affiliationId, userId))
                .switchIfEmpty(Mono.error(AffiliationUseCase::afiliacionNoEncontrada))
                .flatMapMany(relacion -> CON_PARTES.contains(relacion.status())
                        ? saldos.findSharesBetween(userId, otraPersona(relacion, userId))
                        : Flux.error(sinPartes()));
    }

    /**
     * Un usuario borrado o desactivado cuenta como correo sin cuenta, igual que en el login. La
     * respuesta es la misma en los dos casos: solo cambia si se guarda el destinatario.
     */
    @Override
    public Mono<AffiliationView> send(UUID userId, String email) {
        return Mono.defer(() -> {
            String correo = email.trim().toLowerCase();
            return usuarios.findActiveByEmail(correo)
                    .map(UserCredentials::id)
                    .map(Optional::of)
                    .defaultIfEmpty(Optional.empty())
                    .flatMap(destinatario -> enviar(userId, correo, destinatario.orElse(null)));
        });
    }

    @Override
    public Mono<AffiliationView> accept(UUID userId, UUID affiliationId) {
        return cambiar(userId, affiliationId, AffiliationAction.ACCEPT);
    }

    @Override
    public Mono<AffiliationView> reject(UUID userId, UUID affiliationId) {
        return cambiar(userId, affiliationId, AffiliationAction.REJECT);
    }

    @Override
    public Mono<AffiliationView> cancel(UUID userId, UUID affiliationId) {
        return cambiar(userId, affiliationId, AffiliationAction.CANCEL);
    }

    @Override
    public Mono<AffiliationView> unlink(UUID userId, UUID affiliationId) {
        return cambiar(userId, affiliationId, AffiliationAction.UNLINK);
    }

    /**
     * Lee antes de escribir para distinguir el 404 de una ajena del 409 de una accion que no aplica. La
     * escritura va condicionada al estado leido: si otra peticion la cambio en medio, tambien es 409.
     */
    private Mono<AffiliationView> cambiar(UUID userId, UUID affiliationId, AffiliationAction action) {
        return Mono.defer(() -> afiliaciones.findForParticipant(affiliationId, userId))
                .switchIfEmpty(Mono.error(AffiliationUseCase::solicitudNoEncontrada))
                .flatMap(relacion -> action.appliesTo(relacion, userId)
                        ? afiliaciones.changeStatus(affiliationId, action.from(), action.to())
                        : Mono.just(false))
                .flatMap(cambiada -> cambiada
                        ? afiliaciones.findViewForParticipant(affiliationId, userId)
                        : Mono.error(accionInvalida(action)));
    }

    private Mono<AffiliationView> enviar(UUID userId, String correo, UUID destinatario) {
        if (userId.equals(destinatario)) {
            return Mono.error(new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                    "No puedes enviarte una solicitud a ti mismo", "email"));
        }
        return afiliaciones.existsOpenVisible(userId, correo, destinatario)
                .flatMap(existe -> existe
                        ? Mono.error(duplicada())
                        : guardar(userId, correo, destinatario));
    }

    private Mono<AffiliationView> guardar(UUID userId, String correo, UUID destinatario) {
        Instant ahora = clock.instant();
        Affiliation solicitud = new Affiliation(UuidV7.from(ahora), userId, correo, destinatario,
                AffiliationStatus.REQUESTED, ahora, ahora);
        return afiliaciones.save(solicitud).thenReturn(AffiliationView.sent(solicitud));
    }

    private static UUID otraPersona(Affiliation relacion, UUID userId) {
        return relacion.sentBy(userId) ? relacion.addresseeId() : relacion.requesterId();
    }

    private static BadRequestException duplicada() {
        return new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_RESOURCE,
                "Ya tienes una solicitud abierta o una afiliacion con esa persona", "email");
    }

    private static BadRequestException solicitudNoEncontrada() {
        return new BadRequestException(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND,
                "La solicitud de afiliacion no existe", "id");
    }

    private static BadRequestException accionInvalida(AffiliationAction action) {
        return new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.INVALID_STATE,
                switch (action) {
                    case ACCEPT -> "Solo quien recibe una solicitud abierta puede aceptarla";
                    case REJECT -> "Solo quien recibe una solicitud abierta puede rechazarla";
                    case CANCEL -> "Solo quien envia una solicitud abierta puede cancelarla";
                    case UNLINK -> "Solo se puede desvincular una afiliacion aceptada";
                }, "status");
    }

    private static BadRequestException afiliacionNoEncontrada() {
        return new BadRequestException(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND,
                "La afiliacion no existe", "id");
    }

    private static BadRequestException sinPartes() {
        return new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.INVALID_STATE,
                "Solo hay partes con una afiliacion aceptada o desvinculada", "status");
    }
}
