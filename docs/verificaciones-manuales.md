# Verificaciones manuales

Comprobaciones que ninguna de las dos capas de prueba puede hacer y que por eso hay que correr a
mano. No están en la suite de Gradle porque exigen manipular el entorno de arranque, y no están en
`bruno/` porque ocurren antes de que la aplicación acepte tráfico.

Cada una dice qué cambiar, qué comando correr y qué salida esperar. Si la salida no coincide, el
cableado se rompió.

## Arranque con `startup.db-check` activo

`DatabaseStartupCheck` hace `SELECT 1` contra PostgreSQL al terminar de instanciar los singletons,
antes de que Netty abra el puerto, y aborta el contexto si la base no responde tras agotar los
reintentos. Lo que se detecta aquí es un fallo de cableado que compila igual de bien: el
`@Qualifier("postgresHealthCheckAdapter")` del constructor, o la conversión a `Duration` de
`startup.db-check.initial-backoff` y `timeout`.

La suite no lo cubre: su R2DBC apunta a un puerto sin escucha a propósito y el chequeo está apagado
con `startup.db-check.enabled: false`. Encenderlo ahí exigiría una base real en los tests, que es
justo la decisión que se revirtió el 14-09-2026.

**Correr esto al tocar `DatabaseStartupCheck`, `PostgresHealthCheckAdapter` o las propiedades
`startup.db-check.*`.** Verificado por última vez el 14-09-2026 contra PostgreSQL 18 local.

### Camino 1 — la base responde: arranca limpio

`startup.db-check.enabled` ya vale `true` por defecto, así que basta con levantar la aplicación
contra el PostgreSQL local:

```powershell
.\gradlew.bat bootRun
```

Salida esperada — **el chequeo no dice nada cuando pasa**, su silencio es la señal:

```
... INFO  o.s.b.reactor.netty.NettyWebServer [] - Netty started on port 8080 (http)
... INFO  c.o.f.FinanceappBkApplication [] - Started FinanceappBkApplication in 3.374 seconds
```

Ni una línea de `DatabaseStartupCheck`. Si aparece un `WARN` de ese logger, la base tardó en
responder pero el arranque siguió adelante; si aparece un `ERROR`, no se llegó a arrancar y esto es
el camino 2.

### Camino 2 — la base no responde: aborta sin abrir el puerto

Apuntar el R2DBC a un puerto sin escucha. **Tiene que ser `SPRING_R2DBC_URL`, no `DB_PORT`**:
`application-local.yaml` trae la URL completa escrita a mano, así que bajo el perfil `local` la
variable `DB_PORT` del `application.yaml` base no se lee y el intento de usarla arranca contra la
base real sin avisar.

```powershell
$env:SPRING_R2DBC_URL = "r2dbc:postgresql://localhost:65535/financeapp"
.\gradlew.bat bootRun
Remove-Item Env:\SPRING_R2DBC_URL
```

Salida esperada — cuatro reintentos con backoff creciente y el quinto intento aborta, unos 13
segundos en total:

```
... WARN  c.o.f.i.c.s.DatabaseStartupCheck [] - Verificacion de arranque fallida (intento 1 de 5): org.springframework.dao.DataAccessResourceFailureException: Failed to obtain R2DBC Connection
... WARN  c.o.f.i.c.s.DatabaseStartupCheck [] - Verificacion de arranque fallida (intento 2 de 5): ...
... WARN  c.o.f.i.c.s.DatabaseStartupCheck [] - Verificacion de arranque fallida (intento 3 de 5): ...
... WARN  c.o.f.i.c.s.DatabaseStartupCheck [] - Verificacion de arranque fallida (intento 4 de 5): ...
... ERROR c.o.f.i.c.s.DatabaseStartupCheck [] - Arranque abortado: postgres no respondio tras 5 intentos
... WARN  o.s.b...ApplicationContext [] - Exception encountered during context initialization - cancelling refresh attempt: java.lang.IllegalStateException: Arranque abortado: el servicio postgres no respondio tras 5 intentos
BUILD FAILED
```

Lo que hay que comprobar, en este orden:

1. Son **cuatro** `WARN` numerados del 1 al 4 y luego el `ERROR`, no cinco warns. El quinto intento
   es el que falla definitivamente y sale por el `catch`.
2. Los tiempos entre warns crecen — aproximadamente 1s, 2s, 4s y 8s con jitter. Si salen los cinco
   seguidos sin pausa, `initial-backoff` no se está leyendo.
3. **No aparece `Netty started on port 8080`.** Es el punto de toda la verificación: el puerto no
   llega a abrirse, así que no hay ventana en la que la aplicación acepte tráfico sin base.
4. El `IllegalStateException` dice el servicio y el número de intentos, y **no arrastra la causa**:
   el detalle del driver se queda en el log y no sube al mensaje.

Un cambio de `max-attempts` cambia el conteo de los mensajes; el resto no debería moverse.
