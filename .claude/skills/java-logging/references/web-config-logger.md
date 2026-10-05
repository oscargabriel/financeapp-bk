# WebConfig.java — Bean requestLogger

Ubicación: `src/main/java/{base-package}/infrastructure/config/logging/WebConfig.java`

Si ya existe un `WebConfig.java` en el proyecto, agregar solo el bean `requestLogger` dentro de la clase `@Configuration` existente. No crear un archivo duplicado.

```java
package {base-package}.infrastructure.config.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebFilter;

@Configuration
public class WebConfig {
  private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

  @Bean
  public WebFilter requestLogger() {
    return (exchange, chain) -> {
      String path = exchange.getRequest().getPath().value();

      // Excluir health check del log (ajustar paths según proyecto)
      if (!path.contains("/status")) {
        log.debug(">> Request {} {}", exchange.getRequest().getMethod(), exchange.getRequest().getURI());
        return chain.filter(exchange)
            .doOnSuccess(done -> log.debug("<< Response {} for {}",
                exchange.getResponse().getStatusCode(),
                exchange.getRequest().getURI()));
      }

      return chain.filter(exchange);
    };
  }
}
```

## Notas

- El nivel `DEBUG` para request/response es intencional: en producción con `logging.level.root=INFO` estos logs no aparecen, evitando ruido. Se activan bajando el nivel a DEBUG cuando se necesita depurar.
- El `requestId` ya está en el MDC cuando este filtro se ejecuta (gracias a `LoggingFilter`), por lo que aparece automáticamente en los logs `>> Request` y `<< Response`.
- Para excluir más paths: `!path.contains("/status") && !path.contains("/actuator")`.
