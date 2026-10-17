package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.Category;
import com.oscargabriel.financeapp.core.domain.model.CategoryScope;
import com.oscargabriel.financeapp.core.domain.model.Currency;

import reactor.core.publisher.Flux;

public interface CatalogPort {

    /** Las monedas que acepta el alta de cuentas, por codigo. */
    Flux<Currency> listCurrencies();

    /** Con el mismo filtro y orden que GET /categories. appliesTo admite null: sin filtro, todas. */
    Flux<Category> listCategories(UUID userId, CategoryScope appliesTo);
}
