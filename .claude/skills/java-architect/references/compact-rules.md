# Compact Rules — bloque inyectable para sub-agentes

Resumen autocontenido de las reglas de este stack (Java 25 + Spring Boot 4 + WebFlux + R2DBC,
arquitectura hexagonal). El orquestador lo extrae y lo incluye en
`## Project Standards (auto-resolved)` antes de delegar la generación de código.

**Reactivo**
- Los métodos devuelven `Mono<T>` o `Flux<T>`, no `void` ni tipos bloqueantes.
- No usar `.block()`, `subscribe()` dentro de pipelines, ni `new Thread()`.
- Composición: `flatMap` (async), `map` (sync), `switchIfEmpty`, `onErrorResume`.
- Errores como señal: `Mono.error(new DomainException(...))`, no `throw` dentro del pipeline.

**Repositorios (convención `buscar*` / `obtener*`)**
- `buscar*` devuelve `Mono.empty()` si no existe; el caller decide qué hacer.
- `obtener*` nunca completa vacío; lanza la excepción de dominio si no existe.
- El `switchIfEmpty(Mono.error(...))` va en el adapter (infrastructure), no en el use case.
- Los port interfaces son contratos puros: sin `default` methods con lógica de negocio.
- Javadoc en los `obtener*`: `/** Never completes empty; emits XxxException if not found. */`

**Inyección de dependencias**
- Lombok `@AllArgsConstructor` sobre campos `final`, en vez del constructor manual.
- Constructor explícito solo si lleva `@Value` (propiedades) o desambigua con `@Qualifier` varias
  implementaciones de una interfaz. En los demás casos es boilerplate evitable.

**Logging (`@Slf4j`)**
- `log.debug` para datos de entrada (request, id) y resultados de consulta; visible solo en
  ambientes de prueba (`DEBUG` en el perfil local, `INFO` en base/prod).
- `log.info` solo con datos no sensibles (ids, contadores). Nunca secretos (`password`,
  `password_hash`, tokens), tampoco en `debug`.

**Seguridad (OWASP)**
- Toda entrada externa se valida en el boundary: invariantes en el constructor compacto del
  VO/record, para que el objeto inválido no pueda existir.
- Queries R2DBC/`DatabaseClient` con `.bind(...)` o `Criteria`; nunca concatenar input en el SQL
  (A03).
- Los DTOs de entrada exponen solo campos que el cliente puede controlar; el request no se mapea
  directo a la entidad (mass assignment, A04).
- `WebClient`: base URL desde configuración; si el destino deriva de input, validar contra
  allowlist de hosts antes de llamar (A10). Timeouts siempre configurados.

**Construcción**
- Records/clases de datos (DTOs request/response/mensaje) con `@Builder`, construidos con
  `.builder().campo(...).build()`, no con el constructor posicional. Aplica también en tests.
- `@Builder` es aditivo sobre `record`: conserva el constructor canónico y la serialización.
  Excepciones: value objects con constructor compacto de validación y entidades con factory method.

**Funcional**
- Value objects: `record` con validación en constructor compacto.
- Inmutabilidad: `List.of()`, `Map.of()`, campos `final`, métodos que devuelven nueva instancia.
- `Stream` + `map/filter/collect` o `Flux.fromIterable` donde hoy habría un `for`/`while`.
- `orElseThrow()` en vez de `Optional.get()`.
- Sin mutación de estado externo dentro de streams o pipelines.
