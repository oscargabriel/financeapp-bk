package com.oscargabriel.financeapp.infrastructure.config.logging;

import java.util.HashMap;
import java.util.Map;

import org.reactivestreams.Subscription;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

/**
 * En WebFlux el MDC de SLF4J no se propaga entre hilos: una peticion puede cambiar de scheduler
 * (boundedElastic, parallel) y el requestId desapareceria. Este hook copia el Reactor Context al
 * MDC en cada evento del pipeline.
 */
@Component
public class ReactorMdcHook {

    private static final String HOOK_KEY = "mdc";

    @PostConstruct
    public void contextOperatorHook() {
        // Reset primero: evita hooks duplicados si el contexto de Spring se reinicia.
        Hooks.resetOnEachOperator(HOOK_KEY);
        Hooks.onEachOperator(HOOK_KEY, Operators.lift((sc, actual) -> new MdcSubscriber<>(actual)));
    }

    static class MdcSubscriber<T> implements CoreSubscriber<T> {

        private final CoreSubscriber<? super T> actual;

        MdcSubscriber(CoreSubscriber<? super T> actual) {
            this.actual = actual;
        }

        @Override
        public void onSubscribe(Subscription s) {
            copyMdcFromContext();
            try {
                actual.onSubscribe(s);
            } finally {
                MDC.clear();
            }
        }

        @Override
        public void onNext(T t) {
            copyMdcFromContext();
            try {
                actual.onNext(t);
            } finally {
                MDC.clear();
            }
        }

        @Override
        public void onError(Throwable t) {
            copyMdcFromContext();
            try {
                actual.onError(t);
            } finally {
                MDC.clear();
            }
        }

        @Override
        public void onComplete() {
            copyMdcFromContext();
            try {
                actual.onComplete();
            } finally {
                MDC.clear();
            }
        }

        @Override
        public Context currentContext() {
            return actual.currentContext();
        }

        private void copyMdcFromContext() {
            Map<String, String> contextMap = new HashMap<>();
            actual.currentContext().forEach((k, v) -> {
                if (k instanceof String key && v != null) {
                    contextMap.put(key, String.valueOf(v));
                }
            });

            if (!contextMap.isEmpty()) {
                MDC.setContextMap(contextMap);
            }
        }
    }
}
