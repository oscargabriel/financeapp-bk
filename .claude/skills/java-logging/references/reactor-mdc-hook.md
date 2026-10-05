# ReactorMdcHook.java

Ubicación: `src/main/java/{base-package}/infrastructure/config/logging/ReactorMdcHook.java`

Propósito: propagar el MDC de SLF4J entre hilos en pipelines reactivos de Reactor/WebFlux.
Sin este componente, el `requestId` (y cualquier otro valor del MDC) desaparece al cambiar de scheduler.

```java
package {base-package}.infrastructure.config.logging;

import io.micrometer.common.lang.NonNullApi;
import jakarta.annotation.PostConstruct;
import org.reactivestreams.Subscription;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;

import java.util.HashMap;
import java.util.Map;

@Component
@NonNullApi
public class ReactorMdcHook {

  @PostConstruct
  public void contextOperatorHook() {
    // Resetear primero para evitar hooks duplicados si el contexto se reinicia
    Hooks.resetOnEachOperator("mdc");
    // Instalar el hook: envuelve cada operador con MdcSubscriber
    Hooks.onEachOperator("mdc", Operators.lift((sc, actual) -> new MdcSubscriber<>(actual)));
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
    public reactor.util.context.Context currentContext() {
      return actual.currentContext();
    }

    private void copyMdcFromContext() {
      Map<String, String> contextMap = new HashMap<>();
      actual.currentContext().forEach((k, v) -> {
        if (k instanceof String && v != null) {
          contextMap.put((String) k, String.valueOf(v));
        }
      });

      if (!contextMap.isEmpty()) {
        MDC.setContextMap(contextMap);
      }
    }
  }
}
```

## Cómo funciona

1. `@PostConstruct` se ejecuta al arrancar Spring e instala el hook global de Reactor.
2. Por cada operador en cualquier pipeline (`map`, `flatMap`, `filter`, etc.), Reactor envuelve el suscriptor con `MdcSubscriber`.
3. En cada evento del pipeline (`onNext`, `onError`, `onComplete`, `onSubscribe`), `copyMdcFromContext()` lee el Reactor Context y copia los valores al MDC del hilo actual.
4. El bloque `finally` limpia el MDC para evitar que los valores se "filtren" hacia otras peticiones que reutilicen el mismo hilo.
