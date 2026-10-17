package com.oscargabriel.financeapp.telegram.infrastructure.adapter.out.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.telegram.domain.model.RespuestaDelBot;
import com.oscargabriel.financeapp.telegram.domain.model.TecladoDelBot;
import com.oscargabriel.financeapp.telegram.support.TelegramMother;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import reactor.test.StepVerifier;

class StubTelegramBotAdapterTest {

    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void capturarLog() {
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(StubTelegramBotAdapter.class)).addAppender(logs);
    }

    @AfterEach
    void soltarLog() {
        ((Logger) LoggerFactory.getLogger(StubTelegramBotAdapter.class)).detachAppender(logs);
    }

    @Test
    void dejaElEnvioEnElLogConSuTecladoEnVezDeLlamarATelegram() {
        StepVerifier.create(new StubTelegramBotAdapter().enviar(TelegramMother.CHAT,
                        new RespuestaDelBot("hola", TecladoDelBot.PEDIR_CONTACTO)))
                .verifyComplete();

        assertThat(logs.list).singleElement().satisfies(evento ->
                assertThat(evento.getFormattedMessage())
                        .contains(String.valueOf(TelegramMother.CHAT), "hola", "PedirContacto"));
    }

    @Test
    void noExisteConElPerfilProd() {
        assertThat(StubTelegramBotAdapter.class.getAnnotation(Profile.class).value()).containsExactly("!prod");
    }

    @Test
    void dejaEnElLogLosBotonesDelGrupo() {
        UUID grupo = UUID.fromString("71000000-0000-7000-8000-000000000009");

        StepVerifier.create(new StubTelegramBotAdapter().enviar(TelegramMother.CHAT,
                        new RespuestaDelBot("registre", new TecladoDelBot.ConfirmarODescartar(grupo))))
                .verifyComplete();

        assertThat(logs.list).singleElement().satisfies(evento ->
                assertThat(evento.getFormattedMessage()).contains(grupo.toString()));
    }

    /** bruno/telegram/ manda una foto y una nota de voz con estos file_id: el asistente stub solo mira el texto. */
    @Test
    void stubJpegEntregaUnaCabeceraJpeg() {
        StepVerifier.create(new StubTelegramBotAdapter().descargar("stub-jpeg"))
                .assertNext(bytes -> assertThat(bytes).startsWith((byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0))
                .verifyComplete();
    }

    @Test
    void stubOggEntregaUnaCabeceraOgg() {
        StepVerifier.create(new StubTelegramBotAdapter().descargar("stub-ogg"))
                .assertNext(bytes -> assertThat(bytes).startsWith((byte) 'O', (byte) 'g', (byte) 'g', (byte) 'S'))
                .verifyComplete();
    }

    @Test
    void otroFileIdFallaComoSiTelegramNoLoEntregara() {
        StepVerifier.create(new StubTelegramBotAdapter().descargar("otro"))
                .verifyErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(BadRequestException.class, e ->
                                assertThat(e.getErrorResponse().getErrors().getFirst().getCode())
                                        .isEqualTo(ErrorCodes.EXTERNAL_SERVICE_ERROR.name())));
    }

    @Test
    void cerrarLaEsperaYQuitarLosBotonesQuedanEnElLog() {
        StubTelegramBotAdapter stub = new StubTelegramBotAdapter();

        StepVerifier.create(stub.cerrarEspera("cb-1").then(stub.quitarBotones(TelegramMother.CHAT, 50L)))
                .verifyComplete();

        assertThat(logs.list).hasSize(2);
        assertThat(logs.list.get(0).getFormattedMessage()).contains("cb-1");
        assertThat(logs.list.get(1).getFormattedMessage()).contains(String.valueOf(TelegramMother.CHAT), "50");
    }
}
