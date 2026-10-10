package com.oscargabriel.financeapp.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.Currency;
import com.oscargabriel.financeapp.domain.model.UsdRate;
import com.oscargabriel.financeapp.domain.port.out.CurrencyQueryPort;
import com.oscargabriel.financeapp.domain.port.out.ExchangeRateProviderPort;
import com.oscargabriel.financeapp.domain.port.out.ExchangeRateRepositoryPort;
import com.oscargabriel.financeapp.support.CurrencyMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ResolveExchangeRateUseCaseTest {

    /** 10:00 en Bogota: hoy es el 10 de octubre en la zona de la app. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-10T15:00:00Z"), ZoneId.of("America/Bogota"));
    private static final LocalDate HOY = LocalDate.of(2026, 10, 10);

    @Mock
    private ExchangeRateRepositoryPort tasas;
    @Mock
    private ExchangeRateProviderPort proveedor;
    @Mock
    private CurrencyQueryPort monedas;

    private ResolveExchangeRateUseCase casoDeUso;

    @BeforeEach
    void setUp() {
        casoDeUso = new ResolveExchangeRateUseCase(tasas, proveedor, monedas, RELOJ);
        lenient().when(monedas.exists(anyString())).thenReturn(Mono.just(true));
    }

    private static UsdRate usdA(String moneda, String tasa, LocalDate fecha) {
        return new UsdRate(moneda, new BigDecimal(tasa), fecha);
    }

    private static Currency moneda(String codigo) {
        return new Currency(codigo, codigo, codigo, 2);
    }

    private static void esError(Throwable error, HttpStatus status, ErrorCodes codigo, String campo) {
        BadRequestException bre = (BadRequestException) error;
        assertThat(bre.getHttpStatus()).isEqualTo(status);
        assertThat(bre.getErrorResponse().getErrors()).singleElement().satisfies(detalle -> {
            assertThat(detalle.getCode()).isEqualTo(codigo.getCode());
            assertThat(detalle.getField()).isEqualTo(campo);
        });
    }

    @Test
    void elMismoParValeUnoSinBuscarTasasNiConsultarAlProveedor() {
        StepVerifier.create(casoDeUso.resolve("COP", "COP", LocalDate.of(2026, 1, 15)))
                .assertNext(tasa -> {
                    assertThat(tasa.rate()).isEqualByComparingTo("1");
                    assertThat(tasa.rateDate()).isEqualTo(LocalDate.of(2026, 1, 15));
                })
                .verifyComplete();

        verifyNoInteractions(tasas, proveedor);
    }

    @Test
    void cruzaLasPatasGuardadasDeHoySinConsultarAlProveedor() {
        when(tasas.findLatestFromUsd("EUR", HOY)).thenReturn(Mono.just(usdA("EUR", "0.8", HOY)));
        when(tasas.findLatestFromUsd("COP", HOY)).thenReturn(Mono.just(usdA("COP", "4100", HOY)));

        StepVerifier.create(casoDeUso.resolve("EUR", "COP", HOY))
                .assertNext(tasa -> {
                    assertThat(tasa.rate()).isEqualByComparingTo("5125");
                    assertThat(tasa.rateDate()).isEqualTo(HOY);
                })
                .verifyComplete();

        verifyNoInteractions(proveedor);
    }

    @Test
    void unaFechaPasadaUsaLaFilaAnteriorSinConsultarAlProveedor() {
        LocalDate hace10 = HOY.minusDays(10);
        when(tasas.findLatestFromUsd("COP", hace10)).thenReturn(Mono.just(usdA("COP", "3900", HOY.minusDays(30))));

        StepVerifier.create(casoDeUso.resolve("USD", "COP", hace10))
                .assertNext(tasa -> {
                    assertThat(tasa.rate()).isEqualByComparingTo("3900");
                    assertThat(tasa.rateDate()).isEqualTo(HOY.minusDays(30));
                    assertThat(tasa.date()).isEqualTo(hace10);
                })
                .verifyComplete();

        verifyNoInteractions(proveedor);
    }

    @Test
    void sinLaPataDeHoyConsultaUnaVezYGuardaSoloLasMonedasActivasDistintasDeUsd() {
        when(tasas.findLatestFromUsd("COP", HOY)).thenReturn(Mono.just(usdA("COP", "4100", HOY)));
        when(tasas.findLatestFromUsd("EUR", HOY))
                .thenReturn(Mono.just(usdA("EUR", "0.79", HOY.minusDays(1))), Mono.just(usdA("EUR", "0.8", HOY)));
        when(proveedor.latestFromUsd()).thenReturn(Mono.just(Map.of(
                "USD", BigDecimal.ONE, "EUR", new BigDecimal("0.8"), "COP", new BigDecimal("4000"),
                "JPY", new BigDecimal("150"))));
        when(monedas.findActive()).thenReturn(Flux.just(CurrencyMother.cop(), CurrencyMother.usd(), moneda("EUR")));
        when(tasas.saveFromUsd(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(casoDeUso.resolve("EUR", "COP", HOY))
                .assertNext(tasa -> assertThat(tasa.rate()).isEqualByComparingTo("5125"))
                .verifyComplete();

        verify(proveedor, times(1)).latestFromUsd();
        verify(tasas).saveFromUsd(HOY, Map.of("EUR", new BigDecimal("0.8"), "COP", new BigDecimal("4000")));
    }

    @Test
    void conTodasLasPatasDeHoyNoConsultaAlProveedor() {
        when(tasas.findLatestFromUsd("COP", HOY)).thenReturn(Mono.just(usdA("COP", "4100", HOY)));

        StepVerifier.create(casoDeUso.resolve("COP", "USD", HOY))
                .assertNext(tasa -> assertThat(tasa.rate()).isEqualTo(new BigDecimal("0.0002439024")))
                .verifyComplete();

        verifyNoInteractions(proveedor);
    }

    @Test
    void unaFechaFuturaConsultaAlProveedorYUsaLaTasaDeHoy() {
        LocalDate manana = HOY.plusDays(1);
        when(tasas.findLatestFromUsd("COP", HOY)).thenReturn(Mono.just(usdA("COP", "4050", HOY.minusDays(1))));
        when(tasas.findLatestFromUsd("COP", manana)).thenReturn(Mono.just(usdA("COP", "4000", HOY)));
        when(proveedor.latestFromUsd()).thenReturn(Mono.just(Map.of("COP", new BigDecimal("4000"))));
        when(monedas.findActive()).thenReturn(Flux.just(CurrencyMother.cop()));
        when(tasas.saveFromUsd(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(casoDeUso.resolve("USD", "COP", manana))
                .assertNext(tasa -> {
                    assertThat(tasa.rate()).isEqualByComparingTo("4000");
                    assertThat(tasa.rateDate()).isEqualTo(HOY);
                })
                .verifyComplete();

        verify(tasas).saveFromUsd(HOY, Map.of("COP", new BigDecimal("4000")));
    }

    @Test
    void conElProveedorCaidoUsaLaTasaAnteriorSinGuardar() {
        when(tasas.findLatestFromUsd("COP", HOY)).thenReturn(Mono.just(usdA("COP", "4050", HOY.minusDays(1))));
        when(proveedor.latestFromUsd()).thenReturn(Mono.error(new TimeoutException()));

        StepVerifier.create(casoDeUso.resolve("USD", "COP", HOY))
                .assertNext(tasa -> {
                    assertThat(tasa.rate()).isEqualByComparingTo("4050");
                    assertThat(tasa.rateDate()).isEqualTo(HOY.minusDays(1));
                })
                .verifyComplete();

        verify(tasas, never()).saveFromUsd(any(), any());
    }

    @Test
    void conElProveedorCaidoYSinNingunaTasaDelParResponde404EnLaFecha() {
        when(tasas.findLatestFromUsd("COP", HOY)).thenReturn(Mono.empty());
        when(proveedor.latestFromUsd()).thenReturn(Mono.error(new IllegalStateException("caido")));

        StepVerifier.create(casoDeUso.resolve("USD", "COP", HOY))
                .expectErrorSatisfies(error -> {
                    esError(error, HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "date");
                    assertThat(((BadRequestException) error).getErrorResponse().getErrors())
                            .extracting(ErrorDetail::getDescription)
                            .noneMatch(descripcion -> descripcion.contains("caido"));
                })
                .verify();
    }

    @Test
    void refrescarHoySinLaFilaDeHoyConsultaUnaVezYGuarda() {
        when(tasas.findLatestFromUsd("COP", HOY)).thenReturn(Mono.just(usdA("COP", "4050", HOY.minusDays(1))));
        when(proveedor.latestFromUsd()).thenReturn(Mono.just(Map.of("COP", new BigDecimal("4100"))));
        when(monedas.findActive()).thenReturn(Flux.just(CurrencyMother.cop()));
        when(tasas.saveFromUsd(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(casoDeUso.refreshToday(Set.of("COP", "USD"))).verifyComplete();

        verify(proveedor, times(1)).latestFromUsd();
        verify(tasas).saveFromUsd(HOY, Map.of("COP", new BigDecimal("4100")));
    }

    @Test
    void refrescarHoyConLaFilaDeHoyNoConsultaAlProveedor() {
        when(tasas.findLatestFromUsd("COP", HOY)).thenReturn(Mono.just(usdA("COP", "4100", HOY)));

        StepVerifier.create(casoDeUso.refreshToday(Set.of("COP"))).verifyComplete();

        verifyNoInteractions(proveedor);
    }

    @Test
    void refrescarHoySoloConDolaresNoBuscaNada() {
        StepVerifier.create(casoDeUso.refreshToday(Set.of("USD"))).verifyComplete();

        verifyNoInteractions(tasas, proveedor);
    }

    @Test
    void refrescarHoyConElProveedorCaidoCompletaSinGuardar() {
        when(tasas.findLatestFromUsd("COP", HOY)).thenReturn(Mono.empty());
        when(proveedor.latestFromUsd()).thenReturn(Mono.error(new TimeoutException()));

        StepVerifier.create(casoDeUso.refreshToday(Set.of("COP"))).verifyComplete();

        verify(tasas, never()).saveFromUsd(any(), any());
    }

    /** El repositorio da la mas antigua cuando no hay anterior (FA-122): la fecha de la tasa queda despues. */
    @Test
    void unaFechaAnteriorATodasLasTasasUsaLaMasAntiguaSinConsultarAlProveedor() {
        LocalDate antigua = LocalDate.of(2000, 1, 1);
        when(tasas.findLatestFromUsd("COP", antigua)).thenReturn(Mono.just(usdA("COP", "3900", HOY.minusDays(45))));

        StepVerifier.create(casoDeUso.resolve("USD", "COP", antigua))
                .assertNext(tasa -> {
                    assertThat(tasa.rate()).isEqualByComparingTo("3900");
                    assertThat(tasa.date()).isEqualTo(antigua);
                    assertThat(tasa.rateDate()).isEqualTo(HOY.minusDays(45));
                })
                .verifyComplete();

        verifyNoInteractions(proveedor);
    }

    @Test
    void unParSinNingunaTasaResponde404SinConsultarAlProveedorEnUnaFechaPasada() {
        LocalDate antigua = LocalDate.of(2000, 1, 1);
        when(tasas.findLatestFromUsd("COP", antigua)).thenReturn(Mono.empty());

        StepVerifier.create(casoDeUso.resolve("USD", "COP", antigua))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "date"))
                .verify();

        verifyNoInteractions(proveedor);
    }

    @Test
    void rechazaElOrigenQueNoEstaActivoEnElCatalogo() {
        when(monedas.exists("XTS")).thenReturn(Mono.just(false));

        StepVerifier.create(casoDeUso.resolve("XTS", "COP", HOY))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR, "from"))
                .verify();

        verifyNoInteractions(tasas, proveedor);
    }

    @Test
    void rechazaElDestinoQueNoEstaActivoEnElCatalogo() {
        when(monedas.exists("XTS")).thenReturn(Mono.just(false));

        StepVerifier.create(casoDeUso.resolve("USD", "XTS", HOY))
                .expectErrorSatisfies(error -> esError(error, HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR, "to"))
                .verify();

        verifyNoInteractions(tasas, proveedor);
    }
}
