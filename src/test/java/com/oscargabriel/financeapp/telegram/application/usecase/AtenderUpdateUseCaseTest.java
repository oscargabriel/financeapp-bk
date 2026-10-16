package com.oscargabriel.financeapp.telegram.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import com.oscargabriel.financeapp.telegram.domain.model.MensajeDeTelegram;
import com.oscargabriel.financeapp.telegram.domain.model.RespuestaDelBot;
import com.oscargabriel.financeapp.telegram.domain.model.TecladoDelBot;
import com.oscargabriel.financeapp.telegram.domain.port.out.BotDeTelegramPort;
import com.oscargabriel.financeapp.telegram.domain.port.out.ChatsVinculadosPort;
import com.oscargabriel.financeapp.telegram.support.TelegramMother;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.publisher.PublisherProbe;

@ExtendWith(MockitoExtension.class)
class AtenderUpdateUseCaseTest {

    private static final RespuestaDelBot SIN_VINCULAR = new RespuestaDelBot(
            "Este chat todavía no está vinculado a una cuenta de FinanceApp. Comparte tu número con el botón de"
                    + " abajo para vincularlo.",
            TecladoDelBot.PEDIR_CONTACTO);

    private static final RespuestaDelBot SOLO_PRIVADOS = new RespuestaDelBot(
            "Este bot solo atiende chats privados.", TecladoDelBot.NINGUNO);

    private static final RespuestaDelBot CONTACTO_AJENO = new RespuestaDelBot(
            "Solo puedo vincular este chat con tu propio número. Compártelo con el botón de abajo.",
            TecladoDelBot.PEDIR_CONTACTO);

    private static final RespuestaDelBot NO_VINCULADO = new RespuestaDelBot(
            "No pude vincular este chat con ese número.", TecladoDelBot.QUITAR);

    private static final RespuestaDelBot RECIEN_VINCULADO = new RespuestaDelBot(
            "Hola, Ana. Este chat quedó vinculado a tu cuenta de FinanceApp.", TecladoDelBot.QUITAR);

    private static final RespuestaDelBot YA_VINCULADO = new RespuestaDelBot(
            "Hola, Ana. Este chat ya está vinculado a tu cuenta de FinanceApp.", TecladoDelBot.NINGUNO);

    @Mock
    private BotDeTelegramPort bot;

    @Mock
    private ChatsVinculadosPort chats;

    @InjectMocks
    private AtenderUpdateUseCase useCase;

    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void capturarLog() {
        logs = new ListAppender<>();
        logs.start();
        raiz().addAppender(logs);
    }

    @AfterEach
    void soltarLog() {
        raiz().detachAppender(logs);
    }

    private static Logger raiz() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    private PublisherProbe<Void> envioPosible() {
        PublisherProbe<Void> envio = PublisherProbe.empty();
        when(bot.enviar(anyLong(), any())).thenReturn(envio.mono());
        return envio;
    }

    private void sinVincular() {
        when(chats.usuarioDelChat(TelegramMother.CHAT)).thenReturn(Mono.empty());
    }

    private void atiende(MensajeDeTelegram mensaje) {
        StepVerifier.create(useCase.atender(mensaje)).verifyComplete();
    }

    @Test
    void unChatNoPrivadoRecibeQueSoloAtiendeChatsPrivadosSinConsultarLosChats() {
        PublisherProbe<Void> envio = envioPosible();

        atiende(TelegramMother.deGrupo(null));

        verify(bot).enviar(TelegramMother.GRUPO, SOLO_PRIVADOS);
        envio.assertWasSubscribed();
        verifyNoInteractions(chats);
    }

    /** En un grupo el chat es de todos sus miembros: vincularlo dejaria a cualquiera actuar como el usuario. */
    @Test
    void unContactoPropioDesdeUnGrupoNoVincula() {
        envioPosible();

        atiende(TelegramMother.deGrupo(TelegramMother.contactoPropio(TelegramMother.TELEFONO)));

        verify(bot).enviar(TelegramMother.GRUPO, SOLO_PRIVADOS);
        verifyNoInteractions(chats);
    }

    @Test
    void unChatVinculadoRecibeElSaludoConElNombre() {
        envioPosible();
        when(chats.usuarioDelChat(TelegramMother.CHAT)).thenReturn(Mono.just(TelegramMother.ana()));

        atiende(TelegramMother.privado(null));

        verify(bot).enviar(TelegramMother.CHAT, YA_VINCULADO);
    }

