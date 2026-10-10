package com.oscargabriel.financeapp.application.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.util.stream.Stream;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.Currency;
import com.oscargabriel.financeapp.domain.model.ExchangeRate;
import com.oscargabriel.financeapp.domain.model.UsdRate;
import com.oscargabriel.financeapp.domain.port.in.ResolveExchangeRatePort;
import com.oscargabriel.financeapp.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.domain.port.out.ExchangeRateProviderPort;
import com.oscargabriel.financeapp.domain.port.out.ExchangeRateRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * La tasa de un par en una fecha, contra USD (design.md de FA-120). El proveedor solo se consulta
 * para hoy o despues, cuando a una pata le falta la fila de hoy; su fallo lo deja en el log el
 * adapter, y aqui se sigue con lo guardado.
 */
@Service
@AllArgsConstructor
public class ResolveExchangeRateUseCase implements ResolveExchangeRatePort {

    private final ExchangeRateRepositoryPort tasas;
    private final ExchangeRateProviderPort proveedor;
    private final CurrencyQueryPort monedas;
    private final Clock clock;

    @Override
    public Mono<ExchangeRate> resolve(String from, String to, LocalDate date) {
        return activa(from, "from")
                .then(activa(to, "to"))
                .then(Mono.defer(() -> from.equals(to)
                        ? Mono.just(ExchangeRate.mismoPar(from, date))
                        : cruzar(from, to, date)));
    }

    private Mono<Void> activa(String moneda, String campo) {
        return monedas.exists(moneda)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(() -> new BadRequestException(HttpStatus.BAD_REQUEST,
                        ErrorCodes.VALIDATION_ERROR, campo + " no es una moneda activa del catalogo", campo)))
                .then();
    }

    private Mono<ExchangeRate> cruzar(String from, String to, LocalDate date) {
        LocalDate hoy = LocalDate.now(clock);
        Mono<Void> alDia = date.isBefore(hoy) ? Mono.empty() : alDia(hoy, from, to);
        return alDia
                .then(Mono.defer(() -> Mono.zip(pata(from, date), pata(to, date))))
                .map(patas -> ExchangeRate.cruzada(date, patas.getT1(), patas.getT2()))
                .switchIfEmpty(Mono.error(() -> new BadRequestException(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND,
                        "No hay tasa de cambio de " + from + " a " + to + " para esa fecha", "date")));
    }

    private Mono<UsdRate> pata(String moneda, LocalDate date) {
        return UsdRate.USD.equals(moneda) ? Mono.just(UsdRate.usd()) : tasas.findLatestFromUsd(moneda, date);
    }

    /** Consulta al proveedor una vez si a alguna pata distinta de USD le falta la fila de hoy. */
    private Mono<Void> alDia(LocalDate hoy, String from, String to) {
        return Flux.fromStream(Stream.of(from, to).filter(m -> !UsdRate.USD.equals(m)))
                .concatMap(moneda -> tasas.findLatestFromUsd(moneda, hoy)
                        .filter(fila -> hoy.equals(fila.rateDate()))
                        .hasElement())
                .any(deHoy -> !deHoy)
                .flatMap(falta -> falta ? guardarDelProveedor(hoy) : Mono.empty());
    }

    /** Solo las monedas activas distintas de USD: la FK no admite otras, y USD→USD no es una fila. */
    private Mono<Void> guardarDelProveedor(LocalDate hoy) {
        return proveedor.latestFromUsd()
                .onErrorResume(e -> Mono.empty())
                .flatMap(respuesta -> monedas.findActive()
                        .map(Currency::code)
                        .filter(codigo -> !UsdRate.USD.equals(codigo) && respuesta.containsKey(codigo))
                        .collectMap(codigo -> codigo, respuesta::get))
                .filter(activas -> !activas.isEmpty())
                .flatMap(activas -> tasas.saveFromUsd(hoy, activas));
    }
}
