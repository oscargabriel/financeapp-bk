package com.oscargabriel.financeapp.ia.domain.model;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Lo que el asistente entiende de un archivo: los formatos que Gemini documenta para inlineData (design.md de FIN-57,
 * decision 3). El tipo declarado lo controla el cliente, por eso ademas tienen que coincidir los primeros bytes: un
 * video declarado image/jpeg no llega al modelo.
 */
public enum AssistantFileType {

    JPEG("image/jpeg", empiezaPor(0xFF, 0xD8, 0xFF)),
    PNG("image/png", empiezaPor(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)),
    WEBP("image/webp", riff("WEBP")),
    HEIC("image/heic", isoBmff()),
    HEIF("image/heif", isoBmff()),
    OGG("audio/ogg", empiezaPor("OggS")),
    MP3("audio/mpeg", empiezaPor("ID3").or(c -> c.length >= 2 && u(c[0]) == 0xFF && (u(c[1]) & 0xE0) == 0xE0)),
    WAV("audio/wav", riff("WAVE")),
    AAC("audio/aac", c -> c.length >= 2 && u(c[0]) == 0xFF && (u(c[1]) & 0xF6) == 0xF0),
    FLAC("audio/flac", empiezaPor("fLaC"));

    private static final Set<String> MARCAS_HEIF = Set.of("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1",
            "msf1");

    private final String mime;
    private final Predicate<byte[]> firma;

    AssistantFileType(String mime, Predicate<byte[]> firma) {
        this.mime = mime;
        this.firma = firma;
    }

    public String mime() {
        return mime;
    }

    /** Sin distinguir mayusculas y sin los parametros del tipo ("audio/ogg; codecs=opus" es audio/ogg). */
    public static Optional<AssistantFileType> de(String mimeDeclarado, byte[] contenido) {
        if (mimeDeclarado == null) {
            return Optional.empty();
        }
        String mime = mimeDeclarado.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(tipo -> tipo.mime.equals(mime) && tipo.firma.test(contenido))
                .findFirst();
    }

    private static Predicate<byte[]> empiezaPor(int... firma) {
        return c -> c.length >= firma.length && rangoIgual(c, 0, firma);
    }

    private static Predicate<byte[]> empiezaPor(String firma) {
        return empiezaPor(ascii(firma));
    }

    private static Predicate<byte[]> riff(String formato) {
        return c -> c.length >= 12 && rangoIgual(c, 0, ascii("RIFF")) && rangoIgual(c, 8, ascii(formato));
    }

    /** La caja ftyp de HEIC/HEIF: "ftyp" en el byte 4 y la marca en el 8. Un MP4 tiene otra marca. */
    private static Predicate<byte[]> isoBmff() {
        return c -> c.length >= 12 && rangoIgual(c, 4, ascii("ftyp"))
                && MARCAS_HEIF.contains(new String(c, 8, 4, StandardCharsets.ISO_8859_1));
    }

    private static boolean rangoIgual(byte[] contenido, int desde, int[] firma) {
        for (int i = 0; i < firma.length; i++) {
            if (u(contenido[desde + i]) != firma[i]) {
                return false;
            }
        }
        return true;
    }

    private static int[] ascii(String texto) {
        return texto.chars().toArray();
    }

    private static int u(byte b) {
        return b & 0xFF;
    }
}
