package com.oscargabriel.financeapp.domain.model;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * Generador de UUID version 7 (RFC 9562): 48 bits de epoch en milisegundos seguidos de 74 bits
 * aleatorios. Los ids ordenan como el tiempo, asi que las inserciones caen al final del indice en
 * vez de dispersarse como haria un v4.
 *
 * Existe porque el JDK 25 no lo trae: java.util.UUID solo ofrece randomUUID (v4), nameUUIDFromBytes
 * (v3) y fromString.
 */
public final class UuidV7 {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    /** 48 bits para el epoch: hasta el ano 10889, cuando el campo se desborda. */
    private static final long MASCARA_EPOCH = 0xFFFF_FFFF_FFFFL;
    private static final long VERSION_7 = 0x7L << 12;
    private static final long VARIANTE_RFC = 0x8000_0000_0000_0000L;
    private static final long MASCARA_ALEATORIO_BAJO = 0x3FFF_FFFF_FFFF_FFFFL;

    private UuidV7() {
    }

    public static UUID from(Instant momento) {
        byte[] bytes = new byte[10];
        ALEATORIO.nextBytes(bytes);

        long alto = ((momento.toEpochMilli() & MASCARA_EPOCH) << 16)
                | VERSION_7
                | ((bytes[0] & 0x0FL) << 8)
                | (bytes[1] & 0xFFL);

        long bajo = 0L;
        for (int i = 2; i < bytes.length; i++) {
            bajo = (bajo << 8) | (bytes[i] & 0xFFL);
        }

        return new UUID(alto, (bajo & MASCARA_ALEATORIO_BAJO) | VARIANTE_RFC);
    }
}
