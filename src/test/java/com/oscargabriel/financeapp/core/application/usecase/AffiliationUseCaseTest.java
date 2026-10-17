package com.oscargabriel.financeapp.core.application.usecase;

import static com.oscargabriel.financeapp.core.support.AffiliateBalanceMother.unSaldo;
import static com.oscargabriel.financeapp.core.support.AffiliateBalanceMother.unaParte;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.AFILIACION_ID;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.AJENO_ID;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.CORREO_DESTINATARIO;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.DESTINATARIO_ID;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.REMITENTE_ID;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.unaSolicitud;
import static com.oscargabriel.financeapp.core.support.AffiliationMother.unaVista;
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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.core.domain.model.AffiliateBalance;
import com.oscargabriel.financeapp.core.domain.model.Affiliation;
import com.oscargabriel.financeapp.core.domain.model.AffiliationDirection;
import com.oscargabriel.financeapp.core.domain.model.AffiliationStatus;
import com.oscargabriel.financeapp.core.domain.model.AffiliationView;
import com.oscargabriel.financeapp.core.domain.model.ShareWithAffiliate;
import com.oscargabriel.financeapp.core.domain.model.UserCredentials;
import com.oscargabriel.financeapp.core.domain.port.out.AffiliateBalanceQueryPort;
import com.oscargabriel.financeapp.core.domain.port.out.AffiliationRepositoryPort;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;
import com.oscargabriel.financeapp.core.support.UserMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AffiliationUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-10-13T15:00:00Z");

    @Mock
    private AffiliationRepositoryPort afiliaciones;

    @Mock
    private UserRepositoryPort usuarios;

    @Mock
    private AffiliateBalanceQueryPort saldos;

    @Nested
    class Enviar {

        @Test
        void aUnCorreoConCuentaGuardaElDestinatarioYDevuelveLaEnviadaSinNombre() {
            conCuenta(DESTINATARIO_ID);
            when(afiliaciones.existsOpenVisible(REMITENTE_ID, CORREO_DESTINATARIO, DESTINATARIO_ID)).thenReturn(Mono.just(false));
            when(afiliaciones.save(any())).thenReturn(Mono.empty());

            StepVerifier.create(casoDeUso().send(REMITENTE_ID, CORREO_DESTINATARIO))
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

            StepVerifier.create(casoDeUso().send(REMITENTE_ID, CORREO_DESTINATARIO))
                    .assertNext(vista -> esLaEnviadaSinNombre(vista))
                    .verifyComplete();

            assertThat(guardada().addresseeId()).isNull();
        }

        @Test
        void normalizaElCorreoComoElRegistro() {
            conCuenta(DESTINATARIO_ID);
            when(afiliaciones.existsOpenVisible(REMITENTE_ID, CORREO_DESTINATARIO, DESTINATARIO_ID)).thenReturn(Mono.just(false));
            when(afiliaciones.save(any())).thenReturn(Mono.empty());

            StepVerifier.create(casoDeUso().send(REMITENTE_ID, "  Beto@Ejemplo.COM "))
                    .assertNext(vista -> assertThat(vista.email()).isEqualTo(CORREO_DESTINATARIO))
                    .verifyComplete();
        }

        @Test
        void aSiMismoEs400SobreEmailYNoGuardaNada() {
            conCuenta(REMITENTE_ID);

            StepVerifier.create(casoDeUso().send(REMITENTE_ID, CORREO_DESTINATARIO))
                    .verifyErrorSatisfies(e -> esError(e, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "email"));

            verify(afiliaciones, never()).save(any());
        }

        @Test
        void conUnaRelacionAbiertaQueElUsuarioVeEs409SobreEmailYNoGuardaNada() {
            conCuenta(DESTINATARIO_ID);
            when(afiliaciones.existsOpenVisible(REMITENTE_ID, CORREO_DESTINATARIO, DESTINATARIO_ID)).thenReturn(Mono.just(true));

            StepVerifier.create(casoDeUso().send(REMITENTE_ID, CORREO_DESTINATARIO))
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
    }

    @Nested
    class Listar {

        @Test
        void devuelveLasAbiertasDelUsuarioEnElOrdenDelRepositorio() {
            AffiliationView recibida = unaVista().build();
            AffiliationView aceptada = unaVista().enviada().conEstado(AffiliationStatus.ACCEPTED).build();
            when(afiliaciones.findOpenByUser(DESTINATARIO_ID)).thenReturn(Flux.just(recibida, aceptada));

            StepVerifier.create(casoDeUso().list(DESTINATARIO_ID))
                    .expectNext(recibida, aceptada)
                    .verifyComplete();
        }
    }

    @Nested
    class CambiarEstado {

        @Test
        void aceptaYDevuelveLaRelacionComoQueda() {
            AffiliationView aceptada = unaVista().conEstado(AffiliationStatus.ACCEPTED).build();
            when(afiliaciones.findForParticipant(AFILIACION_ID, DESTINATARIO_ID)).thenReturn(Mono.just(unaSolicitud().build()));
            when(afiliaciones.changeStatus(AFILIACION_ID, AffiliationStatus.REQUESTED, AffiliationStatus.ACCEPTED))
                    .thenReturn(Mono.just(true));
            when(afiliaciones.findViewForParticipant(AFILIACION_ID, DESTINATARIO_ID)).thenReturn(Mono.just(aceptada));

            StepVerifier.create(casoDeUso().accept(DESTINATARIO_ID, AFILIACION_ID))
                    .expectNext(aceptada)
                    .verifyComplete();
        }

        @Test
        void elDestinatarioDesvinculaUnaAceptada() {
            AffiliationView desvinculada = unaVista().conEstado(AffiliationStatus.UNLINKED).build();
            when(afiliaciones.findForParticipant(AFILIACION_ID, DESTINATARIO_ID))
                    .thenReturn(Mono.just(unaSolicitud().conEstado(AffiliationStatus.ACCEPTED).build()));
            when(afiliaciones.changeStatus(AFILIACION_ID, AffiliationStatus.ACCEPTED, AffiliationStatus.UNLINKED))
                    .thenReturn(Mono.just(true));
            when(afiliaciones.findViewForParticipant(AFILIACION_ID, DESTINATARIO_ID)).thenReturn(Mono.just(desvinculada));

            StepVerifier.create(casoDeUso().unlink(DESTINATARIO_ID, AFILIACION_ID))
                    .expectNext(desvinculada)
                    .verifyComplete();
        }

        @Test
        void unaAjenaOInexistenteEs404SobreIdSinCambiarNada() {
            when(afiliaciones.findForParticipant(AFILIACION_ID, AJENO_ID)).thenReturn(Mono.empty());

            StepVerifier.create(casoDeUso().accept(AJENO_ID, AFILIACION_ID))
                    .verifyErrorSatisfies(e -> esError(e, HttpStatus.NOT_FOUND, "NOT_FOUND", "id"));

            verify(afiliaciones, never()).changeStatus(any(), any(), any());
        }

        @Test
        void unaAccionQueNoCorrespondeAlPapelEs409SobreStatusSinCambiarNada() {
            when(afiliaciones.findForParticipant(AFILIACION_ID, REMITENTE_ID)).thenReturn(Mono.just(unaSolicitud().build()));

            StepVerifier.create(casoDeUso().accept(REMITENTE_ID, AFILIACION_ID))
                    .verifyErrorSatisfies(e -> esError(e, HttpStatus.CONFLICT, "INVALID_STATE", "status"));

            verify(afiliaciones, never()).changeStatus(any(), any(), any());
        }

        @Test
        void siOtraPeticionLaCambioAntesEs409SobreStatus() {
            when(afiliaciones.findForParticipant(AFILIACION_ID, REMITENTE_ID)).thenReturn(Mono.just(unaSolicitud().build()));
            when(afiliaciones.changeStatus(AFILIACION_ID, AffiliationStatus.REQUESTED, AffiliationStatus.CANCELLED))
                    .thenReturn(Mono.just(false));

            StepVerifier.create(casoDeUso().cancel(REMITENTE_ID, AFILIACION_ID))
                    .verifyErrorSatisfies(e -> esError(e, HttpStatus.CONFLICT, "INVALID_STATE", "status"));
        }
    }

    @Nested
    class Saldos {

        @Test
        void devuelveElSaldoDeCadaPersonaDelUsuario() {
            AffiliateBalance afiliada = unSaldo().build();
            AffiliateBalance desvinculada = unSaldo().conEstado(AffiliationStatus.UNLINKED).build();
            when(saldos.findBalances(UserMother.ID)).thenReturn(Flux.just(afiliada, desvinculada));

            StepVerifier.create(casoDeUso().balances(UserMother.ID))
                    .expectNext(afiliada, desvinculada)
                    .verifyComplete();
        }
    }

    @Nested
    class Partes {

        @Test
        void elRemitenteVeLasPartesConElDestinatario() {
            ShareWithAffiliate parte = unaParte().build();
            when(afiliaciones.findForParticipant(AFILIACION_ID, REMITENTE_ID))
                    .thenReturn(Mono.just(unaSolicitud().conEstado(AffiliationStatus.ACCEPTED).build()));
            when(saldos.findSharesBetween(REMITENTE_ID, DESTINATARIO_ID)).thenReturn(Flux.just(parte));

            StepVerifier.create(casoDeUso().shares(REMITENTE_ID, AFILIACION_ID))
                    .expectNext(parte)
                    .verifyComplete();
        }

        @Test
        void elDestinatarioVeLasPartesConElRemitenteAunqueEsteDesvinculada() {
            ShareWithAffiliate parte = unaParte().queDebo().build();
            when(afiliaciones.findForParticipant(AFILIACION_ID, DESTINATARIO_ID))
                    .thenReturn(Mono.just(unaSolicitud().conEstado(AffiliationStatus.UNLINKED).build()));
            when(saldos.findSharesBetween(DESTINATARIO_ID, REMITENTE_ID)).thenReturn(Flux.just(parte));

            StepVerifier.create(casoDeUso().shares(DESTINATARIO_ID, AFILIACION_ID))
                    .expectNext(parte)
                    .verifyComplete();
        }

        @Test
        void unaAjenaOInexistenteEs404SobreId() {
            when(afiliaciones.findForParticipant(AFILIACION_ID, AJENO_ID)).thenReturn(Mono.empty());

            StepVerifier.create(casoDeUso().shares(AJENO_ID, AFILIACION_ID))
                    .verifyErrorSatisfies(e -> esError(e, HttpStatus.NOT_FOUND, "NOT_FOUND", "id"));

            verify(saldos, never()).findSharesBetween(any(), any());
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = AffiliationStatus.class, names = {"REQUESTED", "REJECTED", "CANCELLED"})
        void unaQueNuncaQuedoAceptadaEs409SobreStatus(AffiliationStatus estado) {
            when(afiliaciones.findForParticipant(AFILIACION_ID, REMITENTE_ID))
                    .thenReturn(Mono.just(unaSolicitud().conEstado(estado).build()));

            StepVerifier.create(casoDeUso().shares(REMITENTE_ID, AFILIACION_ID))
                    .verifyErrorSatisfies(e -> esError(e, HttpStatus.CONFLICT, "INVALID_STATE", "status"));

            verify(saldos, never()).findSharesBetween(any(), any());
        }
    }

    private AffiliationUseCase casoDeUso() {
        return new AffiliationUseCase(afiliaciones, usuarios, saldos, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static void esError(Throwable e, HttpStatus status, String codigo, String campo) {
        BadRequestException error = (BadRequestException) e;
        assertThat(error.getHttpStatus()).isEqualTo(status);
        assertThat(error.getErrorResponse().getErrors()).extracting(ErrorDetail::getCode, ErrorDetail::getField)
                .containsExactly(tuple(codigo, campo));
    }
}
