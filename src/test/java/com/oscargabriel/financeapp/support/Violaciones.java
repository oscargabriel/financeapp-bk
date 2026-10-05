package com.oscargabriel.financeapp.support;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * Valida un request con Bean Validation fuera de Spring y agrupa los mensajes por campo, que es como
 * los reporta el WebExceptionHandler. El orden de las violaciones no esta garantizado: por eso mapa.
 */
public final class Violaciones {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private Violaciones() {
    }

    public static Map<String, List<String>> de(Object request) {
        return VALIDATOR.validate(request).stream()
                .collect(Collectors.groupingBy(
                        (ConstraintViolation<Object> v) -> v.getPropertyPath().toString(),
                        TreeMap::new,
                        Collectors.mapping(ConstraintViolation::getMessage, Collectors.toList())));
    }
}
