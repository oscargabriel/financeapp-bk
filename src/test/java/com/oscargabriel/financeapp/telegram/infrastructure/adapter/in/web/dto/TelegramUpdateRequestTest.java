package com.oscargabriel.financeapp.telegram.infrastructure.adapter.in.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.oscargabriel.financeapp.telegram.domain.model.ArchivoDeTelegram;
import com.oscargabriel.financeapp.telegram.domain.model.BotonDeTelegram;
import com.oscargabriel.financeapp.telegram.domain.model.EventoDeTelegram;
import com.oscargabriel.financeapp.telegram.domain.model.MensajeDeTelegram;
import com.oscargabriel.financeapp.telegram.support.TelegramMother;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class TelegramUpdateRequestTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private static EventoDeTelegram evento(String cuerpo) {
        return JSON.readValue(cuerpo, TelegramUpdateRequest.class).evento().orElseThrow();
    }

    private static MensajeDeTelegram mensaje(String cuerpo) {
        return (MensajeDeTelegram) evento(cuerpo);
    }

    @Test
    void unTextoLlegaComoTextoSinArchivo() {
        MensajeDeTelegram mensaje = mensaje(TelegramMother.mensajeDeTexto());

        assertThat(mensaje.texto()).isEqualTo("hola");
        assertThat(mensaje.archivo()).isNull();
        assertThat(mensaje.privado()).isTrue();
    }

    /** Telegram ordena los tamanos de menor a mayor: va el ultimo. */
    @Test
    void unaFotoConPieLlegaConElPieYLaFotoMasGrande() {
        MensajeDeTelegram mensaje = mensaje(TelegramMother.fotoConPie());

        assertThat(mensaje.texto()).isEqualTo("almuerzo 45000");
        assertThat(mensaje.archivo()).isEqualTo(new ArchivoDeTelegram("foto-grande", "image/jpeg", 90_000L));
    }

    @Test
    void unaNotaDeVozSinMimeLlegaComoAudioOgg() {
        MensajeDeTelegram mensaje = mensaje(TelegramMother.notaDeVozSinMime());

        assertThat(mensaje.texto()).isNull();
        assertThat(mensaje.archivo()).isEqualTo(new ArchivoDeTelegram("voz", "audio/ogg", 12_000L));
    }

    @Test
    void unAudioLlegaConSuMime() {
        assertThat(mensaje(TelegramMother.audio()).archivo())
                .isEqualTo(new ArchivoDeTelegram("cancion", "audio/mpeg", 40_000L));
    }

    @Test
    void unVideoLlegaSinContenido() {
        assertThat(mensaje(TelegramMother.video()).sinContenido()).isTrue();
    }

    @Test
    void unBotonLlegaConSuChatSuMensajeYSusDatos() {
        assertThat(evento(TelegramMother.boton("confirmar:abc")))
                .isEqualTo(new BotonDeTelegram("cb-1", TelegramMother.CHAT, true, 50L, "confirmar:abc"));
    }

    @Test
    void unBotonSinMensajeLlegaSinChatNiMensaje() {
        assertThat(evento(TelegramMother.botonSinMensaje()))
                .isEqualTo(new BotonDeTelegram("cb-2", null, false, null, "confirmar:x"));
    }

    @Test
    void unUpdateSinMensajeNiBotonNoTraeEvento() {
        assertThat(JSON.readValue(TelegramMother.sinMensaje(), TelegramUpdateRequest.class).evento()).isEmpty();
    }
}
