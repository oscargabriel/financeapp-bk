package com.oscargabriel.financeapp.application.usecase;

import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.model.ChangePasswordCommand;
import com.oscargabriel.financeapp.domain.port.in.ChangePasswordPort;
import com.oscargabriel.financeapp.domain.port.out.PasswordHasherPort;
import com.oscargabriel.financeapp.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class ChangePasswordUseCase implements ChangePasswordPort {

    private final UserRepositoryPort usuarios;
    private final PasswordHasherPort hasher;

    /** Las reglas de la nueva ya vienen validadas por ChangePasswordRequest. */
    @Override
    public Mono<Void> change(UUID userId, ChangePasswordCommand command) {
        return Mono.defer(() -> usuarios.findActivePasswordHash(userId))
                .switchIfEmpty(Mono.error(ErroresDelPerfil::usuarioNoActivo))
                .filter(hash -> hasher.matches(command.currentPassword(), hash))
                .switchIfEmpty(Mono.error(ErroresDelPerfil::claveActualEquivocada))
                .flatMap(hash -> usuarios.updatePassword(userId, hasher.hash(command.newPassword())));
    }
}
