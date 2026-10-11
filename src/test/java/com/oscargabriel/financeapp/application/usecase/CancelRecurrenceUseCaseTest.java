package com.oscargabriel.financeapp.application.usecase;

import static com.oscargabriel.financeapp.support.RecurrenceMother.SERIE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.port.out.RecurrenceRepositoryPort;
import com.oscargabriel.financeapp.support.TransactionMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class CancelRecurrenceUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-10-09T15:00:00Z");

    private static final UUID USER_ID = TransactionMother.USER_ID;

    @Mock
    private RecurrenceRepositoryPort series;

    private CancelRecurrenceUseCase casoDeUso;

    @BeforeEach
    void setUp() {
        casoDeUso = new CancelRecurrenceUseCase(series, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void cancelaConElInstanteDelReloj() {
        when(series.cancel(SERIE_ID, USER_ID, AHORA)).thenReturn(Mono.just(true));

        StepVerifier.create(casoDeUso.cancel(USER_ID, SERIE_ID)).verifyComplete();

        verify(series).cancel(SERIE_ID, USER_ID, AHORA);
    }

    @Test
    void sinUnaSerieActivaQueCancelarEs404SobreId() {
        when(series.cancel(SERIE_ID, USER_ID, AHORA)).thenReturn(Mono.just(false));

        StepVerifier.create(casoDeUso.cancel(USER_ID, SERIE_ID))
                .verifyErrorSatisfies(e -> {
                    BadRequestException error = (BadRequestException) e;
                    assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(error.getErrorResponse().getErrors()).extracting(ErrorDetail::getField)
                            .containsExactly("id");
                });
    }
}
