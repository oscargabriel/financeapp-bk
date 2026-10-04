# OWASP Top 10 (2021) — contramedidas en Spring WebFlux

Checklist de endurecimiento para los microservicios del proyecto. Cada ítem indica la
contramedida concreta en el stack WebFlux + hexagonal.

## A01 — Broken Access Control

- **Deny by default**: la última regla del `authorizeExchange` es `.anyExchange().authenticated()`
  (o `.denyAll()` si aplica). Las rutas públicas se enumeran explícitamente ANTES.
- Orden importa: un `permitAll()` después de `authenticated()` queda sombreado — revisar siempre
  el orden de los `pathMatchers`.
- Roles granulares con `hasRole(...)` cuando existan permisos distintos; no todo bajo "USER".
- La autorización vive en `SecurityWebFilterChain`, no en `if` dentro de controllers.
- Todo endpoint protegido tiene test de 401 sin credenciales (ver skill `java-testing`).

## A02 — Cryptographic Failures

- Passwords siempre con `BCryptPasswordEncoder` (ya es el estándar del proyecto). Nunca MD5/SHA-1
  ni texto plano.
- Credenciales y secretos vía `@Value` desde variables de entorno / secret store — nunca
  hardcodeados ni en `application.properties` versionado (en OpenShift: `00-secrets.yaml`).
- TLS terminado en el router/ingress; no deshabilitar validación de certificados en `WebClient`
  (`insecure`/trust-all está prohibido, incluso en QA).

## A03 — Injection

- R2DBC/`DatabaseClient`: siempre `.bind(...)` — nunca concatenar input en el SQL.
- Query methods derivados o `@Query` con parámetros nombrados.
- Lo mismo aplica a filtros dinámicos: construir con `Criteria`, no con strings.
- PL/SQL: ver skill `oracle-ddl` (bind variables en `EXECUTE IMMEDIATE ... USING`, `DBMS_ASSERT`).

## A04 — Insecure Design

- Validación de invariantes en el constructor compacto del `record`/VO de dominio — el objeto
  inválido no puede existir.
- Los DTOs de entrada (`adapter/in/web/dto/`) exponen SOLO los campos que el cliente puede
  controlar — nunca mapear el request directo a la entidad (mass assignment).
- Límites explícitos: tamaño de página máximo en listados, tamaño de payload, timeouts en
  `WebClient`.

## A05 — Security Misconfiguration

- CORS: `cors.allowed-origins=*` solo en local/dev; en QA/prod, lista explícita de orígenes.
- CSRF deshabilitado es correcto SOLO para APIs stateless — documentarlo en el config.
- Respuestas de error sin stacktrace, SQL ni nombres de clase (ver skill `java-exceptions`).
- Cabeceras de seguridad con el spec `headers()` de `ServerHttpSecurity`:

```java
.headers(headers -> headers
    .frameOptions(frame -> frame.mode(Mode.DENY))
    .contentTypeOptions(withDefaults())          // X-Content-Type-Options: nosniff
    .hsts(hsts -> hsts.maxAge(Duration.ofDays(365)).includeSubdomains(true))
    .referrerPolicy(ref -> ref.policy(ReferrerPolicy.NO_REFERRER)))
```

- Swagger UI público solo en dev; en prod, protegido o deshabilitado.
- Actuator: exponer solo `health` (y lo mínimo necesario) — `management.endpoints.web.exposure.include=health`.

## A06 — Vulnerable and Outdated Components

- Versiones gestionadas por el BOM de Spring Boot; no fijar versiones sueltas sin razón.
- Al agregar una dependencia nueva: verificar CVEs conocidos y que esté mantenida.
- Escaneo con OWASP Dependency-Check bajo demanda: ver skill `verificar`.

## A07 — Identification and Authentication Failures

- HTTP Basic solo para APIs internas; APIs públicas/móviles → JWT.
- JWT: firma validada SIEMPRE (HS256 con secreto ≥256 bits desde env, o RS256), `exp` obligatorio
  y verificado, no aceptar `alg: none`.
- Mensajes de error de autenticación genéricos: nunca revelar si el usuario existe
  ("credenciales inválidas", no "usuario no encontrado").
- Considerar rate limiting en endpoints de autenticación (a nivel gateway/ingress si el
  servicio no lo implementa).

## A08 — Software and Data Integrity Failures

- Pipeline y despliegue: ver skill `azure-devops-deploy` (imagen base con tag fijo, secretos
  fuera del pipeline en claro).
- No deserializar objetos Java nativos de fuentes no confiables; JSON con Jackson y DTOs
  tipados (`@JsonIgnoreProperties(ignoreUnknown = true)` ya es el patrón del proyecto).

## A09 — Security Logging and Monitoring Failures

- Ver skill `java-logging`: nunca datos sensibles en logs/MDC; eventos de seguridad
  (401/403, login fallido) a WARN con `requestId`.

## A10 — Server-Side Request Forgery (SSRF)

- Si la URL de un `WebClient` deriva de input del usuario (o de datos externos): validar contra
  una allowlist de hosts/esquemas antes de llamar. Nunca `webClient.get().uri(urlDelRequest)`.
- Base URLs de integraciones siempre desde configuración (`application.properties`/ConfigMap),
  con el input aportando solo path/params validados.
