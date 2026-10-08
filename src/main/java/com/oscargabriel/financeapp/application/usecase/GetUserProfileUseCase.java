package com.oscargabriel.financeapp.application.usecase;

import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.model.UserProfile;
import com.oscargabriel.financeapp.domain.port.in.GetUserProfilePort;
import com.oscargabriel.financeapp.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class GetUserProfileUseCase implements GetUserProfilePort {

    private final UserRepositoryPort usuarios;

    @Override
    public Mono<UserProfile> get(UUID userId) {
        return usuarios.findActiveProfile(userId)
                .switchIfEmpty(Mono.error(ErroresDelPerfil::usuarioNoActivo));
    }
}
