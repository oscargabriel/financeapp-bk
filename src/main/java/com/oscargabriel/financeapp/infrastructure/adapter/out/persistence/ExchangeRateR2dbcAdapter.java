package com.oscargabriel.financeapp.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import lombok.AllArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.domain.model.UsdRate;
import com.oscargabriel.financeapp.domain.port.out.ExchangeRateRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@AllArgsConstructor
public class ExchangeRateR2dbcAdapter implements ExchangeRateRepositoryPort {

    /**
     * La regla vive en finance.usd_rate (FA-122): la mas reciente <= la fecha o, si no hay, la mas antigua.
     * La misma funcion congela la tasa de cada movimiento, asi que este endpoint no puede decir otra cosa.
     */
    private static final String MAS_RECIENTE = """
            SELECT rate, rate_date
              FROM finance.usd_rate(:currency, :date)
            """;

    /**
     * DO NOTHING y no DO UPDATE: una fila MANUAL del dia no se pisa, y dos requests que consultaron al
     * proveedor a la vez no chocan por ux_exchange_rates_pair_date (design.md de FA-120).
     */
    private static final String INSERTAR = """
            INSERT INTO finance.exchange_rates (from_currency_code, to_currency_code, rate, rate_date, source)
            VALUES ('USD', :currency, :rate, :date, 'API')
            ON CONFLICT (from_currency_code, to_currency_code, rate_date) DO NOTHING
            """;

    private final DatabaseClient databaseClient;

    @Override
    public Mono<UsdRate> findLatestFromUsd(String currency, LocalDate date) {
        return databaseClient.sql(MAS_RECIENTE)
                .bind("currency", currency)
                .bind("date", date)
                .map((row, metadata) -> new UsdRate(currency, row.get("rate", BigDecimal.class),
                        row.get("rate_date", LocalDate.class)))
                .one();
    }

    @Override
    public Mono<Void> saveFromUsd(LocalDate date, Map<String, BigDecimal> rates) {
        return Flux.fromIterable(rates.entrySet())
                .concatMap(tasa -> databaseClient.sql(INSERTAR)
                        .bind("currency", tasa.getKey())
                        .bind("rate", tasa.getValue())
                        .bind("date", date)
                        .fetch().rowsUpdated())
                .then();
    }
}
