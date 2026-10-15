package com.oscargabriel.financeapp.telegram.application.usecase;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.oscargabriel.financeapp.telegram.domain.model.MensajeDeTelegram;
import com.oscargabriel.financeapp.telegram.domain.port.out.BotDeTelegramPort;
import com.oscargabriel.financeapp.telegram.support.TelegramMother;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.publisher.PublisherProbe;

@ExtendWith(MockitoExtension.class)
class AtenderUpdateUseCaseTest {

    private static final String SIN_VINCULAR = "Este chat todavía no está vinculado a una cuenta de FinanceApp.";

    @Mock
    private BotDeTelegramPort bot;

    @InjectMocks
    private AtenderUpdateUseCase useCase;

    /** Hasta FIN-60 no hay vinculacion: todo chat esta sin vincular y recibe el texto fijo. */
    @Test
    void respondeAlChatDelMensajeQueTodaviaNoEstaVinculado() {
        PublisherProbe<Void> envio = PublisherProbe.empty();
        when(bot.enviar(anyLong(), anyString())).thenReturn(envio.mono());

        StepVerifier.create(useCase.atender(new MensajeDeTelegram(TelegramMother.CHAT)))
                .verifyComplete();

        verify(bot).enviar(TelegramMother.CHAT, SIN_VINCULAR);
        envio.assertWasSubscribed();
    }

    /** Telegram reintenta un update que no recibe 2xx: el fallo del envio no llega al webhook. */
    @Test
    void unFalloAlResponderTerminaSinError() {
        when(bot.enviar(anyLong(), anyString())).thenReturn(Mono.error(new IllegalStateException("Telegram caido")));

        StepVerifier.create(useCase.atender(new MensajeDeTelegram(TelegramMother.CHAT)))
                .verifyComplete();
    }
}
