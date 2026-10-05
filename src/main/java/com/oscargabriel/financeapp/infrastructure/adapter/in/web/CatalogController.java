package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import java.util.Arrays;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.AccountType;
import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.port.in.ListCategoriesPort;
import com.oscargabriel.financeapp.domain.port.in.ListCurrenciesPort;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.CatalogCategoryResponse;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.CatalogItemResponse;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.CurrencyResponse;

import reactor.core.publisher.Flux;

/**
 * Los valores validos para armar formularios (FA-59). Los tipos salen del enum sin puerto: no hay
 * persistencia ni regla que orquestar. Las categorias pasan por el mismo caso de uso que
 * GET /categories, asi que filtro y orden coinciden por construccion.
 */
@RestController
@RequestMapping("/catalogs")
@AllArgsConstructor
public class CatalogController {

    private final ListCurrenciesPort listCurrencies;
    private final ListCategoriesPort listCategories;

    @GetMapping("/account-types")
    public Flux<CatalogItemResponse> accountTypes() {
        return Flux.fromArray(AccountType.values()).map(CatalogItemResponse::from);
    }

    @GetMapping("/transaction-types")
    public Flux<CatalogItemResponse> transactionTypes() {
        return Flux.fromArray(TransactionType.values()).map(CatalogItemResponse::from);
    }

    @GetMapping("/currencies")
    public Flux<CurrencyResponse> currencies() {
        return listCurrencies.listActive().map(CurrencyResponse::from);
    }

    @GetMapping("/categories")
    public Flux<CatalogCategoryResponse> categories(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String appliesTo) {
        return Flux.defer(() -> listCategories.list(UsuarioDelToken.de(jwt), parseScope(appliesTo)))
                .map(CatalogCategoryResponse::from);
    }

    /**
     * Copia del parseo de CategoryController, que FA-59 no podia tocar: si cambia uno, cambia el otro.
     * Exacto y en mayusculas, como la columna.
     */
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
