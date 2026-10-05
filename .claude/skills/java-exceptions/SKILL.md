---
name: java-exceptions
description: >
  Use when creating or extending exception handling in a Spring WebFlux project: ErrorCodes enum,
  BadRequestException, WebExceptionHandler, structured ErrorResponse, or error propagation in
  reactive pipelines (onErrorMap, switchIfEmpty, Mono.error).
  Skip for non-WebFlux or servlet-based Spring projects.
metadata:
  origen: skills-catalogo 2026-10-04
---

# Manejo de excepciones — Spring WebFlux reactivo

Estructura estándar de manejo de errores para proyectos reactivos con arquitectura hexagonal. El
patrón produce la misma respuesta JSON de error en todos los endpoints.

Entregar lo pedido al alcance pedido: si se pide agregar un código de error, no se rehace la
estructura completa; si se pide la estructura, no se añaden códigos de dominio que nadie pidió.

Identificar del contexto qué hace falta (generar la estructura completa, agregar un `ErrorCode`,
extender el handler, crear una excepción nueva, o lanzar desde un pipeline). Preguntar solo si dos
lecturas del pedido llevarían a trabajo distinto.

## Formato de respuesta

```json
{
  "errors": [
    {
      "code": "VALIDATION_ERROR",
      "description": "El campo es requerido",
      "field": "nombreCampo"
    }
  ]
}
```

La lista permite devolver varios errores en una sola respuesta, útil cuando fallan varios campos
de validación a la vez.

## Ubicación en las capas

```
domain/exceptions/
├── ErrorCodes.java          ← enum de códigos — solo java.*, sin Spring
├── BadRequestException.java ← excepción de dominio (usa Spring HttpStatus — aceptable)
└── responses/
    ├── ErrorResponse.java   ← DTO raíz: List<ErrorDetail>
    └── ErrorDetail.java     ← DTO detalle: code + description + field

infrastructure/config/
└── ResourcesWebPropertiesConfig.java  ← @Bean WebProperties.Resources

infrastructure/adapter/in/web/
└── WebExceptionHandler.java ← AbstractErrorWebExceptionHandler
```

**`WebExceptionHandler` va en infrastructure** porque depende de `AbstractErrorWebExceptionHandler`,
`ServerResponse` y `RouterFunction`; en `domain/` rompería la regla de que el dominio no importa
frameworks.

**`ResourcesWebPropertiesConfig` también**, y es obligatorio: en Spring Boot 4,
`WebProperties.Resources` no se registra automáticamente como bean y
`AbstractErrorWebExceptionHandler` lo requiere por constructor. Sin él, Spring lanza
`NoSuchBeanDefinitionException` **al arrancar el contexto**, no al compilar — por eso conviene
levantar la app o correr un test de contexto tras tocar esta estructura.

## Generar la estructura completa

Recabar el paquete base (ej. `mx.com.miempresa.api`), el sub-paquete de infraestructura donde irá
el handler y los códigos de error propios del dominio. Generar los 6 archivos en este orden:
`ErrorDetail` → `ErrorResponse` → `ErrorCodes` → `BadRequestException` →
`ResourcesWebPropertiesConfig` → `WebExceptionHandler`, adaptados a esos paquetes.

Dependencia requerida (incluye `WebProperties` y WebFlux):

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

Y `@Order(Ordered.HIGHEST_PRECEDENCE)` en el handler, o el handler por defecto de Spring
intercepta primero.

## Agregar un ErrorCode

`ErrorCodes.java` es deliberadamente flexible: los códigos de `examples/` son un punto de partida,
no un contrato. Cada proyecto ajusta el enum a su dominio.

Nombrar en SCREAMING_SNAKE_CASE describiendo la **categoría** del error, no el mensaje ni el
detalle técnico:

```java
// Bien: categoría, específica del dominio
ORACLE_UNAVAILABLE("ORACLE_UNAVAILABLE"),
PAYMENT_DECLINED("PAYMENT_DECLINED"),
RATE_LIMIT_EXCEEDED("RATE_LIMIT_EXCEEDED"),

// Mal: vago o atado a un detalle de implementación
ERROR_123("ERROR_123"),
DB_CONN_TIMEOUT_ORACLE_57("DB_CONN_TIMEOUT_ORACLE_57"),
```

El argumento del constructor es lo que aparece en el campo `"code"` del JSON. `VALIDATION_ERROR` e
`INTERNAL_SERVER_ERROR` conviene conservarlos en cualquier proyecto.

## Lanzar errores

Constructores, guía de HTTP status, los cuatro patrones reactivos (`onErrorMap`, `switchIfEmpty`,
`flatMap` condicional, `onErrorMap` con predicado), cómo crear una excepción propia y cómo extender
el handler: `references/throwing-patterns.md`.

## No filtrar internals (OWASP A05)

El `ErrorResponse` que ve el cliente no incluye stacktraces, SQL, nombres de clase, versiones de
librerías ni rutas internas. La `description` habla en lenguaje de negocio; el detalle técnico va
al log, con la causa preservada vía el constructor de 5 argumentos. Los errores de autenticación
usan un mensaje genérico: distinguir "usuario no existe" de "contraseña incorrecta" permite
enumerar usuarios (A07).

## Errores comunes

| Error | Problema | Fix |
|---|---|---|
| `WebExceptionHandler` en `domain/` | Rompe la regla hexagonal: el dominio importaría WebFlux | Ponerlo en `infrastructure/adapter/in/web/` |
| `@ControllerAdvice` / `@ExceptionHandler` | Son servlet-only; los errores reactivos no pasan por el dispatcher servlet | `AbstractErrorWebExceptionHandler` |
| Falta el `@Bean WebProperties.Resources` | `NoSuchBeanDefinitionException` al arrancar | `ResourcesWebPropertiesConfig` en `infrastructure/config/` |
| Falta `@Order(Ordered.HIGHEST_PRECEDENCE)` | El handler por defecto de Spring intercepta primero | Añadir la anotación |
| `onErrorMap` sin pasar la causa | La raíz desaparece de los logs | Constructor de 5 argumentos: `..., description, field, e` |
| `throw` dentro de `flatMap`/`map`/`fromCallable` | Señal no manejada; puede romper el flujo en silencio | `Mono.error()` u `onErrorMap` |
| `field` vacío | Confuso para el cliente | Nombre con significado: `"parameter"`, `"body"`, `"server"` o el campo real |
| Falta `@Serial serialVersionUID` en los DTOs | Rompe algunos contextos de serialización | Mantenerlo en `ErrorDetail` y `ErrorResponse` |
| Stacktrace, SQL o nombres de clase en `description` | Filtra internals (A05) | Lenguaje de negocio; el detalle al log |

## Archivos de referencia

- `references/throwing-patterns.md` — constructores, HTTP status, patrones reactivos, excepción
  propia y extensión del handler.
- `examples/ErrorDetail.java`, `ErrorResponse.java`, `ErrorCodes.java`, `BadRequestException.java`,
  `ResourcesWebPropertiesConfig.java`, `WebExceptionHandler.java`, `ServiceUsageExample.java` —
  implementaciones listas para copiar.
