package com.oscargabriel.financeapp.compartido.infrastructure.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationEntryPointFailureHandler;

import reactor.core.publisher.Mono;

/**
 * La autenticacion del webhook de Telegram (FIN-89). Telegram no puede presentar ni la Basic ni un
 * JWT: repite en cada update, en X-Telegram-Bot-Api-Secret-Token, el secret_token que se le dio al
 * registrar el webhook. Aqui se compara con el configurado, en tiempo constante.
 */
final class AutenticacionDelWebhook {

    static final String CABECERA = "X-Telegram-Bot-Api-Secret-Token";

    /**
     * El alfabeto y el maximo son los que Telegram acepta en setWebhook. El minimo de 32 es nuestro,
     * como el de JWT_SECRET: con menos, el secreto se adivina.
     */
    private static final Pattern FORMATO = Pattern.compile("[A-Za-z0-9_-]{32,256}");

    private static final String PRINCIPAL = "telegram";

    private AutenticacionDelWebhook() {
    }

    /** El mensaje no lleva el valor: se loguea al arrancar. */
    static String validado(String secreto) {
        if (secreto == null || !FORMATO.matcher(secreto).matches()) {
            throw new IllegalStateException("telegram.webhook-secret tiene que tener entre 32 y 256 caracteres"
                    + " de A-Z, a-z, 0-9, _ y -");
        }
        return secreto;
    }

    /**
     * Sin la cabecera el filtro deja pasar sin autenticar, y el 401 lo da la autorizacion. Con otro
     * valor, el fallo va al mismo entry point. La excepcion lleva un mensaje fijo, sin lo recibido,
     * porque el entry point la pasa al log.
     */
    static AuthenticationWebFilter filtro(String secreto, ServerAuthenticationEntryPoint entryPoint) {
        byte[] esperado = validado(secreto).getBytes(StandardCharsets.UTF_8);

        ReactiveAuthenticationManager comparador = autenticacion -> {
            byte[] recibido = String.valueOf(autenticacion.getCredentials()).getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(esperado, recibido)
                    ? Mono.just(UsernamePasswordAuthenticationToken.authenticated(PRINCIPAL, null,
                            List.of(new SimpleGrantedAuthority("ROLE_TELEGRAM"))))
                    : Mono.error(new BadCredentialsException("Secreto del webhook invalido"));
        };
        ServerAuthenticationConverter lector = exchange -> Mono
                .justOrEmpty(exchange.getRequest().getHeaders().getFirst(CABECERA))
                .map(valor -> UsernamePasswordAuthenticationToken.unauthenticated(PRINCIPAL, valor));

        AuthenticationWebFilter filtro = new AuthenticationWebFilter(comparador);
        filtro.setServerAuthenticationConverter(lector);
        filtro.setAuthenticationFailureHandler(new ServerAuthenticationEntryPointFailureHandler(entryPoint));
        return filtro;
    }
}
