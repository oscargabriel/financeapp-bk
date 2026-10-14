package com.oscargabriel.financeapp.core.application.usecase;

import java.util.Set;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.core.domain.model.Affiliation;
import com.oscargabriel.financeapp.core.domain.model.AffiliationStatus;
import com.oscargabriel.financeapp.core.domain.model.ShareWithAffiliate;
import com.oscargabriel.financeapp.core.domain.port.in.ListSharesWithAffiliatePort;
import com.oscargabriel.financeapp.core.domain.port.out.AffiliateBalanceQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.AffiliationRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class ListSharesWithAffiliateUseCase implements ListSharesWithAffiliatePort {

    /** Las que quedaron aceptadas alguna vez: las demas nunca tuvieron a nadie con quien compartir. */
    private static final Set<AffiliationStatus> CON_PARTES = Set.of(AffiliationStatus.ACCEPTED, AffiliationStatus.UNLINKED);

    private final AffiliationRepositoryPort afiliaciones;
    private final AffiliateBalanceQueryPort saldos;

    /** Las partes no guardan la afiliacion: salen todas las de los dos usuarios, sin importar con cual se crearon. */
    @Override
    public Flux<ShareWithAffiliate> list(UUID userId, UUID affiliationId) {
        return Mono.defer(() -> afiliaciones.findForParticipant(affiliationId, userId))
                .switchIfEmpty(Mono.error(ListSharesWithAffiliateUseCase::noEncontrada))
                .flatMapMany(relacion -> CON_PARTES.contains(relacion.status())
                        ? saldos.findSharesBetween(userId, otraPersona(relacion, userId))
                        : Flux.error(estadoInvalido()));
    }

    private static UUID otraPersona(Affiliation relacion, UUID userId) {
        return relacion.sentBy(userId) ? relacion.addresseeId() : relacion.requesterId();
    }

    private static BadRequestException noEncontrada() {
        return new BadRequestException(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND,
                "La afiliacion no existe", "id");
    }

    private static BadRequestException estadoInvalido() {
        return new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.INVALID_STATE,
                "Solo hay partes con una afiliacion aceptada o desvinculada", "status");
    }
}
