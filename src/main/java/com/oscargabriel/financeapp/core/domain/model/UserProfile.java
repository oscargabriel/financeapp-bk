package com.oscargabriel.financeapp.core.domain.model;

import java.util.UUID;

/**
 * Lo que el usuario ve y edita de si mismo. Deliberadamente no es {@link User}: no lleva el hash, asi
 * que ninguna lectura del perfil puede devolverlo por descuido. {@code hasPassword} y
 * {@code googleLinked} dicen con que metodos entra (FIN-53); los calcula la consulta, sin traer el hash
 * ni el sub, y el parche no los cambia. {@code telegramLinked} dice si tiene un chat de Telegram vinculado
 * con su celular todavia admitido (FIN-60); tambien lo calcula la consulta, sin traer el id del chat.
 */
public record UserProfile(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phone,
        String baseCurrencyCode,
        String timezone,
        boolean hasPassword,
        boolean googleLinked,
        boolean telegramLinked) {
}
