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
(`GEMINI_MODEL`) o los tipos de `AssistantFileType`.** Verificado por última vez el 17-10-2026 con
`gemini-3.1-flash-lite`: un archivo el 14-10-2026 (FIN-57), y varios archivos y el audio del
navegador el 17-10-2026 (FIN-113, *Varios archivos y el audio del navegador*).

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

Los tiempos son lo que más hay que vigilar: el límite es `GEMINI_TIMEOUT` (20 s por defecto; 30 s
en el servicio desde FIN-113, ver `docs/despliegue.md`). Si una corrida se acerca al límite, subir
la variable en el servicio.

### Varios archivos y el audio del navegador

FIN-113 agregó varias partes `file` por mensaje y los tipos `audio/webm` y `audio/mp4`, los que
graba `MediaRecorder`. En la máquina no hay `ffmpeg`, y el WAV de la síntesis de Windows no sirve
como WebM ni MP4: las notas se graban en Chrome, reproduciendo el WAV por Web Audio hacia un
`MediaStreamDestination`. Todo va en `build/verificacion-fin-113/`, que git ignora.

**Los archivos.** Las notas «los dos con la Nequi» y «los tres con la Nequi» y tres recibos
distintos, con el mismo método que *Los archivos* de arriba. Se corre con `powershell.exe` (5.1),
guardado como `generar.ps1` fuera del repo y llamado con `-d <ruta absoluta de la carpeta>`:

```powershell
param([string]$d)
Add-Type -AssemblyName System.Speech
$s = New-Object System.Speech.Synthesis.SpeechSynthesizer
$v = $s.GetInstalledVoices() | Where-Object { $_.VoiceInfo.Culture.Name -like 'es*' } | Select-Object -First 1
if ($v) { $s.SelectVoice($v.VoiceInfo.Name) }
$s.SetOutputToWaveFile("$d\nota-dos.wav"); $s.Speak('los dos con la Nequi'); $s.SetOutputToNull()
$s.SetOutputToWaveFile("$d\nota-tres.wav"); $s.Speak('los tres con la Nequi'); $s.Dispose()
Add-Type -AssemblyName System.Drawing
$recibos = @(
  @('RESTAURANTE EL BUEN SABOR','NIT 900.123.456-7','Calle 10 # 5-20, Bogota','','Fecha: 13/10/2026  Hora: 12:45','Factura No. 004512','----------------------------------','1 Bandeja paisa        28.000','1 Limonada natural      6.000','1 Postre del dia        4.500','----------------------------------','TOTAL A PAGAR    38.500','Medio de pago: TARJETA DEBITO','Gracias por su compra'),
  @('DROGUERIA LA SALUD','NIT 800.456.789-1','Carrera 7 # 45-10, Bogota','','Fecha: 14/10/2026  Hora: 09:10','Factura No. 118230','----------------------------------','1 Acetaminofen 500mg    7.900','1 Vitamina C            15.600','----------------------------------','TOTAL A PAGAR    23.500','Medio de pago: TARJETA CREDITO','Gracias por su compra'),
  @('PARQUEADERO CENTRAL','NIT 901.222.333-4','Avenida 19 # 100-05, Bogota','','Fecha: 15/10/2026  Hora: 18:30','Recibo No. 77231','----------------------------------','Entrada 15:02  Salida 18:28','Tiempo: 3 h 26 min','----------------------------------','TOTAL A PAGAR    12.000','Medio de pago: EFECTIVO','Gracias por su visita')
)
for ($i = 0; $i -lt 3; $i++) {
  $b = New-Object System.Drawing.Bitmap 420, 560; $g = [System.Drawing.Graphics]::FromImage($b)
  $g.Clear([System.Drawing.Color]::White); $f = New-Object System.Drawing.Font 'Consolas', 14; $y = 20
  foreach ($l in $recibos[$i]) { $g.DrawString($l, $f, [System.Drawing.Brushes]::Black, 20, $y); $y += 28 }
  $b.Save("$d\recibo-$($i + 1).png", [System.Drawing.Imaging.ImageFormat]::Png); $g.Dispose(); $b.Dispose()
}
```

Ningún recibo dice Nequi: la cuenta sale del texto o de la nota. La `nota.wav` de FIN-57 se copia a
la carpeta para grabarla también.

