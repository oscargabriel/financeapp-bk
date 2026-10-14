package com.oscargabriel.financeapp.core.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.core.domain.model.Affiliation;
import com.oscargabriel.financeapp.core.domain.model.AffiliationStatus;
import com.oscargabriel.financeapp.core.domain.model.AffiliationView;
import com.oscargabriel.financeapp.core.domain.model.UserCredentials;
import com.oscargabriel.financeapp.core.domain.model.UuidV7;
import com.oscargabriel.financeapp.core.domain.port.in.SendAffiliationRequestPort;
import com.oscargabriel.financeapp.core.domain.port.out.AffiliationRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class SendAffiliationRequestUseCase implements SendAffiliationRequestPort {

    private final AffiliationRepositoryPort afiliaciones;
    private final UserRepositoryPort usuarios;
    private final Clock clock;

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

    private static BadRequestException duplicada() {
        return new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_RESOURCE,
                "Ya tienes una solicitud abierta o una afiliacion con esa persona", "email");
    }
}
