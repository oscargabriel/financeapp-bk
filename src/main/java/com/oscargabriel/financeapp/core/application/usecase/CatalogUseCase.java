package com.oscargabriel.financeapp.core.application.usecase;

import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.core.domain.model.Category;
import com.oscargabriel.financeapp.core.domain.model.CategoryScope;
import com.oscargabriel.financeapp.core.domain.model.Currency;
import com.oscargabriel.financeapp.core.domain.port.in.CatalogPort;
import com.oscargabriel.financeapp.core.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.CurrencyQueryPort;

import reactor.core.publisher.Flux;

@Service
@AllArgsConstructor
public class CatalogUseCase implements CatalogPort {

    private final CurrencyQueryPort monedas;
    private final CategoryQueryPort categorias;

    @Override
    public Flux<Currency> listCurrencies() {
        return Flux.defer(monedas::findActive);
    }

    /** La misma consulta y los mismos alcances que CategoryUseCase.list. */
    @Override
    public Flux<Category> listCategories(UUID userId, CategoryScope appliesTo) {
        return Flux.defer(() -> categorias.findActiveByUser(userId, CategoryScope.compatiblesCon(appliesTo)));
    }
}
