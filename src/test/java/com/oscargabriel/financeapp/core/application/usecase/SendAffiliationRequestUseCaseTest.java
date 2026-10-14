package com.oscargabriel.financeapp.core.application.usecase;

import static com.oscargabriel.financeapp.core.support.AffiliationMother.CORREO_DESTINATARIO;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.DESTINATARIO_ID;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.REMITENTE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.Affiliation;
import com.oscargabriel.financeapp.core.domain.model.AffiliationDirection;
import com.oscargabriel.financeapp.core.domain.model.AffiliationStatus;
import com.oscargabriel.financeapp.core.domain.model.AffiliationView;
import com.oscargabriel.financeapp.core.domain.model.UserCredentials;
import com.oscargabriel.financeapp.core.domain.port.out.AffiliationRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SendAffiliationRequestUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-10-13T15:00:00Z");

    @Mock
    private AffiliationRepositoryPort afiliaciones;

    @Mock
    private UserRepositoryPort usuarios;

    private SendAffiliationRequestUseCase casoDeUso;

    @BeforeEach
    void setUp() {
        casoDeUso = new SendAffiliationRequestUseCase(afiliaciones, usuarios, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void aUnCorreoConCuentaGuardaElDestinatarioYDevuelveLaEnviadaSinNombre() {
        conCuenta(DESTINATARIO_ID);
        when(afiliaciones.existsOpenVisible(REMITENTE_ID, CORREO_DESTINATARIO, DESTINATARIO_ID)).thenReturn(Mono.just(false));
        when(afiliaciones.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(casoDeUso.send(REMITENTE_ID, CORREO_DESTINATARIO))
                .assertNext(vista -> esLaEnviadaSinNombre(vista))
                .verifyComplete();

        Affiliation guardada = guardada();
        assertThat(guardada.requesterId()).isEqualTo(REMITENTE_ID);
        assertThat(guardada.addresseeId()).isEqualTo(DESTINATARIO_ID);
        assertThat(guardada.addresseeEmail()).isEqualTo(CORREO_DESTINATARIO);
        assertThat(guardada.status()).isEqualTo(AffiliationStatus.REQUESTED);
        assertThat(guardada.createdAt()).isEqualTo(AHORA);
    }

    @Test
    void aUnCorreoSinCuentaRespondeIgualYGuardaSinDestinatario() {
        when(usuarios.findActiveByEmail(CORREO_DESTINATARIO)).thenReturn(Mono.empty());
        when(afiliaciones.existsOpenVisible(REMITENTE_ID, CORREO_DESTINATARIO, null)).thenReturn(Mono.just(false));
        when(afiliaciones.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(casoDeUso.send(REMITENTE_ID, CORREO_DESTINATARIO))
                .assertNext(vista -> esLaEnviadaSinNombre(vista))
                .verifyComplete();

        assertThat(guardada().addresseeId()).isNull();
    }

    @Test
    void normalizaElCorreoComoElRegistro() {
        conCuenta(DESTINATARIO_ID);
        when(afiliaciones.existsOpenVisible(REMITENTE_ID, CORREO_DESTINATARIO, DESTINATARIO_ID)).thenReturn(Mono.just(false));
        when(afiliaciones.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(casoDeUso.send(REMITENTE_ID, "  Beto@Ejemplo.COM "))
                .assertNext(vista -> assertThat(vista.email()).isEqualTo(CORREO_DESTINATARIO))
                .verifyComplete();
    }

    @Test
    void aSiMismoEs400SobreEmailYNoGuardaNada() {
        conCuenta(REMITENTE_ID);

        StepVerifier.create(casoDeUso.send(REMITENTE_ID, CORREO_DESTINATARIO))
                .verifyErrorSatisfies(e -> esError(e, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "email"));

        verify(afiliaciones, never()).save(any());
    }

    @Test
    void conUnaRelacionAbiertaQueElUsuarioVeEs409SobreEmailYNoGuardaNada() {
        conCuenta(DESTINATARIO_ID);
        when(afiliaciones.existsOpenVisible(REMITENTE_ID, CORREO_DESTINATARIO, DESTINATARIO_ID)).thenReturn(Mono.just(true));

        StepVerifier.create(casoDeUso.send(REMITENTE_ID, CORREO_DESTINATARIO))
                .verifyErrorSatisfies(e -> esError(e, HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", "email"));

        verify(afiliaciones, never()).save(any());
    }

    private void conCuenta(UUID id) {
        when(usuarios.findActiveByEmail(CORREO_DESTINATARIO)).thenReturn(Mono.just(new UserCredentials(id, "hash", 0, null)));
    }

    private Affiliation guardada() {
        ArgumentCaptor<Affiliation> captor = ArgumentCaptor.forClass(Affiliation.class);
        verify(afiliaciones).save(captor.capture());
        return captor.getValue();
    }

    private static void esLaEnviadaSinNombre(AffiliationView vista) {
        assertThat(vista.id()).isNotNull();
        assertThat(vista.status()).isEqualTo(AffiliationStatus.REQUESTED);
        assertThat(vista.direction()).isEqualTo(AffiliationDirection.SENT);
        assertThat(vista.email()).isEqualTo(CORREO_DESTINATARIO);
        assertThat(vista.firstName()).isNull();
        assertThat(vista.lastName()).isNull();
        assertThat(vista.createdAt()).isEqualTo(AHORA);
        assertThat(vista.updatedAt()).isEqualTo(AHORA);
    }

    static void esError(Throwable e, HttpStatus status, String codigo, String campo) {
        BadRequestException error = (BadRequestException) e;
        assertThat(error.getHttpStatus()).isEqualTo(status);
        assertThat(error.getErrorResponse().getErrors()).extracting(ErrorDetail::getCode, ErrorDetail::getField)
                .containsExactly(tuple(codigo, campo));
    }
}
