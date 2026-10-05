---
name: java-security
description: >
  Use when creating or modifying Spring Security configuration in a WebFlux project:
  SecurityWebFilterChain, ServerHttpSecurity, HTTP Basic Auth, JWT migration, CORS setup,
  or route authorization with pathMatchers.
  Skip for servlet-based (non-WebFlux) Spring Security configurations.
metadata:
  origen: skills-catalogo 2026-10-04
---

# Spring Security — WebFlux reactivo

Crear, analizar y modificar configuraciones de Spring Security para proyectos reactivos
(WebFlux, no servlet).

Identificar del contexto qué hace falta —crear la config, analizar la existente, migrar a JWT,
cambiar reglas de rutas, ajustar CORS o endurecer contra OWASP— y proceder. Preguntar solo cuando
dos lecturas del pedido lleven a trabajo distinto.

## Localizar la configuración actual

Antes de proponer cambios sobre una app existente, ubicar la clase anotada con
`@EnableWebFluxSecurity` en el proyecto (típicamente
`src/main/java/**/config/SecurityConfig.java`) y leerla. Si no existe ninguna, decirlo: la app
está sin cadena de filtros propia y la tarea pasa a ser crearla, no modificarla.

## Stack esperado

- Spring Boot 4 con WebFlux (reactivo)
- `@EnableWebFluxSecurity`, no `@EnableWebSecurity`
- `SecurityWebFilterChain` con `ServerHttpSecurity`, no `HttpSecurity`
- HTTP Basic con `MapReactiveUserDetailsService` y BCrypt, o JWT
- Credenciales inyectadas con `@Value` desde propiedades, cuyo valor real viene de variables de
  entorno o del secret del despliegue
- CORS desde la propiedad `cors.allowed-origins`

## Archivos de referencia

- `references/templates.md` — SecurityConfig completo con HTTP Basic, los 5 pasos de migración a
  JWT, CORS y reglas de rutas. Leer al generar o migrar configuración.
- `references/owasp-top10-webflux.md` — checklist OWASP Top 10 (2021) con la contramedida concreta
  en WebFlux. Leer al analizar o endurecer una aplicación.

## Analizar una configuración existente

Dimensiones a revisar:

- **Autenticación**: Basic es razonable para APIs internas; JWT para APIs públicas o móviles.
- **Deny by default (A01)**: la última regla debe ser `anyExchange().authenticated()` o
  `denyAll()`. Buscar `permitAll()` sombreados por una regla `authenticated()` anterior.
- **CORS**: `cors.allowed-origins=*` es aceptable en dev, no en producción.
- **Credenciales**: usuario/contraseña visibles en properties versionadas → mover a variables de
  entorno o secret.
- **Rutas públicas**: revisar si alguna no debería serlo.
- **CSRF**: deshabilitado es correcto para APIs REST stateless.
- **Roles**: si hay permisos distintos, un solo rol `USER` no alcanza.
- **Encoding**: BCrypt es el estándar recomendado.
- **Cabeceras de seguridad (A05)**: ¿configura el spec `headers()` (frameOptions, nosniff, HSTS)?
- **Mensajes de auth (A07)**: genéricos, sin revelar si el usuario existe.
- **Rate limiting (A07)**: en endpoints de autenticación; si no está ni en el servicio ni en el
  gateway/ingress, señalarlo.
- Contrastar el resto con `references/owasp-top10-webflux.md`.

Presentar los hallazgos en tres bloques: correcto / mejorable / problema real con impacto.

## Cobertura de tests

Un cambio en la cadena de filtros se rompe en el arranque del contexto o en runtime, no en
compilación. La cobertura mínima que lo detecta, con `WebTestClient`: un endpoint público responde
sin credenciales, y uno protegido responde 401 sin credenciales y 200 con ellas. En slices
`@WebFluxTest`, importar la config real (`@Import(SecurityConfig.class)`) — sin ella el slice
prueba rutas que en producción están protegidas de otra forma.

## Errores frecuentes en WebFlux

| Error | Por qué falla | Fix |
|---|---|---|
| `@EnableWebSecurity` | Stack equivocado: servlet vs. reactivo | `@EnableWebFluxSecurity` |
| `HttpSecurity` | La API servlet no está disponible en WebFlux | `ServerHttpSecurity` |
| `UserDetailsService` | No es reactivo: bloquea el event loop | `MapReactiveUserDetailsService` / `ReactiveUserDetailsService` |
| `addAllowedOrigin("*")` con `allowCredentials(true)` | Spring Security lo rechaza | `addAllowedOriginPattern("*")` |
| `WebSecurityConfigurerAdapter` | Removido desde Spring Boot 3 | Bean `SecurityWebFilterChain` |
| `permitAll()` después de un `authenticated()` que cubre la ruta | La primera regla que coincide gana; queda sombreado | Reordenar: lo específico antes que lo general |
