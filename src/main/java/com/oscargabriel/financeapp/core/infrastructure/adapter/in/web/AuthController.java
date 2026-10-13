package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.oscargabriel.financeapp.core.domain.port.in.LoginPort;
import com.oscargabriel.financeapp.core.domain.port.in.LogoutPort;
import com.oscargabriel.financeapp.core.domain.port.in.RefreshSessionPort;
import com.oscargabriel.financeapp.core.domain.port.in.RegisterUserPort;
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

    private final RegisterUserPort registerUser;
    private final LoginPort login;
    private final RefreshSessionPort refreshSession;
    private final LogoutPort logout;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RegisterUserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        return registerUser.register(request.toCommand())
                .map(RegisterUserResponse::from);
    }

    @PostMapping("/login")
    public Mono<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return login.login(request.toCommand())
                .map(TokenResponse::from);
    }

    @PostMapping("/refresh")
    public Mono<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return refreshSession.refresh(request.refreshToken())
                .map(TokenResponse::from);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        return logout.logout(request.refreshToken());
    }
}
