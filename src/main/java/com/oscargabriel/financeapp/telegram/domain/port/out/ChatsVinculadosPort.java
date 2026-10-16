package com.oscargabriel.financeapp.telegram.domain.port.out;

import com.oscargabriel.financeapp.telegram.domain.model.UsuarioDelBot;

import reactor.core.publisher.Mono;

/**
 * Que usuario tiene cada chat (FIN-60). Un chat cuenta como vinculado solo si su usuario sigue activo y
 * su phone sigue en la lista de admitidos.
 */
public interface ChatsVinculadosPort {

    /** Vacio si el chat no esta vinculado, o su usuario perdio el acceso. */
    Mono<UsuarioDelBot> usuarioDelChat(long chatId);

    /**
     * Vincula el chat al unico usuario activo cuyo phone es {@code telefono}, admitido, y lo quita de
     * cualquier otro. Vacio, sin tocar nada, si el telefono no esta admitido o no es de exactamente un
     * usuario activo.
     */
    Mono<UsuarioDelBot> vincular(long chatId, String telefono);
}
