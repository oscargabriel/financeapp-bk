# Verificaciones manuales

Comprobaciones que ninguna de las dos capas de prueba puede hacer y que por eso hay que correr a
mano: porque exigen manipular el entorno de arranque y ocurren antes de que la aplicación acepte
tráfico, o porque dependen de un servicio externo que `bruno/` reemplaza por un stub.

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

## El asistente con audio e imagen contra Gemini

`bruno/assistant/` corre con el modelo stub, que solo lee el texto del mensaje: ejerce el
multipart, el formato del archivo y el 401, pero no lo que el modelo hace con una nota de voz o con
la foto de un recibo (FIN-57). Eso solo se ve contra Gemini. Lo que se detecta aquí es una
instrucción (`asistente/instrucciones.txt`) que el modelo deja de seguir, o un tipo de archivo que
Gemini rechaza.

**Correr esto al tocar `instrucciones.txt`, `GeminiAssistantAdapter`, el modelo
(`GEMINI_MODEL`) o los tipos de `AssistantFileType`.** Verificado por última vez el 14-10-2026 con
`gemini-3.1-flash-lite`.

### Los archivos

Se generan en `build/verificacion-fin-57/`, que git ignora: una nota de voz con la síntesis de voz
de Windows y un recibo dibujado como PNG. Se corre con `powershell.exe` (5.1), que trae
`System.Speech`:

```powershell
$d = "$PWD\build\verificacion-fin-57"; New-Item -ItemType Directory -Force $d | Out-Null
powershell.exe -NoProfile -Command @"
Add-Type -AssemblyName System.Speech
`$s = New-Object System.Speech.Synthesis.SpeechSynthesizer
`$v = `$s.GetInstalledVoices() | Where-Object { `$_.VoiceInfo.Culture.Name -like 'es*' } | Select-Object -First 1
if (`$v) { `$s.SelectVoice(`$v.VoiceInfo.Name) }
`$s.SetOutputToWaveFile('$d\nota.wav'); `$s.Speak('gasté veinte mil en almuerzo con la Nequi'); `$s.Dispose()
Add-Type -AssemblyName System.Drawing
`$b = New-Object System.Drawing.Bitmap 420, 560; `$g = [System.Drawing.Graphics]::FromImage(`$b)
`$g.Clear([System.Drawing.Color]::White); `$f = New-Object System.Drawing.Font 'Consolas', 14; `$y = 20
foreach (`$l in @('RESTAURANTE EL BUEN SABOR','NIT 900.123.456-7','Calle 10 # 5-20, Bogota','',
  'Fecha: 13/10/2026  Hora: 12:45','Factura No. 004512','----------------------------------',
  '1 Bandeja paisa        28.000','1 Limonada natural      6.000','1 Postre del dia        4.500',
  '----------------------------------','TOTAL A PAGAR    38.500','Medio de pago: TARJETA DEBITO',
  'Gracias por su compra')) {
  `$g.DrawString(`$l, `$f, [System.Drawing.Brushes]::Black, 20, `$y); `$y += 28 }
`$b.Save('$d\recibo.png', [System.Drawing.Imaging.ImageFormat]::Png); `$g.Dispose(); `$b.Dispose()
"@
```

El recibo dice a propósito «TARJETA DEBITO»: la cuenta no puede salir de ahí.

### La corrida

Levantar la app de la rama con Gemini real (el perfil `local` ya trae la clave), en un puerto
libre:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local'; $env:SERVER_PORT = '8082'; $env:ASISTENTE_PROVEEDOR = 'gemini'
.\gradlew.bat bootRun
```

Desde Git Bash, con el token de `dev@financeapp.local` (`POST /api/auth/login` con la credencial
Basic de `bruno/.env`), que tiene las cuentas `Nequi` y la categoría `Restaurantes`:

```bash
cd build/verificacion-fin-57; U=http://localhost:8082/api/assistant/messages
curl -s -w "\n%{time_total}s\n" -H "Authorization: Bearer $T" -F "file=@nota.wav;type=audio/wav" $U
curl -s -w "\n%{time_total}s\n" -H "Authorization: Bearer $T" -F "file=@recibo.png;type=image/png" $U
curl -s -w "\n%{time_total}s\n" -H "Authorization: Bearer $T" -F "file=@recibo.png;type=image/png" -F "data=con la Nequi" $U
```

Lo esperado, y lo que dio el 14-10-2026:

| Envío | Esperado | Resultado | Tiempo |
|---|---|---|---|
| Nota de voz | Lo mismo que el texto «gaste 20 mil en almuerzo con la Nequi»: un gasto pendiente de 20.000 en Nequi, categoría Restaurantes | `CREATE_TRANSACTION`, 20.000, Nequi, Restaurantes, «almuerzo». El texto da lo mismo en 3,2 s | 8,3 s |
| Recibo sin texto | `NEEDS_CLARIFICATION` pidiendo la cuenta; no la saca del «TARJETA DEBITO» | `NEEDS_CLARIFICATION`: «Falta la cuenta. Tus cuentas: …» | 10,4 s |
| Recibo con «con la Nequi» | Gasto pendiente con el total, la fecha y una descripción del recibo, en Nequi | `CREATE_TRANSACTION`, 38.500, 13-10-2026 a las 12:00, «Restaurante El Buen Sabor», Nequi, Restaurantes | 13,2 s |

Después, descartar los grupos creados (`POST /api/transactions/groups/{id}/reject`) y comprobar que
el log de la app no tiene el contenido de los archivos: buscar el texto del recibo y el comienzo de
su base64 (`iVBORw0`, y `UklGR` del WAV) no debe dar ninguna línea.

Los tiempos son lo que más hay que vigilar: el límite es `GEMINI_TIMEOUT` (20 s), y el recibo con
texto ya usa dos tercios. Si una corrida se acerca al límite, subir la variable en el servicio.
