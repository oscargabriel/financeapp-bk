---
name: java-logging
description: >
  Use when setting up or troubleshooting MDC/UUID traceability logging in a Spring WebFlux project:
  LoggingFilter, ReactorMdcHook, requestId propagation across reactive threads, log pattern
  configuration, or log level tuning per package.
  Skip for non-reactive (servlet-based) Spring projects.
metadata:
  origen: skills-catalogo 2026-10-04
---

# Java Logging — Trazabilidad con UUID en WebFlux Reactivo

Skill para implementar logging con propagación de `requestId` (UUID) en proyectos Spring Boot WebFlux.
El patrón garantiza que todos los logs de una misma petición HTTP comparten el mismo identificador,
incluso cuando las operaciones cruzan hilos en el pipeline reactivo.

## Por qué este patrón es necesario en WebFlux

En aplicaciones reactivas (Reactor/WebFlux) el MDC de SLF4J **no se propaga automáticamente**
entre hilos. Una petición puede ejecutarse en distintos schedulers (`boundedElastic`, `parallel`, etc.).
Sin `ReactorMdcHook`, el `requestId` desaparece en cuanto la ejecución salta de hilo.

## Archivos de referencia

- `references/logging-filter.md` — código completo de `LoggingFilter.java`
- `references/reactor-mdc-hook.md` — código completo de `ReactorMdcHook.java`
- `references/web-config-logger.md` — código completo del bean `requestLogger` en `WebConfig.java`
- `references/properties-config.md` — configuración de `application.properties`

---

## Paso 1 — Capturar intención

Identificar qué se pide antes de generar código:

- **Setup inicial**: crear el patrón completo desde cero en un proyecto nuevo.
- **Agregar campo al MDC**: añadir más contexto (ej. `userId`, `correlationId`) junto al `requestId`.
- **Cambiar niveles de log**: ajustar qué paquetes loguean a qué nivel.
- **Excluir endpoints del request logger**: no loguear ciertos paths (health checks, actuator).
- **Depurar propagación**: el `requestId` no aparece en los logs de algún servicio.

Si la intención no es clara, hacer una pregunta concisa antes de continuar.

---

## Paso 2 — Setup completo del patrón

Leer los 4 archivos de referencia. Crear los siguientes archivos en un subpaquete dedicado
`infrastructure/config/logging/`:

```
infrastructure/config/logging/
├── LoggingFilter.java      ← genera UUID y lo escribe en MDC + Reactor Context
├── ReactorMdcHook.java     ← propaga el MDC entre hilos del scheduler
└── WebConfig.java          ← bean requestLogger
```

Agrupar los tres componentes del patrón en un subpaquete propio (en lugar de sueltos en
`config/`) separa la trazabilidad de la config de seguridad/WebClient/properties y deja
la responsabilidad de logging localizada y fácil de descubrir.

### 2.1 LoggingFilter.java

Lee `references/logging-filter.md` para el código exacto. Puntos clave:
- Implementa `WebFilter` con `@Component`
- Genera `UUID.randomUUID().toString()` por petición
- Pone el `requestId` en `exchange.getAttributes()` (acceso imperativo) y en el Reactor Context via `.contextWrite()` (acceso reactivo)
- `doFinally` limpia MDC al terminar la petición

### 2.2 ReactorMdcHook.java

Lee `references/reactor-mdc-hook.md` para el código exacto. Puntos clave:
- `@PostConstruct` instala el hook con `Hooks.onEachOperator("mdc", ...)`
- `MdcSubscriber` copia el Context de Reactor al MDC en `onSubscribe`, `onNext`, `onError`, `onComplete`
- Bloque `finally` en cada evento para evitar leaks entre hilos
- `currentContext()` delega al suscriptor real para mantener la cadena de contexto

### 2.3 WebConfig.java (bean requestLogger)

