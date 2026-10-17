package com.oscargabriel.financeapp.ia.domain.model;

/** Por donde entro la peticion al asistente: lo fija quien llama, no el mensaje ni el modelo (FIN-112). */
public enum AssistantChannel {
    /** {@code POST /api/assistant/messages}: el chat de la web. */
    WEB_CHAT,
    /** El bot de Telegram (FIN-61). */
    TELEGRAM
}
