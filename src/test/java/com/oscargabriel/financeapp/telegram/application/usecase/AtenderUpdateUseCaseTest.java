package com.oscargabriel.financeapp.telegram.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import com.oscargabriel.financeapp.telegram.domain.exceptions.GrupoYaNoPendienteException;
import com.oscargabriel.financeapp.telegram.domain.model.ArchivoDeTelegram;
import com.oscargabriel.financeapp.telegram.domain.model.EventoDeTelegram;
import com.oscargabriel.financeapp.telegram.domain.model.MensajeDeTelegram;
import com.oscargabriel.financeapp.telegram.domain.model.RespuestaDelAsistente;
import com.oscargabriel.financeapp.telegram.domain.model.RespuestaDelBot;
import com.oscargabriel.financeapp.telegram.domain.model.TecladoDelBot;
import com.oscargabriel.financeapp.telegram.domain.port.out.AsistenteDelBotPort;
import com.oscargabriel.financeapp.telegram.domain.port.out.BotDeTelegramPort;
import com.oscargabriel.financeapp.telegram.domain.port.out.ChatsVinculadosPort;
import com.oscargabriel.financeapp.telegram.domain.port.out.GruposDelBotPort;
import com.oscargabriel.financeapp.telegram.domain.port.out.UpdatesAtendidosPort;
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

    private static final RespuestaDelBot REINTENTAR = new RespuestaDelBot(
            "No pude procesar tu mensaje. Intenta de nuevo en un momento.", TecladoDelBot.NINGUNO);

    private static final RespuestaDelBot NO_ADMITIDO = new RespuestaDelBot(
            "Solo entiendo texto, notas de voz, audios y fotos.", TecladoDelBot.NINGUNO);

    private static final RespuestaDelBot ARCHIVO_NO_ADMITIDO = new RespuestaDelBot(
            "No pude leer ese archivo. Manda una foto o una nota de voz de hasta 5 MB.", TecladoDelBot.NINGUNO);

    private static final RespuestaDelBot YA_NO_PENDIENTE = new RespuestaDelBot(
            "Esos movimientos ya no están pendientes.", TecladoDelBot.NINGUNO);

    private static final RespuestaDelBot NO_GUARDADO = new RespuestaDelBot(
            "No pude guardar los movimientos ahora. Intenta de nuevo o revísalos en Pendientes de la web.",
            TecladoDelBot.NINGUNO);

    private static final RespuestaDelBot DESCARTADOS = new RespuestaDelBot(
            "Descarté los movimientos. No se guardó nada.", TecladoDelBot.NINGUNO);

    private static final long UPDATE = 10001L;
    private static final UUID ANA = TelegramMother.ana().id();
    private static final UUID GRUPO = UUID.fromString("71000000-0000-7000-8000-000000000001");
    private static final byte[] BYTES = {1, 2, 3};

    @Mock
    private BotDeTelegramPort bot;

    @Mock
    private ChatsVinculadosPort chats;

    @Mock
    private UpdatesAtendidosPort updates;

    @Mock
    private AsistenteDelBotPort asistente;

    @Mock
    private GruposDelBotPort grupos;

    @InjectMocks
    private AtenderUpdateUseCase useCase;

    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void escenario() {
        logs = new ListAppender<>();
        logs.start();
        raiz().addAppender(logs);
        lenient().when(updates.primeraVez(anyLong())).thenReturn(Mono.just(true));
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

    private void vinculado() {
        when(chats.usuarioDelChat(TelegramMother.CHAT)).thenReturn(Mono.just(TelegramMother.ana()));
    }

    private void atiende(EventoDeTelegram evento) {
        StepVerifier.create(useCase.atender(UPDATE, evento)).verifyComplete();
    }

    @Nested
    class Vinculacion {

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

        @ParameterizedTest
        @ValueSource(strings = {"/start", " /start ", "/start vincular"})
        void unStartDeUnChatVinculadoRecibeElSaludoSinLlegarAlAsistente(String texto) {
            envioPosible();
            vinculado();

            atiende(TelegramMother.texto(texto));

            verify(bot).enviar(TelegramMother.CHAT, YA_VINCULADO);
            verifyNoInteractions(asistente);
        }

        @Test
        void unContactoEnUnChatVinculadoNoCambiaLaVinculacion() {
            envioPosible();
            vinculado();

            atiende(TelegramMother.privado(TelegramMother.contactoPropio("+573009990002")));

            verify(bot).enviar(TelegramMother.CHAT, YA_VINCULADO);
            verify(chats, never()).vincular(anyLong(), anyString());
            verifyNoInteractions(asistente);
        }

        @Test
        void unTextoDeUnChatSinVincularRecibeElTextoFijoConElBotonSinLlegarAlAsistente() {
            envioPosible();
            sinVincular();

            atiende(TelegramMother.texto("gaste 20 mil"));

            verify(bot).enviar(TelegramMother.CHAT, SIN_VINCULAR);
            verifyNoInteractions(asistente);
        }

        @Test
        void unaFotoDeUnChatSinVincularNoSeDescarga() {
            envioPosible();
            sinVincular();

            atiende(TelegramMother.conArchivo(null, TelegramMother.foto()));

            verify(bot).enviar(TelegramMother.CHAT, SIN_VINCULAR);
            verify(bot, never()).descargar(anyString());
            verifyNoInteractions(asistente);
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
            when(chats.vincular(TelegramMother.CHAT, TelegramMother.TELEFONO))
                    .thenReturn(Mono.just(TelegramMother.ana()));

            atiende(TelegramMother.privado(TelegramMother.contactoPropio(" 573009990001 ")));

            verify(chats).vincular(TelegramMother.CHAT, TelegramMother.TELEFONO);
            verify(bot).enviar(TelegramMother.CHAT, RECIEN_VINCULADO);
            assertThat(logs.list).isNotEmpty().allSatisfy(evento ->
                    assertThat(evento.getFormattedMessage()).doesNotContain("9990001"));
        }

        @Test
        void unFalloAlResolverElChatPideReintentar() {
            envioPosible();
            when(chats.usuarioDelChat(TelegramMother.CHAT))
                    .thenReturn(Mono.error(new IllegalStateException("base caida")));

            atiende(TelegramMother.texto("hola"));

            verify(bot).enviar(TelegramMother.CHAT, REINTENTAR);
        }

        @Test
        void unFalloAlVincularPideReintentarSinElTelefonoEnElLog() {
            envioPosible();
            sinVincular();
            when(chats.vincular(anyLong(), anyString()))
                    .thenReturn(Mono.error(new IllegalStateException("base caida")));

            atiende(TelegramMother.privado(TelegramMother.contactoPropio(TelegramMother.TELEFONO)));

            verify(bot).enviar(TelegramMother.CHAT, REINTENTAR);
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

    @Nested
    class ConElAsistente {

        private void responde(RespuestaDelAsistente respuesta) {
            when(asistente.atender(any(), any(), any(), any())).thenReturn(Mono.just(respuesta));
        }

        @Test
        void unTextoLlegaAlAsistenteComoElUsuarioDelChatYSeEnviaSuAclaracionTalCual() {
            envioPosible();
            vinculado();
            responde(new RespuestaDelAsistente.Texto("En que cuenta?"));

            atiende(TelegramMother.texto("gaste 20 mil"));

            verify(asistente).atender(ANA, "gaste 20 mil", null, null);
            verify(bot).enviar(TelegramMother.CHAT, new RespuestaDelBot("En que cuenta?", TecladoDelBot.NINGUNO));
        }

        @Test
        void loRegistradoSaleConLosBotonesDelGrupo() {
            envioPosible();
            vinculado();
            RespuestaDelAsistente.Registro registro = new RespuestaDelAsistente.Registro(GRUPO,
                    List.of(TelegramMother.gasto("Almuerzo")));
            responde(registro);

            atiende(TelegramMother.texto("almuerzo 45 mil"));

            verify(bot).enviar(TelegramMother.CHAT, new RespuestaDelBot(
                    TextosDelBot.registro(registro, TelegramMother.ana().zona()),
                    new TecladoDelBot.ConfirmarODescartar(GRUPO)));
        }

        @Test
        void elSaldoSaleSinBotones() {
            envioPosible();
            vinculado();
            RespuestaDelAsistente.Saldo saldo = new RespuestaDelAsistente.Saldo(LocalDate.of(2026, 10, 1),
                    LocalDate.of(2026, 10, 31), "COP", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO,
                    BigDecimal.ZERO, List.of());
            responde(saldo);

            atiende(TelegramMother.texto("mi saldo"));

            verify(bot).enviar(TelegramMother.CHAT, new RespuestaDelBot(TextosDelBot.saldo(saldo),
                    TecladoDelBot.NINGUNO));
        }

        @Test
        void losMovimientosSalenEnLaZonaDelUsuario() {
            envioPosible();
            vinculado();
            RespuestaDelAsistente.Movimientos consulta = new RespuestaDelAsistente.Movimientos(
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "COP", BigDecimal.ZERO,
                    new BigDecimal("45000"), new BigDecimal("-45000"), List.of(TelegramMother.gasto("Almuerzo")));
            responde(consulta);

            atiende(TelegramMother.texto("mis gastos"));

            verify(bot).enviar(TelegramMother.CHAT, new RespuestaDelBot(
                    TextosDelBot.movimientos(consulta, TelegramMother.ana().zona()), TecladoDelBot.NINGUNO));
        }

        @Test
        void unaFotoSeDescargaYLlegaConSuPieComoImagenJpeg() {
            envioPosible();
            vinculado();
            when(bot.descargar("foto-grande")).thenReturn(Mono.just(BYTES));
            responde(new RespuestaDelAsistente.Texto("ok"));

            atiende(TelegramMother.conArchivo("el recibo", TelegramMother.foto()));

            verify(asistente).atender(ANA, "el recibo", "image/jpeg", BYTES);
        }

        @Test
        void unaNotaDeVozSinPieLlegaSinTexto() {
            envioPosible();
            vinculado();
            when(bot.descargar("voz-file-id")).thenReturn(Mono.just(BYTES));
            responde(new RespuestaDelAsistente.Texto("ok"));

            atiende(TelegramMother.conArchivo(null, TelegramMother.notaDeVoz()));

            verify(asistente).atender(ANA, null, "audio/ogg", BYTES);
        }

        /** Un video, un documento o un sticker llegan sin texto, archivo ni contacto. */
        @Test
        void unMensajeSinContenidoQueElBotEntiendaRecibeElTextoDeNoAdmitido() {
            envioPosible();
            vinculado();

            atiende(TelegramMother.privado(null));

            verify(bot).enviar(TelegramMother.CHAT, NO_ADMITIDO);
            verifyNoInteractions(asistente);
        }

        @Test
        void unArchivoQueTelegramDeclaraMasGrandeQueElTopeNoSeDescarga() {
            envioPosible();
            vinculado();

            atiende(TelegramMother.conArchivo(null, new ArchivoDeTelegram("voz-grande", "audio/ogg",
                    ArchivoDeTelegram.TOPE_ARCHIVO + 1L)));

            verify(bot).enviar(TelegramMother.CHAT, ARCHIVO_NO_ADMITIDO);
            verify(bot, never()).descargar(anyString());
            verifyNoInteractions(asistente);
        }

        @Test
        void unArchivoSinTamanoDeclaradoSeDescarga() {
            envioPosible();
            vinculado();
            when(bot.descargar("sin-tamano")).thenReturn(Mono.just(BYTES));
            responde(new RespuestaDelAsistente.Texto("ok"));

            atiende(TelegramMother.conArchivo(null, new ArchivoDeTelegram("sin-tamano", "audio/mpeg", null)));

            verify(asistente).atender(ANA, null, "audio/mpeg", BYTES);
        }

        @Test
        void unArchivoQueElAsistenteNoAdmiteRecibeElTextoDeArchivoNoAdmitido() {
            envioPosible();
            vinculado();
            when(bot.descargar("voz-file-id")).thenReturn(Mono.just(BYTES));
            responde(new RespuestaDelAsistente.ArchivoNoAdmitido());

            atiende(TelegramMother.conArchivo(null, TelegramMother.notaDeVoz()));

            verify(bot).enviar(TelegramMother.CHAT, ARCHIVO_NO_ADMITIDO);
        }

        @Test
        void unFalloDelModeloPideReintentarSinElTextoEnElLog() {
            envioPosible();
            vinculado();
            when(asistente.atender(any(), any(), any(), any()))
                    .thenReturn(Mono.error(new IllegalStateException("el modelo no respondio")));

            atiende(TelegramMother.texto("mi secreto 4455"));

            verify(bot).enviar(TelegramMother.CHAT, REINTENTAR);
            assertThat(logs.list).isNotEmpty().allSatisfy(evento ->
                    assertThat(evento.getFormattedMessage()).doesNotContain("4455"));
        }

        @Test
        void siTelegramNoEntregaElArchivoPideReintentar() {
            envioPosible();
            vinculado();
            when(bot.descargar("foto-grande")).thenReturn(Mono.error(new IllegalStateException("getFile 400")));

            atiende(TelegramMother.conArchivo(null, TelegramMother.foto()));

            verify(bot).enviar(TelegramMother.CHAT, REINTENTAR);
            verifyNoInteractions(asistente);
        }
    }

    @Nested
    class UpdateRepetido {

        @Test
        void unMensajeYaAtendidoNoSeProcesa() {
            when(updates.primeraVez(UPDATE)).thenReturn(Mono.just(false));

            atiende(TelegramMother.texto("gaste 20 mil"));

            verifyNoInteractions(bot, chats, asistente);
        }

        @Test
        void unBotonYaAtendidoNoSeProcesa() {
            when(updates.primeraVez(UPDATE)).thenReturn(Mono.just(false));

            atiende(TelegramMother.pulsar("confirmar:" + GRUPO));

            verifyNoInteractions(bot, chats, grupos);
        }

        @Test
        void siNoSePuedeRegistrarElUpdatePideReintentar() {
            envioPosible();
            when(updates.primeraVez(UPDATE)).thenReturn(Mono.error(new IllegalStateException("base caida")));

            atiende(TelegramMother.texto("gaste 20 mil"));

            verify(bot).enviar(TelegramMother.CHAT, REINTENTAR);
            verifyNoInteractions(chats, asistente);
        }
    }

    @Nested
    class Botones {

        private PublisherProbe<Void> cerrar;

        @BeforeEach
        void esperaCerrable() {
            cerrar = PublisherProbe.empty();
            lenient().when(bot.cerrarEspera("cb-1")).thenReturn(cerrar.mono());
            lenient().when(bot.quitarBotones(anyLong(), anyLong())).thenReturn(Mono.empty());
        }

        @Test
        void confirmarGuardaElGrupoQuitaLosBotonesYDiceCuantos() {
            envioPosible();
            vinculado();
            when(grupos.confirmar(ANA, GRUPO)).thenReturn(Mono.just(2));

            atiende(TelegramMother.pulsar("confirmar:" + GRUPO));

            cerrar.assertWasSubscribed();
            verify(bot).quitarBotones(TelegramMother.CHAT, 55L);
            verify(bot).enviar(TelegramMother.CHAT, new RespuestaDelBot("Listo, guardé 2 movimientos.",
                    TecladoDelBot.NINGUNO));
        }

        @Test
        void descartarBorraElGrupoQuitaLosBotonesYLoDice() {
            envioPosible();
            vinculado();
            when(grupos.descartar(ANA, GRUPO)).thenReturn(Mono.empty());

            atiende(TelegramMother.pulsar("descartar:" + GRUPO));

            verify(bot).quitarBotones(TelegramMother.CHAT, 55L);
            verify(bot).enviar(TelegramMother.CHAT, DESCARTADOS);
        }

        @Test
        void unGrupoQueYaNoEstaPendienteQuitaLosBotonesYLoDice() {
            envioPosible();
            vinculado();
            when(grupos.confirmar(ANA, GRUPO))
                    .thenReturn(Mono.error(new GrupoYaNoPendienteException(new IllegalStateException("409"))));

            atiende(TelegramMother.pulsar("confirmar:" + GRUPO));

            verify(bot).quitarBotones(TelegramMother.CHAT, 55L);
            verify(bot).enviar(TelegramMother.CHAT, YA_NO_PENDIENTE);
        }

        @Test
        void otroErrorAlGuardarDejaLosBotonesYDiceQueNoPudo() {
            envioPosible();
            vinculado();
            when(grupos.confirmar(ANA, GRUPO)).thenReturn(Mono.error(new IllegalStateException("cuenta archivada")));

            atiende(TelegramMother.pulsar("confirmar:" + GRUPO));

            verify(bot, never()).quitarBotones(anyLong(), anyLong());
            verify(bot).enviar(TelegramMother.CHAT, NO_GUARDADO);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "borrar:71000000-0000-7000-8000-000000000001", "confirmar:no-es-un-uuid",
                "confirmar:1-1-1-1-1", "confirmar:71000000-0000-7000-8000-000000000001:extra"})
        void unBotonConDatosDesconocidosSoloCierraLaEspera(String datos) {
            vinculado();

            atiende(TelegramMother.pulsar(datos));

            cerrar.assertWasSubscribed();
            verify(bot, never()).enviar(anyLong(), any());
            verifyNoInteractions(grupos);
        }

        @Test
        void unBotonSinSuMensajeSoloCierraLaEspera() {
            atiende(TelegramMother.pulsarSinMensaje("confirmar:" + GRUPO));

            cerrar.assertWasSubscribed();
            verify(bot, never()).enviar(anyLong(), any());
            verifyNoInteractions(chats, grupos);
        }

        @Test
        void unBotonDeUnGrupoRecibeQueSoloAtiendeChatsPrivados() {
            envioPosible();

            atiende(TelegramMother.pulsarEnGrupo("confirmar:" + GRUPO));

            verify(bot).enviar(TelegramMother.GRUPO, SOLO_PRIVADOS);
            verifyNoInteractions(chats, grupos);
        }

        @Test
        void unBotonDeUnChatQueYaNoEstaVinculadoNoCambiaElGrupo() {
            envioPosible();
            sinVincular();

            atiende(TelegramMother.pulsar("confirmar:" + GRUPO));

            verify(bot).enviar(TelegramMother.CHAT, SIN_VINCULAR);
            verifyNoInteractions(grupos);
        }

        @Test
        void unFalloAlCerrarLaEsperaNoImpideConfirmar() {
            envioPosible();
            vinculado();
            when(bot.cerrarEspera("cb-1")).thenReturn(Mono.error(new IllegalStateException("Telegram caido")));
            when(grupos.confirmar(ANA, GRUPO)).thenReturn(Mono.just(1));

            atiende(TelegramMother.pulsar("confirmar:" + GRUPO));

            verify(bot).enviar(TelegramMother.CHAT, new RespuestaDelBot("Listo, guardé 1 movimiento.",
                    TecladoDelBot.NINGUNO));
        }

        @Test
        void unFalloAlQuitarLosBotonesNoImpideElMensaje() {
            envioPosible();
            vinculado();
            when(bot.quitarBotones(anyLong(), anyLong())).thenReturn(Mono.error(new IllegalStateException("400")));
            when(grupos.descartar(ANA, GRUPO)).thenReturn(Mono.empty());

            atiende(TelegramMother.pulsar("descartar:" + GRUPO));

            verify(bot).enviar(TelegramMother.CHAT, DESCARTADOS);
        }

        @Test
        void unFalloAlLeerElChatDiceQueNoPudoGuardar() {
            envioPosible();
            when(chats.usuarioDelChat(TelegramMother.CHAT))
                    .thenReturn(Mono.error(new IllegalStateException("base caida")));

            atiende(TelegramMother.pulsar("confirmar:" + GRUPO));

            verify(bot).enviar(TelegramMother.CHAT, NO_GUARDADO);
            verifyNoInteractions(grupos);
        }

        @Test
        void siNoSePuedeRegistrarElUpdateDiceQueNoPudoGuardar() {
            envioPosible();
            when(updates.primeraVez(UPDATE)).thenReturn(Mono.error(new IllegalStateException("base caida")));

            atiende(TelegramMother.pulsar("confirmar:" + GRUPO));

            verify(bot).enviar(TelegramMother.CHAT, NO_GUARDADO);
            verifyNoInteractions(grupos);
        }

        @Test
        void siNoSePuedeRegistrarElUpdateDeUnBotonSinMensajeNoEnviaNada() {
            when(updates.primeraVez(UPDATE)).thenReturn(Mono.error(new IllegalStateException("base caida")));

            atiende(TelegramMother.pulsarSinMensaje("confirmar:" + GRUPO));

            verify(bot, never()).enviar(anyLong(), any());
        }
    }
}
