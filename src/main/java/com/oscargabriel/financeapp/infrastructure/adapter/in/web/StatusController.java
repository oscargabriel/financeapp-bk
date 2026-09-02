package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.oscargabriel.financeapp.domain.model.HealthStatus;
import com.oscargabriel.financeapp.domain.model.SystemStatus;
import com.oscargabriel.financeapp.domain.port.in.CheckSystemStatusPort;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.StatusResponse;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/status")
public class StatusController {

    private final CheckSystemStatusPort checkSystemStatus;

    public StatusController(CheckSystemStatusPort checkSystemStatus) {
        this.checkSystemStatus = checkSystemStatus;
    }

    @GetMapping
    public Mono<ResponseEntity<StatusResponse>> status() {
        return checkSystemStatus.check().map(StatusController::toResponseEntity);
    }

    private static ResponseEntity<StatusResponse> toResponseEntity(SystemStatus systemStatus) {
        HttpStatus httpStatus = systemStatus.status() == HealthStatus.UP
                ? HttpStatus.OK
                : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(httpStatus).body(StatusResponse.from(systemStatus));
    }
}
