package com.oscargabriel.financeapp.telegram.infrastructure.adapter.out.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;

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
    void dejaElEnvioEnElLogEnVezDeLlamarATelegram() {
        StepVerifier.create(new StubTelegramBotAdapter().enviar(TelegramMother.CHAT, "hola"))
                .verifyComplete();

        assertThat(logs.list).singleElement().satisfies(evento ->
                assertThat(evento.getFormattedMessage()).contains(String.valueOf(TelegramMother.CHAT), "hola"));
    }

    @Test
    void noExisteConElPerfilProd() {
        assertThat(StubTelegramBotAdapter.class.getAnnotation(Profile.class).value()).containsExactly("!prod");
    }
}
