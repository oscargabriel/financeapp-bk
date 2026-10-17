package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.compartido.infrastructure.adapter.in.web.UsuarioDelToken;
import com.oscargabriel.financeapp.core.domain.model.ChangePasswordCommand;
import com.oscargabriel.financeapp.core.domain.port.in.UserPort;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.ChangePasswordRequest;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.FirstPasswordRequest;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.UpdateUserProfileRequest;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.UserProfileResponse;

import reactor.core.publisher.Mono;

/** "me" y no un id en la ruta: el usuario sale siempre del token. */
@RestController
@RequestMapping("/users/me")
@AllArgsConstructor
public class UserController {

    private final UserPort usuarios;

    @GetMapping
    public Mono<UserProfileResponse> profile(@AuthenticationPrincipal Jwt jwt) {
        return Mono.defer(() -> usuarios.get(UsuarioDelToken.de(jwt)))
                .map(UserProfileResponse::from);
    }

    @PatchMapping
    public Mono<UserProfileResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateUserProfileRequest parche) {
        return Mono.defer(() -> {
                    if (parche.sinCambios()) {
                        throw new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                                "El parche no trae ningun campo para modificar", "body");
                    }
                    return usuarios.update(UsuarioDelToken.de(jwt), parche.toCommand());
                })
                .map(UserProfileResponse::from);
    }

    /** PUT porque reemplaza el valor entero, y no es parte del recurso que devuelve el GET. */
    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request) {
        return Mono.defer(() -> usuarios.changePassword(UsuarioDelToken.de(jwt),
                new ChangePasswordCommand(request.currentPassword(), request.newPassword())));
    }

    /** POST porque crea el valor que el PUT reemplaza: solo para quien no tiene contrasena (FIN-53). */
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> setFirstPassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody FirstPasswordRequest request) {
        return Mono.defer(() -> usuarios.setFirstPassword(UsuarioDelToken.de(jwt), request.toCommand()));
    }
}
