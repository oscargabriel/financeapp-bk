package com.oscargabriel.financeapp.application.usecase;

import java.text.Normalizer;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** Busca un elemento del usuario por el nombre que escribio el modelo (design.md de FA-77, decision 3). */
final class ResolutorDeNombres<T> {

    sealed interface Resultado<T> {
    }

    record Unico<T>(T valor) implements Resultado<T> {
    }

    record Ninguno<T>() implements Resultado<T> {
    }

    record Varios<T>() implements Resultado<T> {
    }

    private final List<T> elementos;
    private final Function<T, String> nombre;

    ResolutorDeNombres(Collection<T> elementos, Function<T, String> nombre) {
        this.elementos = List.copyOf(elementos);
        this.nombre = nombre;
    }

    /** Sin coincidencias parciales: elegir «Tarjeta Débito» por «tarjeta» seria adivinar. */
    Resultado<T> buscar(String buscado) {
        if (buscado == null) {
            return new Ninguno<>();
        }
        String clave = normalizar(buscado);
        List<T> coincidencias = elementos.stream().filter(e -> normalizar(nombre.apply(e)).equals(clave)).toList();
        return switch (coincidencias.size()) {
            case 0 -> new Ninguno<>();
            case 1 -> new Unico<>(coincidencias.getFirst());
            default -> new Varios<>();
        };
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto.strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
