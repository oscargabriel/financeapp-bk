package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import java.util.UUID;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.port.in.CancelInstallmentPurchasePort;
import com.oscargabriel.financeapp.domain.port.in.CreateInstallmentPurchasePort;
import com.oscargabriel.financeapp.domain.port.in.ListInstallmentPurchasesPort;
import com.oscargabriel.financeapp.domain.port.in.PreviewInstallmentPurchasePort;
import com.oscargabriel.financeapp.domain.port.in.UpdateInstallmentPurchasePort;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.CreateInstallmentPurchaseRequest;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.InstallmentPreviewResponse;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.InstallmentPurchaseResponse;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.UpdateInstallmentPurchaseRequest;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Compras con tarjeta de credito diferidas a cuotas (FA-108). */
@RestController
@RequestMapping("/installment-purchases")
@AllArgsConstructor
public class InstallmentPurchaseController {

    private final PreviewInstallmentPurchasePort previewPurchase;
    private final CreateInstallmentPurchasePort createPurchase;
    private final ListInstallmentPurchasesPort listPurchases;
    private final UpdateInstallmentPurchasePort updatePurchase;
    private final CancelInstallmentPurchasePort cancelPurchase;

    /** 200 y no 201: no crea nada. */
    @PostMapping("/preview")
    public Mono<InstallmentPreviewResponse> preview(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateInstallmentPurchaseRequest compra) {
        return Mono.defer(() -> previewPurchase.preview(UsuarioDelToken.de(jwt), compra.toCommand()))
                .map(InstallmentPreviewResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<InstallmentPurchaseResponse> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateInstallmentPurchaseRequest compra) {
        return Mono.defer(() -> createPurchase.create(UsuarioDelToken.de(jwt), compra.toCommand()))
                .map(InstallmentPurchaseResponse::from);
    }

    @GetMapping
    public Flux<InstallmentPurchaseResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return Flux.defer(() -> listPurchases.list(UsuarioDelToken.de(jwt)))
                .map(InstallmentPurchaseResponse::from);
    }

    @PatchMapping("/{id}")
    public Mono<InstallmentPurchaseResponse> update(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @Valid @RequestBody UpdateInstallmentPurchaseRequest parche) {
        return Mono.defer(() -> {
                    UUID compra = parseId(id);
                    if (parche.sinCambios()) {
                        throw new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                                "El parche no trae ningun campo para modificar, ademas del alcance", "body");
                    }
                    return updatePurchase.update(UsuarioDelToken.de(jwt), compra, parche.toCommand());
                })
                .map(InstallmentPurchaseResponse::from);
    }

    /** Cancelar siempre conserva las cuotas pasadas: no lleva alcance. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return Mono.defer(() -> cancelPurchase.cancel(UsuarioDelToken.de(jwt), parseId(id)));
    }

    /** A mano y no como UUID de Spring: su conversion fallida saldria como JSON_PARSING_ERROR del cuerpo. */
    private static UUID parseId(String valor) {
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                    "El id de la compra debe ser un UUID", "id", e);
        }
    }
}