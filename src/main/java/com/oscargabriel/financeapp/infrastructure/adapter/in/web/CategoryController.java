package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import java.util.Arrays;
import java.util.UUID;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.port.in.CreateCategoryPort;
import com.oscargabriel.financeapp.domain.port.in.ListCategoriesPort;
import com.oscargabriel.financeapp.domain.port.in.UpdateCategoryPort;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.CategoryResponse;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.CreateCategoryRequest;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.UpdateCategoryRequest;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/categories")
@AllArgsConstructor
public class CategoryController {

    private final ListCategoriesPort listCategories;
    private final CreateCategoryPort createCategory;
    private final UpdateCategoryPort updateCategory;

    @GetMapping
    public Flux<CategoryResponse> categories(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String appliesTo) {
        return Flux.defer(() -> listCategories.list(UsuarioDelToken.de(jwt), parseScope(appliesTo)))
                .map(CategoryResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<CategoryResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateCategoryRequest request) {
        return Mono.defer(() -> createCategory.create(UsuarioDelToken.de(jwt), request.toCommand()))
                .map(CategoryResponse::from);
    }

    @PatchMapping("/{id}")
    public Mono<CategoryResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @Valid @RequestBody UpdateCategoryRequest parche) {
        return Mono.defer(() -> {
                    UUID categoria = parseId(id);
                    if (parche.sinCambios()) {
                        throw new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                                "El parche no trae ningun campo para modificar", "body");
                    }
                    return updateCategory.update(UsuarioDelToken.de(jwt), categoria, parche.toCommand());
                })
                .map(CategoryResponse::from);
    }

    /** A mano y no como UUID de Spring: su conversion fallida saldria como JSON_PARSING_ERROR del cuerpo. */
    private static UUID parseId(String valor) {
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                    "El id de la categoria debe ser un UUID", "id", e);
        }
    }

    /** Exacto y en mayusculas, como la columna: el contrato no promete aceptar otra grafia. */
    private static CategoryScope parseScope(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return Arrays.stream(CategoryScope.values())
                .filter(scope -> scope.name().equals(valor))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                        "appliesTo debe ser EXPENSE, INCOME o BOTH", "appliesTo"));
    }
}
