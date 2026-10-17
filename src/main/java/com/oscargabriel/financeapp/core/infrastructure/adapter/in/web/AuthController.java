package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.oscargabriel.financeapp.core.domain.port.in.AuthPort;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.GoogleLoginRequest;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.GoogleRegisterRequest;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.LoginRequest;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.RefreshTokenRequest;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.RegisterUserRequest;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.RegisterUserResponse;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.TokenResponse;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/auth")
@AllArgsConstructor
public class AuthController {

    private final AuthPort autenticacion;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RegisterUserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        return autenticacion.register(request.toCommand())
                .map(RegisterUserResponse::from);
    }

    @PostMapping("/login")
    public Mono<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return autenticacion.login(request.toCommand())
                .map(TokenResponse::from);
    }

    /** 201 como el alta, porque crea el usuario, pero con la sesion del login (FIN-52). */
    @PostMapping("/google/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<TokenResponse> googleRegister(@Valid @RequestBody GoogleRegisterRequest request) {
        return autenticacion.googleRegister(request.toCommand())
                .map(TokenResponse::from);
    }

    @PostMapping("/google/login")
    public Mono<TokenResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return autenticacion.googleLogin(request.idToken())
                .map(TokenResponse::from);
    }

    @PostMapping("/refresh")
    public Mono<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return autenticacion.refresh(request.refreshToken())
                .map(TokenResponse::from);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        return autenticacion.logout(request.refreshToken());
    }
}
