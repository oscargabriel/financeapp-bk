package com.oscargabriel.financeapp.telegram.infrastructure.adapter.out.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;

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
                        .contains(String.valueOf(TelegramMother.CHAT), "hola", "PEDIR_CONTACTO"));
    }

    @Test
    void noExisteConElPerfilProd() {
        assertThat(StubTelegramBotAdapter.class.getAnnotation(Profile.class).value()).containsExactly("!prod");
    }
}