**La grabación.** `grabar.html?nota=<nombre>` carga `<nombre>.wav` y lo graba como
`audio/webm;codecs=opus` y como `audio/mp4`, si el navegador los admite. Abierta a mano descarga
`<nombre>.webm` y `<nombre>.m4a`; con `&auto` graba sola y los sube por `PUT` a `servidor.py`, que
además sirve la carpeta:

```html
<!doctype html>
<html lang="es">
<meta charset="utf-8">
<title>Grabar nota FIN-113</title>
<button id="grabar">Grabar</button>
<pre id="estado"></pre>
<script>
const estado = document.getElementById('estado');
const log = t => { estado.textContent += t + '\n'; };
const nota = new URLSearchParams(location.search).get('nota') || 'nota';
const tipos = [['audio/webm;codecs=opus', nota + '.webm'], ['audio/mp4', nota + '.m4a']];
tipos.forEach(([t]) => log(t + ' soportado: ' + MediaRecorder.isTypeSupported(t)));

async function grabar(ctx, buffer, tipo, nombre) {
  const destino = ctx.createMediaStreamDestination();
  const fuente = ctx.createBufferSource();
  fuente.buffer = buffer;
  fuente.connect(destino);
  const rec = new MediaRecorder(destino.stream, { mimeType: tipo });
  const trozos = [];
  rec.ondataavailable = e => trozos.push(e.data);
  const fin = new Promise(r => rec.onstop = r);
  rec.start();
  fuente.start();
  await new Promise(r => fuente.onended = r);
  await new Promise(r => setTimeout(r, 300));
  rec.stop();
  await fin;
  const blob = new Blob(trozos, { type: rec.mimeType });
  const cabecera = [...new Uint8Array(await blob.slice(0, 12).arrayBuffer())]
    .map(b => b.toString(16).padStart(2, '0')).join(' ');
  log(nombre + ': ' + rec.mimeType + ', ' + blob.size + ' bytes, cabecera ' + cabecera);
  if (auto) {
    await fetch(nombre, { method: 'PUT', body: blob });
    return;
  }
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = nombre;
  a.click();
}

const auto = location.search.includes('auto');
const correr = async () => {
  try {
    const ctx = new AudioContext();
    await ctx.resume();
    const buffer = await ctx.decodeAudioData(await (await fetch(nota + '.wav')).arrayBuffer());
    for (const [tipo, nombre] of tipos) {
      if (MediaRecorder.isTypeSupported(tipo)) await grabar(ctx, buffer, tipo, nombre);
    }
    log('listo');
  } catch (e) {
    log('error: ' + e);
  }
  if (auto) await fetch('estado.txt', { method: 'PUT', body: estado.textContent });
};
document.getElementById('grabar').onclick = correr;
if (auto) correr();
</script>
</html>
```

```python
import functools
import http.server
import pathlib
import re
import sys

DIR = pathlib.Path(__file__).parent
PERMITIDOS = re.compile(r"^(nota[a-z-]*\.(webm|m4a)|estado\.txt)$")


class Handler(http.server.SimpleHTTPRequestHandler):
    def do_PUT(self):
        nombre = self.path.lstrip("/")
        if not PERMITIDOS.match(nombre):
            self.send_error(400)
            return
        largo = int(self.headers["Content-Length"])
        (DIR / nombre).write_bytes(self.rfile.read(largo))
        self.send_response(204)
        self.end_headers()


puerto = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
http.server.ThreadingHTTPServer(
    ("127.0.0.1", puerto), functools.partial(Handler, directory=str(DIR))
).serve_forever()
```

Con `python servidor.py 8765` corriendo en la carpeta, cada nota se graba con Chrome headless, que
no necesita la extensión ni un clic:

