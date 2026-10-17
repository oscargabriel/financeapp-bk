package com.oscargabriel.financeapp.ia.domain.model;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * La firma de audio/webm (design.md de FIN-113, decision 5): cabecera EBML con DocType webm, y un Segment cuyo Tracks
 * llega antes del primer Cluster y no tiene ninguna pista de video. Se recorren los elementos en vez de buscar los
 * bytes del ID de Cluster, porque el SeekHead que escribe Chrome los contiene antes de Tracks.
 */
final class FirmaWebm {

    private static final long CABECERA = 0x1A45DFA3L;
    private static final long DOC_TYPE = 0x4282L;
    private static final long SEGMENTO = 0x18538067L;
    private static final long TRACKS = 0x1654AE6BL;
    private static final long TRACK_ENTRY = 0xAEL;
    private static final long TRACK_TYPE = 0x83L;
    private static final long CLUSTER = 0x1F43B675L;
    private static final long PISTA_DE_VIDEO = 1;

    private FirmaWebm() {
    }

    static boolean esWebmDeSoloAudio(byte[] c) {
        Elemento cabecera = elemento(c, 0, c.length);
        if (cabecera == null || cabecera.id() != CABECERA
                || hijos(c, cabecera).stream().noneMatch(e -> e.id() == DOC_TYPE && texto(c, e).equals("webm"))) {
            return false;
        }
        Elemento segmento = elemento(c, cabecera.fin(), c.length);
        if (segmento == null || segmento.id() != SEGMENTO) {
            return false;
        }
        for (Elemento hijo : hijos(c, segmento)) {
            if (hijo.id() == TRACKS) {
                return !tienePistaDeVideo(c, hijo);
            }
            if (hijo.id() == CLUSTER) {
                return false;
            }
        }
        return false;
    }

    private static boolean tienePistaDeVideo(byte[] c, Elemento tracks) {
        return hijos(c, tracks).stream()
                .filter(e -> e.id() == TRACK_ENTRY)
                .flatMap(e -> hijos(c, e).stream())
                .anyMatch(e -> e.id() == TRACK_TYPE && entero(c, e) == PISTA_DE_VIDEO);
    }

    private record Elemento(long id, int datos, int fin) {
    }

    private static List<Elemento> hijos(byte[] c, Elemento padre) {
        List<Elemento> hijos = new ArrayList<>();
        int posicion = padre.datos();
        Elemento hijo;
        while ((hijo = elemento(c, posicion, padre.fin())) != null) {
            hijos.add(hijo);
            posicion = hijo.fin();
        }
        return hijos;
    }

    /**
     * El elemento que empieza en desde: el ID conserva su marcador de largo, el tamano no. Un tamano desconocido (todos
     * sus bits en 1) o mayor que lo que queda llega hasta el limite. Null si no cabe.
     */
    private static Elemento elemento(byte[] c, int desde, int limite) {
        int largoId = largoVint(c, desde, limite);
        if (largoId == 0 || largoId > 4) {
            return null;
        }
        int posicionTamano = desde + largoId;
        int largoTamano = largoVint(c, posicionTamano, limite);
        if (largoTamano == 0) {
            return null;
        }
        long id = numero(c, desde, largoId);
        long tamano = numero(c, posicionTamano, largoTamano) & (-1L >>> (64 - 7 * largoTamano));
        int datos = posicionTamano + largoTamano;
        boolean desconocido = tamano == (1L << (7 * largoTamano)) - 1;
        int fin = desconocido || tamano > limite - datos ? limite : datos + (int) tamano;
        return new Elemento(id, datos, fin);
    }

    /** Cuantos bytes ocupa el vint que empieza en desde, por los ceros iniciales de su primer byte; 0 si no cabe. */
    private static int largoVint(byte[] c, int desde, int limite) {
        if (desde >= limite || c[desde] == 0) {
            return 0;
        }
        int largo = Integer.numberOfLeadingZeros(c[desde] & 0xFF) - 23;
        return desde + largo <= limite ? largo : 0;
    }

    private static long numero(byte[] c, int desde, int largo) {
        long valor = 0;
        for (int i = 0; i < largo; i++) {
            valor = (valor << 8) | (c[desde + i] & 0xFF);
        }
        return valor;
    }

    private static long entero(byte[] c, Elemento e) {
        int largo = e.fin() - e.datos();
        return largo >= 1 && largo <= 8 ? numero(c, e.datos(), largo) : -1;
    }

    private static String texto(byte[] c, Elemento e) {
        return new String(c, e.datos(), e.fin() - e.datos(), StandardCharsets.ISO_8859_1);
    }
}
