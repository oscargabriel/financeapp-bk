package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import java.util.Arrays;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.port.in.ListCategoriesPort;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.CategoryResponse;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final ListCategoriesPort listCategories;

    public CategoryController(ListCategoriesPort listCategories) {
        this.listCategories = listCategories;
    }

    @GetMapping
    public Flux<CategoryResponse> categories(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String appliesTo) {
        return Flux.defer(() -> listCategories.list(UsuarioDelToken.de(jwt), parseScope(appliesTo)))
                .map(CategoryResponse::from);
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
