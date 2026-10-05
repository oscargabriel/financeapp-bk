# LoggingFilter.java

Ubicación: `src/main/java/{base-package}/infrastructure/config/logging/LoggingFilter.java`

```java
package {base-package}.infrastructure.config.logging;

import io.micrometer.common.lang.NonNullApi;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@NonNullApi
public class LoggingFilter implements WebFilter {

  private static final String REQUEST_ID = "requestId";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String requestId = UUID.randomUUID().toString();

    // Disponible para código imperativo (controllers, etc.)
    exchange.getAttributes().put(REQUEST_ID, requestId);

    // Disponible para el thread inicial
    MDC.put(REQUEST_ID, requestId);

    return chain.filter(exchange)
        .contextWrite(ctx -> ctx.put(REQUEST_ID, requestId))  // propaga al Reactor Context
        .doFinally(sig -> MDC.remove(REQUEST_ID));             // limpia al finalizar
  }
}
```

## Dependencias requeridas

Spring Boot Starter WebFlux (incluye SLF4J + Logback y Micrometer):

```xml
<!-- Maven -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

```groovy
// Gradle
implementation 'org.springframework.boot:spring-boot-starter-webflux'
```
