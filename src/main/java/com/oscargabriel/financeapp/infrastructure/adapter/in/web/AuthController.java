package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.oscargabriel.financeapp.domain.port.in.RegisterUserPort;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.RegisterUserRequest;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.RegisterUserResponse;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final RegisterUserPort registerUser;

    public AuthController(RegisterUserPort registerUser) {
        this.registerUser = registerUser;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RegisterUserResponse> register(@RequestBody RegisterUserRequest request) {
        return registerUser.register(request.toCommand())
                .map(RegisterUserResponse::from);
    }
}