    @Test
    void unContactoEnUnChatVinculadoNoCambiaLaVinculacion() {
        envioPosible();
        when(chats.usuarioDelChat(TelegramMother.CHAT)).thenReturn(Mono.just(TelegramMother.ana()));

        atiende(TelegramMother.privado(TelegramMother.contactoPropio("+573009990002")));

        verify(bot).enviar(TelegramMother.CHAT, YA_VINCULADO);
        verify(chats, never()).vincular(anyLong(), anyString());
    }

    @Test
    void unChatSinVincularSinContactoRecibeElTextoFijoConElBoton() {
        envioPosible();
        sinVincular();

        atiende(TelegramMother.privado(null));

        verify(bot).enviar(TelegramMother.CHAT, SIN_VINCULAR);
    }

    @Test
    void unContactoDeOtraPersonaNoVincula() {
        envioPosible();
        sinVincular();

        atiende(TelegramMother.privado(new MensajeDeTelegram.Contacto(888002L, TelegramMother.TELEFONO)));

        verify(bot).enviar(TelegramMother.CHAT, CONTACTO_AJENO);
        verify(chats, never()).vincular(anyLong(), anyString());
    }

    @Test
    void unContactoSinUserIdNoVincula() {
        envioPosible();
        sinVincular();

        atiende(TelegramMother.privado(new MensajeDeTelegram.Contacto(null, TelegramMother.TELEFONO)));

        verify(bot).enviar(TelegramMother.CHAT, CONTACTO_AJENO);
        verify(chats, never()).vincular(anyLong(), anyString());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "abc", "+0573009990001", "57300", "+5730099900011234567"})
    void unTelefonoQueNoEsE164RecibeElGenericoSinTocarLaBase(String telefono) {
        envioPosible();
        sinVincular();

        atiende(TelegramMother.privado(TelegramMother.contactoPropio(telefono)));

        verify(bot).enviar(TelegramMother.CHAT, NO_VINCULADO);
        verify(chats, never()).vincular(anyLong(), anyString());
    }

    /** Sin admitir, sin usuario, inactivo o de dos usuarios: el adapter termina vacio y el texto no dice cual. */
    @Test
    void unaVinculacionRechazadaRecibeElGenerico() {
        envioPosible();
        sinVincular();
        when(chats.vincular(TelegramMother.CHAT, TelegramMother.TELEFONO)).thenReturn(Mono.empty());

        atiende(TelegramMother.privado(TelegramMother.contactoPropio(TelegramMother.TELEFONO)));

        verify(bot).enviar(TelegramMother.CHAT, NO_VINCULADO);
        assertThat(logs.list).allSatisfy(evento ->
                assertThat(evento.getFormattedMessage()).doesNotContain("9990001"));
    }

    /** Telegram manda phone_number a veces sin +: se agrega antes de buscarlo. */
    @Test
    void elContactoPropioConTelefonoAdmitidoVinculaSaludaYQuitaElBoton() {
        envioPosible();
        sinVincular();
        when(chats.vincular(TelegramMother.CHAT, TelegramMother.TELEFONO)).thenReturn(Mono.just(TelegramMother.ana()));

        atiende(TelegramMother.privado(TelegramMother.contactoPropio(" 573009990001 ")));

        verify(chats).vincular(TelegramMother.CHAT, TelegramMother.TELEFONO);
        verify(bot).enviar(TelegramMother.CHAT, RECIEN_VINCULADO);
        assertThat(logs.list).isNotEmpty().allSatisfy(evento ->
                assertThat(evento.getFormattedMessage()).doesNotContain("9990001"));
    }

    /** La base fallo: el adapter ya dejo el tipo en el log, y sin respuesta el webhook da 200 igual. */
    @Test
    void unFalloAlResolverElChatTerminaSinErrorNiRespuesta() {
        when(chats.usuarioDelChat(TelegramMother.CHAT)).thenReturn(Mono.error(new IllegalStateException("base caida")));

        atiende(TelegramMother.privado(null));

        verifyNoInteractions(bot);
    }

    @Test
    void unFalloAlVincularTerminaSinErrorNiRespuesta() {
        sinVincular();
        when(chats.vincular(anyLong(), anyString())).thenReturn(Mono.error(new IllegalStateException("base caida")));

        atiende(TelegramMother.privado(TelegramMother.contactoPropio(TelegramMother.TELEFONO)));

        verifyNoInteractions(bot);
        assertThat(logs.list).allSatisfy(evento ->
                assertThat(evento.getFormattedMessage()).doesNotContain("9990001"));
    }

    /** Telegram reintenta un update que no recibe 2xx: el fallo del envio no llega al webhook. */
    @Test
    void unFalloAlResponderTerminaSinError() {
        sinVincular();
        when(bot.enviar(anyLong(), any())).thenReturn(Mono.error(new IllegalStateException("Telegram caido")));

        atiende(TelegramMother.privado(null));
    }
}
