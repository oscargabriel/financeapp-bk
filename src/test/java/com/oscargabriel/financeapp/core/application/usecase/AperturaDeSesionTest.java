package com.oscargabriel.financeapp.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.oscargabriel.financeapp.core.domain.model.AccessToken;
import com.oscargabriel.financeapp.core.domain.model.NewRefreshToken;
import com.oscargabriel.financeapp.core.domain.model.RefreshToken;
import com.oscargabriel.financeapp.core.domain.model.SessionTokens;
import com.oscargabriel.financeapp.core.domain.port.out.RefreshTokenGeneratorPort;
import com.oscargabriel.financeapp.core.domain.port.out.SesionRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.TokenIssuerPort;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AperturaDeSesionTest {

    private static final UUID ID = UUID.fromString("01994f00-0000-7000-8000-000000000001");
    private static final long VERSION = 2L;
    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");
    private static final Duration DURACION_DE_LA_SESION = Duration.ofHours(24);
    private static final AccessToken TOKEN = new AccessToken("un.jwt.firmado", Duration.ofHours(1));
    private static final String REFRESH = "refresh-en-claro";
    private static final String HASH_DEL_REFRESH = "hash-del-refresh";

    @Mock
    private TokenIssuerPort emisor;

    @Mock
    private RefreshTokenGeneratorPort generador;

    @Mock
    private SesionRepositoryPort sesiones;

    @BeforeEach
    void emisionPosible() {
        when(emisor.issueFor(ID, VERSION)).thenReturn(TOKEN);
        when(generador.generar()).thenReturn(REFRESH);
        when(generador.hash(REFRESH)).thenReturn(HASH_DEL_REFRESH);
        when(sesiones.abrir(any())).thenReturn(Mono.empty());
    }

    /** FIN-47: la sesion dura 24 horas y en la base queda solo el hash del refresh. */
    @Test
    void abreUnaSesionNuevaGuardandoSoloElHashDelRefresh() {
        StepVerifier.create(apertura().abrir(ID, VERSION))
                .expectNext(new SessionTokens(TOKEN, new RefreshToken(REFRESH, DURACION_DE_LA_SESION)))
                .verifyComplete();

        ArgumentCaptor<NewRefreshToken> abierta = ArgumentCaptor.forClass(NewRefreshToken.class);
        verify(sesiones).abrir(abierta.capture());
        assertThat(abierta.getValue().userId()).isEqualTo(ID);
        assertThat(abierta.getValue().tokenHash()).isEqualTo(HASH_DEL_REFRESH);
        assertThat(abierta.getValue().tokenVersion()).isEqualTo(VERSION);
        assertThat(abierta.getValue().createdAt()).isEqualTo(AHORA);
        assertThat(abierta.getValue().expiresAt()).isEqualTo(AHORA.plus(DURACION_DE_LA_SESION));
        assertThat(abierta.getValue().id()).isNotNull();
        assertThat(abierta.getValue().familyId()).isNotNull().isNotEqualTo(abierta.getValue().id());
    }

    /** Un refresh que no quedo guardado no serviria: mejor fallar que entregarlo. */
    @Test
    void noEntregaTokensSiLaSesionNoSePudoAbrir() {
        when(sesiones.abrir(any())).thenReturn(Mono.error(new IllegalStateException("base caida")));

        StepVerifier.create(apertura().abrir(ID, VERSION))
                .verifyError(IllegalStateException.class);
    }

    private AperturaDeSesion apertura() {
        return new AperturaDeSesion(emisor, generador, sesiones, Clock.fixed(AHORA, ZoneOffset.UTC),
                DURACION_DE_LA_SESION);
    }
}
