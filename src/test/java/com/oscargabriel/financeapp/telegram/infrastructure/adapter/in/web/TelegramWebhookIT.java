package com.oscargabriel.financeapp.telegram.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.oscargabriel.financeapp.compartido.support.BasicMother;
import com.oscargabriel.financeapp.compartido.support.SesionSiempreAbierta;
import com.oscargabriel.financeapp.compartido.support.TokenMother;
import com.oscargabriel.financeapp.telegram.domain.model.RespuestaDelBot;
import com.oscargabriel.financeapp.telegram.domain.model.TecladoDelBot;
import com.oscargabriel.financeapp.telegram.domain.port.out.BotDeTelegramPort;
import com.oscargabriel.financeapp.telegram.domain.port.out.ChatsVinculadosPort;
import com.oscargabriel.financeapp.telegram.support.TelegramMother;

import reactor.core.publisher.Mono;

/**
 * El webhook contra un servidor real, con su cadena de seguridad y el base-path /api. El puerto del
 * bot va mockeado: lo que sale hacia Telegram lo prueba TelegramBotApiAdapterTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SesionSiempreAbierta.class)
class TelegramWebhookIT {

    private static final String WEBHOOK = "/api/telegram/webhook";

    @Value("${local.server.port}")
    private int port;

    @MockitoBean
    private BotDeTelegramPort bot;

    /** La suite no tiene base: los chats los resuelve TelegramChatR2dbcAdapter, que verifica bruno/telegram/. */
    @MockitoBean
    private ChatsVinculadosPort chats;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10))
                .build();
        when(chats.usuarioDelChat(anyLong())).thenReturn(Mono.empty());
    }

    private WebTestClient.ResponseSpec update(Consumer<HttpHeaders> credencial, String cuerpo) {
        return webTestClient.post().uri(WEBHOOK)
                .headers(credencial)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cuerpo)
                .exchange();
    }

    private void esUn401DelWebhook(WebTestClient.ResponseSpec respuesta) {
        respuesta.expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Telegram")
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.errors[0].description").isEqualTo("Autenticacion requerida")
                .jsonPath("$.errors[0].field").isEqualTo("authorization");
    }

    @Test
    void sinLaCabeceraDelSecretoDevuelve401YNoEnviaNada() {
        esUn401DelWebhook(update(headers -> {}, TelegramMother.mensajeDeTexto()));

        verifyNoInteractions(bot);
    }

    @Test
    void conUnSecretoEquivocadoDevuelve401YNoEnviaNada() {
        esUn401DelWebhook(update(TelegramMother.cabeceraConSecretoEquivocado(), TelegramMother.mensajeDeTexto()));

        verifyNoInteractions(bot);
    }

    @Test
    void conUnSecretoVacioDevuelve401() {
        esUn401DelWebhook(update(headers -> headers.set(TelegramMother.CABECERA, ""),
                TelegramMother.mensajeDeTexto()));

        verifyNoInteractions(bot);
    }

    /** La credencial compartida del front no abre el webhook. */
    @Test
    void conLaCredencialBasicDevuelve401() {
        esUn401DelWebhook(update(BasicMother.cabecera(), TelegramMother.mensajeDeTexto()));

        verifyNoInteractions(bot);
    }

    @Test
    void conUnTokenValidoDevuelve401() {
        esUn401DelWebhook(update(headers -> headers.setBearerAuth(TokenMother.valido()),
                TelegramMother.mensajeDeTexto()));

        verifyNoInteractions(bot);
    }

    @Test
    void unMensajeDeTextoRespondeAlChatY200() {
        when(bot.enviar(anyLong(), any())).thenReturn(Mono.empty());

        update(TelegramMother.cabecera(), TelegramMother.mensajeDeTexto())
                .expectStatus().isOk();

        verify(bot).enviar(eq(TelegramMother.CHAT), argThat(r -> r.teclado() == TecladoDelBot.PEDIR_CONTACTO));
    }

    @Test
    void unMensajeSinTextoTambienRespondeAlChat() {
        when(bot.enviar(anyLong(), any())).thenReturn(Mono.empty());

        update(TelegramMother.cabecera(), TelegramMother.mensajeSinTexto())
                .expectStatus().isOk();

        verify(bot).enviar(eq(TelegramMother.CHAT), any());
    }

    /** FIN-60: from.id, el tipo del chat y el contacto llegan al caso de uso, que agrega el + al telefono. */
    @Test
    void unContactoPropioLlegaConSusCamposYVinculaElChat() {
        when(bot.enviar(anyLong(), any())).thenReturn(Mono.empty());
        when(chats.vincular(anyLong(), anyString())).thenReturn(Mono.just(TelegramMother.ana()));

        update(TelegramMother.cabecera(), TelegramMother.mensajeConContactoPropio())
                .expectStatus().isOk();

        verify(chats).vincular(TelegramMother.CHAT, TelegramMother.TELEFONO);
        verify(bot).enviar(eq(TelegramMother.CHAT), argThat(r -> r.teclado() == TecladoDelBot.QUITAR));
    }

    @Test
    void unChatSinTipoLlegaComoNoPrivado() {
        when(bot.enviar(anyLong(), any())).thenReturn(Mono.empty());

        update(TelegramMother.cabecera(), TelegramMother.mensajeDeChatSinTipo())
                .expectStatus().isOk();

        verify(bot).enviar(TelegramMother.CHAT,
                new RespuestaDelBot("Este bot solo atiende chats privados.", TecladoDelBot.NINGUNO));
        verify(chats, never()).usuarioDelChat(anyLong());
    }

    /** Un mensaje editado o un callback: 200 para que Telegram no lo reintente, y nada al chat. */
    @Test
    void unUpdateSinMensajeResponde200SinEnviarNada() {
        update(TelegramMother.cabecera(), TelegramMother.sinMensaje())
                .expectStatus().isOk();

        verifyNoInteractions(bot);
    }

    @Test
    void unCuerpoQueNoEsJsonDevuelve400() {
        update(TelegramMother.cabecera(), "{update_id:")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("JSON_PARSING_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("body");

        verifyNoInteractions(bot);
    }

    @Test
    void unMensajeSinChatDevuelve400SobreElChat() {
        update(TelegramMother.cabecera(), TelegramMother.mensajeSinChat())
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.length()").isEqualTo(1)
                .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("message.chat");

        verifyNoInteractions(bot);
    }

    @Test
    void unChatSinIdDevuelve400SobreElId() {
        update(TelegramMother.cabecera(), """
                {"update_id":10005,"message":{"message_id":4,"chat":{"type":"private"},"text":"hola"}}""")
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.errors[0].field").isEqualTo("message.chat.id");

        verifyNoInteractions(bot);
    }

    /** Telegram reintenta lo que no recibe 2xx: un fallo al responder no puede repetir el update. */
    @Test
    void unFalloAlResponderDevuelve200Igual() {
        when(bot.enviar(anyLong(), any())).thenReturn(Mono.error(new IllegalStateException("Telegram caido")));

        update(TelegramMother.cabecera(), TelegramMother.mensajeDeTexto())
                .expectStatus().isOk();
    }

    /** El matcher incluye el metodo: un GET no entra a la cadena del webhook, cae en la del JWT. */
    @Test
    void unGetConElSecretoLoAtiendeLaCadenaDelJwt() {
        webTestClient.get().uri(WEBHOOK)
                .headers(TelegramMother.cabecera())
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    }
}