```powershell
$chrome = 'C:\Program Files\Google\Chrome\Application\chrome.exe'
foreach ($n in 'nota', 'nota-dos', 'nota-tres') {
  & $chrome --headless=new --autoplay-policy=no-user-gesture-required --user-data-dir="$env:TEMP\chrome-fin113-$n" `
    --no-first-run "http://127.0.0.1:8765/grabar.html?auto&nota=$n"
}
```

`estado.txt` dice qué grabó. Chrome admite los dos tipos; su `audio/mp4` es Opus en MP4 (marca
`isom`, declarado `audio/mp4;codecs=opus`), no el AAC de Safari. Si el Chrome de la máquina no admite
`audio/mp4`, la nota MP4 se pide grabada en Safari.

**La corrida.** La app y el token, como en *La corrida* de arriba. Desde Git Bash, en
`build/verificacion-fin-113`:

```bash
U=http://localhost:8082/api/assistant/messages; C=(-s -w "\n%{time_total}s\n" -H "Authorization: Bearer $T")
curl "${C[@]}" -F "file=@nota.webm;type=audio/webm" $U
curl "${C[@]}" -F "file=@nota.m4a;type=audio/mp4" $U
curl "${C[@]}" -F "data=con la Nequi" -F "file=@recibo-1.png;type=image/png" -F "file=@recibo-2.png;type=image/png" -F "file=@recibo-3.png;type=image/png" $U
curl "${C[@]}" -F "file=@recibo-1.png;type=image/png" -F "file=@recibo-2.png;type=image/png" -F "file=@nota-dos.webm;type=audio/webm" $U
curl "${C[@]}" -F "file=@recibo-1.png;type=image/png" -F "file=@recibo-2.png;type=image/png" -F "file=@recibo-3.png;type=image/png" -F "file=@nota-tres.webm;type=audio/webm" $U
```

Antes de la app, la tarea 1.1 de FIN-113 mandó `nota.webm` y `nota.m4a` directo a `generateContent`
con «transcribe el audio»: las dos dieron «Gasté 20.000 en almuerzo con la Nequi.» en tres corridas,
en 25,4, 9,2 y 14,8 s el WebM y 1,4, 9,3 y 9,8 s el MP4.

Lo esperado, y lo que dio el 17-10-2026 con `gemini-3.1-flash-lite`:

| Envío | Esperado | Resultado | Tiempos |
|---|---|---|---|
| `nota.webm` | Lo mismo que el texto: un gasto pendiente de 20.000 en Nequi, Restaurantes | `CREATE_TRANSACTION`, 20.000, Nequi, Restaurantes, «Almuerzo» | 1,4 s |
| `nota.m4a` | Lo mismo | `CREATE_TRANSACTION`, 20.000, Nequi, Restaurantes, «almuerzo» | 1,3 s |
| Tres recibos con «con la Nequi» | Un grupo con un gasto por recibo, cada uno con su monto, fecha y descripción, en Nequi | `CREATE_TRANSACTION`, un grupo: 38.500 el 13-10 «Restaurante El Buen Sabor» (Restaurantes), 23.500 el 14-10 «Drogueria La Salud» (Salud), 12.000 el 15-10 «Parqueadero Central» (Transporte), los tres en Nequi | 502 a los 18,3 s (Gemini respondió 503), 15,6 s, 3,7 s |
| Dos recibos y `nota-dos.webm`, sin texto | Un grupo con dos gastos en Nequi | `CREATE_TRANSACTION`, 38.500 y 23.500 en Nequi | 502 a los 20 s (`TimeoutException`), 2,9 s, 6,2 s |
| Tres recibos y `nota-tres.webm` | Un grupo con tres gastos en Nequi | `CREATE_TRANSACTION`, los mismos tres gastos que con el texto | 2,2 s, 9,0 s, 1,7 s |

Los resultados correctos fueron iguales en todas las corridas que respondieron. La fecha queda a las
12:00 de Bogotá, como en FIN-57. Una de nueve corridas con varios archivos llegó al timeout de 20 s:
por eso el servicio pasa a `GEMINI_TIMEOUT=30s`.

Después, descartar los grupos creados y buscar en el log de la app el texto de los recibos
(`RESTAURANTE EL BUEN SABOR`, `DROGUERIA LA SALUD`), el comienzo del base64 del PNG (`iVBORw0`), del
WebM (`GkXfo`) y del MP4 de Chrome (`AAAAJGZ0eXBpc29t`), e `inlineData`: ninguno debe dar una línea.
El 17-10-2026 no dio ninguna.

## El bot de Telegram con un bot real y Gemini

`bruno/telegram/` corre con dos stubs: el de Telegram, que solo deja las respuestas en el log y
entrega dos archivos fijos, y el del asistente, que solo lee el texto (FIN-61). Ninguno descarga de
la Bot API, dibuja los botones ni oye una nota de voz. Eso solo se ve con un bot real.

**Correr esto al tocar `TelegramBotApiAdapter`, `AtenderUpdateUseCase`, `TextosDelBot`, los puentes
`AsistenteDelBotAdapter` y `GruposDelBotAdapter`, o lo que se cita en *El asistente con audio e
imagen contra Gemini*.** Todavía no se ha corrido: FIN-61 cerró con la verificación de Bruno.

### Preparación

1. Un bot de prueba creado con @BotFather, distinto del de producción: su token va en
   `TELEGRAM_BOT_TOKEN`, y `TELEGRAM_WEBHOOK_SECRET` es un texto propio de 32 a 256 caracteres
   `A-Za-z0-9_-`.
2. La app de la rama con Telegram y Gemini reales, en un puerto libre:

   ```powershell
   $env:SPRING_PROFILES_ACTIVE = 'local'; $env:SERVER_PORT = '8082'
   $env:ASISTENTE_PROVEEDOR = 'gemini'; $env:TELEGRAM_PROVEEDOR = 'telegram'
   $env:TELEGRAM_BOT_TOKEN = '<token>'; $env:TELEGRAM_WEBHOOK_SECRET = '<secreto>'
   .\gradlew.bat bootRun
   ```

3. Un túnel HTTPS hacia el 8082 (Telegram solo entrega a HTTPS), por ejemplo
   `cloudflared tunnel --url http://localhost:8082`, y el webhook apuntando a él:

   ```bash
   curl -s "https://api.telegram.org/bot$TOKEN/setWebhook" \
     -d "url=https://<tunel>/api/telegram/webhook" -d "secret_token=$SECRETO"
   ```

