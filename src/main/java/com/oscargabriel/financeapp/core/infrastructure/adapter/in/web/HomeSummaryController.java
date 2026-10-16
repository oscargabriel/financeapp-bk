package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web;

import lombok.AllArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.oscargabriel.financeapp.compartido.infrastructure.adapter.in.web.UsuarioDelToken;
import com.oscargabriel.financeapp.core.domain.port.in.GetHomeSummaryPort;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.HomeSummaryResponse;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/reports")
@AllArgsConstructor
public class HomeSummaryController {

    private final GetHomeSummaryPort getHomeSummary;

    /** Sin parametros: la pantalla de Inicio siempre muestra el mes en curso, y uno desconocido se ignora. */
    @GetMapping("/home")
    public Mono<HomeSummaryResponse> home(@AuthenticationPrincipal Jwt jwt) {
        return Mono.defer(() -> getHomeSummary.get(UsuarioDelToken.de(jwt))).map(HomeSummaryResponse::from);
    }
}
