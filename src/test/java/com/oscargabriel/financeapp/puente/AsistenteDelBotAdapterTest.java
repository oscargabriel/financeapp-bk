package com.oscargabriel.financeapp.puente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.oscargabriel.financeapp.ia.domain.model.AssistantBalance;
import com.oscargabriel.financeapp.ia.domain.model.AssistantChannel;
import com.oscargabriel.financeapp.ia.domain.model.AssistantFileType;
import com.oscargabriel.financeapp.ia.domain.model.AssistantMessage;
import com.oscargabriel.financeapp.ia.domain.model.AssistantReply;
import com.oscargabriel.financeapp.ia.domain.model.AssistantReport;
import com.oscargabriel.financeapp.ia.domain.model.CreatedGroup;
import com.oscargabriel.financeapp.ia.domain.model.CreatedMovement;
import com.oscargabriel.financeapp.ia.domain.model.TransactionType;
import com.oscargabriel.financeapp.ia.domain.port.in.AssistantPort;
import com.oscargabriel.financeapp.ia.support.AsistenteMother;
import com.oscargabriel.financeapp.telegram.domain.model.RespuestaDelAsistente;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AsistenteDelBotAdapterTest {

    private static final UUID USUARIO = AsistenteMother.USER_ID;
    private static final UUID GRUPO = UUID.fromString("71000000-0000-7000-8000-000000000001");
    private static final Instant CUANDO = Instant.parse("2026-10-05T17:00:00Z");
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};
    private static final byte[] OGG = {'O', 'g', 'g', 'S', 1, 2, 3};

    @Mock
    private AssistantPort asistente;

    @Captor
    private ArgumentCaptor<AssistantMessage> mensaje;

    private AsistenteDelBotAdapter puente;

    @BeforeEach
    void escenario() {
        puente = new AsistenteDelBotAdapter(asistente);
    }

    private void responde(AssistantReply respuesta) {
        when(asistente.atender(eq(USUARIO), eq(AssistantChannel.TELEGRAM), any())).thenReturn(Mono.just(respuesta));
    }

    @Test
    void mandaElTextoSinArchivosPorElCanalDeTelegram() {
        responde(AssistantReply.aclaracion("Cual cuenta?"));

        StepVerifier.create(puente.atender(USUARIO, "gaste 20 mil", null, null))
                .expectNext(new RespuestaDelAsistente.Texto("Cual cuenta?"))
                .verifyComplete();

        verify(asistente).atender(eq(USUARIO), eq(AssistantChannel.TELEGRAM), mensaje.capture());
        assertThat(mensaje.getValue().texto()).isEqualTo("gaste 20 mil");
        assertThat(mensaje.getValue().archivos()).isEmpty();
    }

    @Test
    void mandaLaFotoComoImagenJpegJuntoAlPie() {
        responde(AssistantReply.noSoportado("No se hacer eso"));

        StepVerifier.create(puente.atender(USUARIO, "el recibo", "image/jpeg", JPEG))
                .expectNext(new RespuestaDelAsistente.Texto("No se hacer eso"))
                .verifyComplete();

        verify(asistente).atender(eq(USUARIO), eq(AssistantChannel.TELEGRAM), mensaje.capture());
        assertThat(mensaje.getValue().texto()).isEqualTo("el recibo");
        assertThat(mensaje.getValue().archivos()).singleElement()
                .satisfies(archivo -> {
                    assertThat(archivo.tipo()).isEqualTo(AssistantFileType.JPEG);
                    assertThat(archivo.contenido()).isEqualTo(JPEG);
                });
    }

    @Test
    void unaNotaDeVozSinPieLlegaSinTexto() {
        responde(AssistantReply.aclaracion("Cuanto fue?"));

        StepVerifier.create(puente.atender(USUARIO, null, "audio/ogg", OGG))
                .expectNextCount(1)
                .verifyComplete();

        verify(asistente).atender(eq(USUARIO), eq(AssistantChannel.TELEGRAM), mensaje.capture());
        assertThat(mensaje.getValue().texto()).isNull();
        assertThat(mensaje.getValue().archivos()).singleElement()
                .extracting(archivo -> archivo.tipo()).isEqualTo(AssistantFileType.OGG);
    }

    @Test
    void unPieEnBlancoJuntoAUnArchivoEsAusenciaDeTexto() {
        responde(AssistantReply.aclaracion("Cuanto fue?"));

        StepVerifier.create(puente.atender(USUARIO, "   ", "audio/ogg", OGG))
                .expectNextCount(1)
                .verifyComplete();

        verify(asistente).atender(eq(USUARIO), eq(AssistantChannel.TELEGRAM), mensaje.capture());
        assertThat(mensaje.getValue().texto()).isNull();
    }

    @Test
    void unArchivoCuyoContenidoNoCoincideConElTipoNoLlegaAlAsistente() {
        StepVerifier.create(puente.atender(USUARIO, "un audio", "audio/ogg", JPEG))
                .expectNext(new RespuestaDelAsistente.ArchivoNoAdmitido())
                .verifyComplete();

        verifyNoInteractions(asistente);
    }

    @Test
    void unArchivoDeUnTipoQueElAsistenteNoAdmiteNoLlegaAlAsistente() {
        StepVerifier.create(puente.atender(USUARIO, null, "video/mp4", JPEG))
                .expectNext(new RespuestaDelAsistente.ArchivoNoAdmitido())
                .verifyComplete();

        verifyNoInteractions(asistente);
    }

    @Test
    void unArchivoPorEncimaDelTopeNoLlegaAlAsistente() {
        byte[] grande = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(JPEG, 0, grande, 0, JPEG.length);

        StepVerifier.create(puente.atender(USUARIO, null, "image/jpeg", grande))
                .expectNext(new RespuestaDelAsistente.ArchivoNoAdmitido())
                .verifyComplete();

        verifyNoInteractions(asistente);
    }

    @Test
    void traduceElRegistroConElGrupoYCadaMovimiento() {
        responde(AssistantReply.creado("Registre", new CreatedGroup(GRUPO, List.of(
                new CreatedMovement(UUID.randomUUID(), TransactionType.EXPENSE, "PENDING", new BigDecimal("45000"),
                        "COP", "Almuerzo", CUANDO, "Billetera", null, "Mercado"),
                new CreatedMovement(UUID.randomUUID(), TransactionType.TRANSFER, "PENDING", new BigDecimal("100000"),
                        "COP", "Al ahorro", CUANDO, "Billetera", "Ahorros", null)))));

        StepVerifier.create(puente.atender(USUARIO, "gaste y pase", null, null))
                .expectNext(new RespuestaDelAsistente.Registro(GRUPO, List.of(
                        new RespuestaDelAsistente.Movimiento("EXPENSE", "Billetera", null, "Mercado",
                                new BigDecimal("45000"), "COP", "Almuerzo", CUANDO),
                        new RespuestaDelAsistente.Movimiento("TRANSFER", "Billetera", "Ahorros", null,
                                new BigDecimal("100000"), "COP", "Al ahorro", CUANDO))))
                .verifyComplete();
    }

    @Test
    void traduceLaConsultaDeMovimientosConSusTotalesYLaLista() {
        AssistantReport.Item item = AsistenteMother.unMovimiento(TransactionType.EXPENSE, "10", "40000");
        AssistantReport reporte = AssistantReport.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "COP",
                List.of(item));
        responde(AssistantReply.reporte("Encontre 1", reporte));

        StepVerifier.create(puente.atender(USUARIO, "mis gastos", null, null))
                .expectNext(new RespuestaDelAsistente.Movimientos(LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 31), "COP", BigDecimal.ZERO, new BigDecimal("40000"),
                        new BigDecimal("-40000"), List.of(new RespuestaDelAsistente.Movimiento("EXPENSE",
                                "Ahorros USD", null, "Mercado", new BigDecimal("10"), "USD", "Un movimiento",
                                item.occurredAt()))))
                .verifyComplete();
    }

    @Test
    void traduceElSaldoConLosTotalesYCadaCuenta() {
        AssistantBalance saldo = AssistantBalance.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                AsistenteMother.sumas(), List.of(AsistenteMother.saldoDeEfectivo()));
        responde(AssistantReply.saldo("Tu saldo", saldo));

        StepVerifier.create(puente.atender(USUARIO, "mi saldo", null, null))
                .expectNext(new RespuestaDelAsistente.Saldo(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                        "COP", new BigDecimal("3000000"), new BigDecimal("1200000"), new BigDecimal("1800000"),
                        new BigDecimal("1500000"),
                        List.of(new RespuestaDelAsistente.Cuenta("Efectivo", "COP", new BigDecimal("250000")))))
                .verifyComplete();
    }

    @Test
    void propagaElErrorDelAsistenteTalCual() {
        IllegalStateException falla = new IllegalStateException("el modelo no respondio");
        when(asistente.atender(eq(USUARIO), eq(AssistantChannel.TELEGRAM), any())).thenReturn(Mono.error(falla));

        StepVerifier.create(puente.atender(USUARIO, "hola", null, null))
                .verifyErrorMatches(e -> e == falla);
    }
}