4. `dev@financeapp.local` con el celular propio en E.164 como `phone` (`PATCH /api/users/me`) y ese
   número en la lista de admitidos:
   `psql -U postgres -d financeapp -c "INSERT INTO finance.registration_allowlist (entry) VALUES ('+57…')"`.
   Desde el celular, `/start` al bot y «Compartir mi número»: el bot responde que el chat quedó
   vinculado.

### La corrida

Con los archivos de *El asistente con audio e imagen contra Gemini* (`nota.wav` no sirve como nota
de voz: se graba una en el celular diciendo «gasté veinte mil en almuerzo con la Nequi», y el
`recibo.png` se manda como foto):

| Envío | Esperado |
|---|---|
| Nota de voz | El registro de un gasto de 20.000 en Nequi, categoría Restaurantes, con los botones Confirmar y Descartar. En la web aparece en Pendientes con origen Telegram |
| Confirmar | Los botones desaparecen, «Listo, guardé 1 movimiento.», y el saldo de Nequi bajó 20.000 |
| Confirmar otra vez, desde un mensaje viejo que conserve los botones | «Esos movimientos ya no están pendientes.» y el saldo no cambia |
| Foto del recibo con el pie «con la Nequi» | El registro de 38.500 con la fecha y la descripción del recibo |
| Descartar | Los botones desaparecen, «Descarté los movimientos. No se guardó nada.», y Pendientes queda sin ese gasto |
| «cuál es mi saldo» | Los totales del mes y una línea por cuenta |
| Un video o un sticker | «Solo entiendo texto, notas de voz, audios y fotos.» |

**El reenvío del mismo update.** Telegram solo reenvía un update que no recibió 2xx, así que el
reenvío se hace a mano: el mismo cuerpo dos veces contra el túnel, con un `update_id` que Telegram
no haya usado y el chat de `dev@`
(`SELECT telegram_chat_id FROM finance.users WHERE email = 'dev@financeapp.local'`):

```bash
B='{"update_id":990000001,"message":{"message_id":1,"date":1791590401,"chat":{"id":<chat>,"type":"private"},"text":"gasté 5 mil en tinto con la Nequi"}}'
for i in 1 2; do curl -s -o /dev/null -w "%{http_code}\n" -H "Content-Type: application/json" \
  -H "X-Telegram-Bot-Api-Secret-Token: $SECRETO" -d "$B" https://<tunel>/api/telegram/webhook; done
```

Las dos dan 200, pero llega un solo registro al chat y Pendientes gana un solo movimiento.

### Al terminar

`deleteWebhook` del bot de prueba, quitar el número de `finance.registration_allowlist` y descartar
en la web lo que haya quedado pendiente. En el log de la app, buscar el texto de la nota, el del
recibo, el token del bot y el número de celular: ninguno debe dar una línea.
