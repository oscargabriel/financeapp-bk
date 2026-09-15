package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.oscargabriel.financeapp.domain.port.in.LoginPort;
import com.oscargabriel.financeapp.domain.port.in.RegisterUserPort;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.LoginRequest;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.LoginResponse;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.RegisterUserRequest;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.RegisterUserResponse;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final RegisterUserPort registerUser;
    private final LoginPort login;

    public AuthController(RegisterUserPort registerUser, LoginPort login) {
        this.registerUser = registerUser;
        this.login = login;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RegisterUserResponse> register(@RequestBody RegisterUserRequest request) {
        return registerUser.register(request.toCommand())
                .map(RegisterUserResponse::from);
    }

    @PostMapping("/login")
    public Mono<LoginResponse> login(@RequestBody LoginRequest request) {
        return login.login(request.toCommand())
                .map(LoginResponse::from);
    }
}