Lee `references/web-config-logger.md` para el código exacto. Puntos clave:
- Bean `WebFilter requestLogger()` con nivel `log.debug`
- Excluye paths con `/status` (configurable)
- Loguea método y URI en entrada, status code y URI en salida

### 2.4 application.properties

Lee `references/properties-config.md`. Puntos clave:
- Patrón de consola con `[%X{requestId}]` para extraer el valor del MDC
- Niveles por paquete: root, springframework, mongodb driver, paquete de la app

---

## Paso 3 — Uso en servicios y handlers

No hace falta pasar el `requestId` manualmente. Declarar el logger y usarlo normalmente:

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MiServicio {
    private static final Logger log = LoggerFactory.getLogger(MiServicio.class);

    public Mono<Resultado> procesar(String id) {
        log.info("Procesando solicitud para id={}", id);  // requestId aparece automático
        return repositorio.buscar(id)
            .doOnNext(r -> log.debug("Resultado obtenido: {}", r))
            .doOnError(e -> log.error("Error procesando id={}", id, e));
    }
}
```

El `requestId` se inyecta en cada log vía MDC gracias a `LoggingFilter` + `ReactorMdcHook`.

---

## Paso 4 — Modificaciones frecuentes

### Agregar campo al MDC (ej. userId)

Modificar `LoggingFilter.java` — añadir el campo al context y al MDC inicial:

```java
// Dentro del método filter(), después de obtener requestId:
String userId = extraerUserIdDeToken(exchange); // tu lógica

exchange.getAttributes().put("userId", userId);
MDC.put("userId", userId);

return chain.filter(exchange)
    .contextWrite(ctx -> ctx
        .put(REQUEST_ID, requestId)
        .put("userId", userId))
    .doFinally(sig -> {
        MDC.remove(REQUEST_ID);
        MDC.remove("userId");
    });
```

Actualizar el patrón en `application.properties`:
```properties
logging.pattern.console=%d{dd-MM-yyyy HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%X{requestId}] [%X{userId}] - %msg%n
```

### Cambiar nivel de log por paquete

```properties
# Más detalle en tu paquete durante desarrollo
logging.level.mx.com.empresa.mipaquete=DEBUG

# Silenciar librería ruidosa
logging.level.io.netty=WARN
```

### Logging de seguridad (OWASP A09)

- NUNCA poner datos sensibles en el MDC ni en mensajes de log: passwords, tokens, headers de
  autorización, datos personales. Si un campo del MDC viene de input (ej. `userId`), registrar
  el identificador, no el contenido.
- Loguear eventos de seguridad a nivel `WARN` con el `requestId` del MDC: respuestas 401/403,
  intentos de login fallidos, tokens inválidos o expirados. Son la materia prima de la
  detección de ataques — sin ellos un intento de fuerza bruta es invisible.

```java
log.warn("Autenticación fallida para usuario={}", username); // el requestId sale del MDC
```

### Excluir más endpoints del request logger

Modificar la condición en `WebConfig.java`:

```java
if (!path.contains("/status") && !path.contains("/actuator") && !path.contains("/favicon")) {
    // loguear
}
```

---

## Paso 5 — Verificación

1. Levantar la aplicación y hacer dos peticiones distintas al mismo endpoint.
2. En la consola, verificar que cada petición tiene un `requestId` diferente y que **todos los logs de una misma petición comparten el mismo UUID**:
   ```
   14-03-2026 10:00:01.123 [reactor-http-nio-3] INFO  mx.com.MiServicio [a1b2c3d4-...] - Procesando solicitud
   14-03-2026 10:00:01.145 [boundedElastic-1]  DEBUG mx.com.MiRepo    [a1b2c3d4-...] - Consultando BD
   14-03-2026 10:00:01.200 [reactor-http-nio-3] INFO  mx.com.MiServicio [a1b2c3d4-...] - Respuesta enviada
   ```
3. Si el `requestId` aparece vacío `[]` en algún log, revisar que `ReactorMdcHook` está en el classpath de Spring y que `@PostConstruct` se ejecutó.
