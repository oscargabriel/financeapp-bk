package com.oscargabriel.financeapp.core.infrastructure.adapter.out.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.core.domain.port.out.RefreshTokenGeneratorPort;

/**
 * SHA-256 sin sal y no BCrypt: con 256 bits aleatorios no hay diccionario que probar, y el refresh
 * se busca por su hash, cosa que una sal impediria.
 */
@Component
public class RefreshTokenGeneratorAdapter implements RefreshTokenGeneratorPort {

    private static final int BYTES = 32;

    private final SecureRandom aleatorio = new SecureRandom();

    @Override
    public String generar() {
        byte[] valor = new byte[BYTES];
        aleatorio.nextBytes(valor);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(valor);
    }

    @Override
    public String hash(String refreshToken) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256")
                    .digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no trae SHA-256", e);
        }
    }
}
