# Tasks

## 1. Gemini acepta el audio del navegador (antes de escribir código)

Sin skills de dominio: no toca código.

- [x] 1.1 Generar en `build/verificacion-fin-113/` la `nota.wav` con el script de FIN-57
  (`docs/verificaciones-manuales.md`, *Los archivos*), y una `grabar.html` que la carga, la reproduce
  por Web Audio hacia un `MediaStreamDestination` y la graba con `MediaRecorder` como
  `audio/webm;codecs=opus` y como `audio/mp4`, descargando `nota.webm` y `nota.m4a`. Servirla con
  `python -m http.server` y correrla en Chrome (design.md, decisión 7). Si el Chrome no admite
  `audio/mp4` (`MediaRecorder.isTypeSupported`), pedirle al usuario una nota grabada en Safari.
  Verificación: los dos archivos existen y empiezan por `1A 45 DF A3` y por `....ftyp`.
- [x] 1.2 Mandar cada nota a Gemini **directo**, sin la app (la app todavía las rechaza): `curl` a
  `generateContent` con la clave y el modelo de `application-local.yaml`, la nota en `inlineData`
  con `mimeType` `audio/webm` y `audio/mp4`, y la pregunta «transcribe el audio». Verificación: las
  dos respuestas transcriben la frase. **Si Gemini rechaza alguno, parar y decidir con el usuario**
  (design.md, *Risks*): el resto del change no sigue.

Resultado del grupo 1 (17-10-2026, `gemini-3.1-flash-lite`), para la tarea 5.3:

- La extensión de Chrome no estaba conectada, así que `grabar.html` corrió en Chrome headless
  (`--headless=new --autoplay-policy=no-user-gesture-required`, URL `grabar.html?auto`), servida por
  un `servidor.py` en la misma carpeta que, además de servir, recibe por `PUT` `nota.webm`,
  `nota.m4a` y `estado.txt`. Con `?auto` la página graba sola y sube los archivos en vez de
  descargarlos.
- Chrome admite los dos tipos. `nota.webm`: `audio/webm;codecs=opus`, 43 815 bytes, `1A 45 DF A3`.
  `nota.m4a`: Chrome la declara `audio/mp4;codecs=opus` (Opus en MP4, marca `isom`, no AAC como
  Safari), 44 310 bytes, `00 00 00 24 ftyp isom`.
- Gemini transcribió las dos como «Gasté 20.000 en almuerzo con la Nequi.» en tres corridas cada una.
  Tiempos de solo transcribir: WebM 25,4 s, 9,2 s y 14,8 s; MP4 1,4 s, 9,3 s y 9,8 s. El de 25 s
  pasa del `GEMINI_TIMEOUT` de 20 s: tenerlo presente en la medición de 5.2.

## 2. Un mensaje con varios archivos en el dominio

Skills: `java-architect` (cambia la forma de un record de dominio).

- [x] 2.1 `ia/domain/model/AssistantFileType`: valores `WEBM("audio/webm", …)` y `MP4("audio/mp4", …)`
  con las firmas de design.md, decisión 5 (EBML + `DocType` `webm` + sin `TrackType` 1 antes del
  primer `Cluster`; `ftyp` con marca fuera de `MARCAS_HEIF` + sin `hdlr`…`vide`), y `esAudio()`.
  Test primero en `AssistantFileTypeTest`: WebM de solo audio, `audio/webm;codecs=opus`, Matroska
  con `DocType` `matroska`, WebM con pista de video, MP4 de audio, MP4 con `hdlr vide`, HEIC
  declarada `audio/mp4`, y `esAudio()` de cada valor.
- [x] 2.2 `ia/domain/model/AssistantMessage`: pasa a `(String texto, List<AssistantFile> archivos)`,
  con `TOPE_TOTAL_BYTES = 10 * 1024 * 1024`; el constructor copia la lista (null es vacía), exige
  texto o algún archivo, rechaza más de un audio y más del total con `IllegalArgumentException`.
  `deTexto` queda. Test primero en `AssistantMessageTest`: sin nada, dos audios, 10 MB justos,
  10 MB + 1, la lista es una copia inmodificable.
- [x] 2.3 Ajustar a la firma nueva lo que ya compila contra `archivo()`, sin cambiar comportamiento:
  `LectorDeMensajeMultipart.mensaje` (construye con `List.of(archivo)`), `GeminiAssistantAdapter.partes`
  (lee `archivos()`), y los tests `AssistantUseCaseTest` (línea ~179), `StubAssistantAdapterTest`
  (~74, ~84), `GeminiAssistantAdapterTest` (~320, ~339, ~370), `AssistantControllerTest` (~304) y
  `LectorDeMensajeMultipartTest` (~69, ~80, ~90, ~111, ~121). Verificación: `gradlew test` en verde
  con los mismos conteos que antes del grupo más los tests nuevos.

## 3. Lo que recibe el modelo

Sin skills de dominio: el adapter ya existe y no cambia de forma.

- [x] 3.1 `GeminiAssistantAdapter.partes`: el texto, si lo hay, y un `inlineData` por archivo en el
  orden de `archivos()`. Test primero en `GeminiAssistantAdapterTest` (`ConArchivo`):
  `variasImagenesYUnAudioVanEnElMismoTurnoEnOrden` (dos imágenes, un audio y un texto: cuatro partes,
  cada una con su `mimeType`, una sola petición) y `unWebmVaConSuTipoSinParametros`. Los tests de un
  archivo siguen pasando sin cambios, que es lo que prueba que el cuerpo de un archivo es el de hoy.
- [x] 3.2 `src/main/resources/asistente/instrucciones.txt`, regla 10: con varias fotos de
  comprobantes, cada una es un movimiento aparte (una llamada a `crear_movimiento` por comprobante),
  y el texto y la nota de voz valen para todos. Test primero: ampliar
  `laInstruccionTrataLaVozComoTextoYNoSacaLaCuentaDeLaImagen` con esas dos frases.

## 4. El multipart con varios archivos

Skills: `java-architect` (el controlador pasa a constructor explícito por el `@Value`), `bruno-cli`.

- [x] 4.1 Bruno primero, fallando contra la app de hoy. En `bruno/assistant/archivos/`, tres archivos
  hechos a mano de unos cientos de bytes (con un script de Python que no se versiona): `nota.webm`
  (EBML, `DocType` `webm`, `Tracks` con `TrackType` 2), `nota.m4a` (`ftyp M4A ` y `hdlr soun`) y
  `video-con-pista.mp4` (`ftyp isom` y `hdlr vide`). Requests nuevos, después de `archivo-con-basic`
  (seq 80 en adelante), todos con `data` = `hola` cuando esperan 200, para que el stub responda
  `UNSUPPORTED` y no cree grupos que cambien los pendientes de la colección:
  `varias-imagenes-y-audio` (3 PNG + OGG → 200 `UNSUPPORTED`), `audio-webm` (`audio/webm;codecs=opus`
  → 200), `audio-mp4` (→ 200), `video-como-audio` (`video-con-pista.mp4` como `audio/mp4` → 400
  `file`), `dos-audios` (PNG + 2 OGG → 400 `file`), `no-admitido-entre-varios` (2 PNG + PDF → 400
  `file`), `dos-textos` (2 `data` → 400 `data`) y `varios-archivos-sin-credenciales` (2 PNG + OGG sin
  token → 401 `authorization`). `git mv archivo-dos.yml imagenes-de-mas.yml`, con cuatro PNG → 400
  `file`. Verificación: `verificar-bruno.ps1 -RecargarDatos -Objetivo assistant` falla en los 200 y
  en `video-como-audio`, y el resto pasa.
  Resultado: fallaron `varias-imagenes-y-audio` (400 por el segundo archivo) y
  `no-admitido-entre-varios` (sin «Archivo 3:»). `audio-webm` y `audio-mp4` ya pasaban porque la
  tarea 2.1 sumó los tipos al enum que usa el lector, y `video-como-audio` pasaba por lo mismo: eso
  confirmó que los archivos hechos a mano pasan y fallan la firma como se quería.
- [x] 4.2 `LectorDeMensajeMultipart.leer(Flux<PartEvent> partes, int maxImagenes)` con el orden de
  design.md, decisión 4: corta en la parte `file` número N + 2 sin leerla, junta cada archivo con
  tope `min(AssistantFile.TOPE_BYTES, lo que queda de AssistantMessage.TOPE_TOTAL_BYTES)` y dice cuál
  tope se rompió, y al final valida vacío y tipo de cada archivo, un solo audio y N imágenes, con
  «Archivo n: …» cuando hay más de uno. `mensaje(...)` arma la lista; `TIPO_NO_ADMITIDO` suma WEBM y
  MP4; la constante `"Solo se admite un archivo por mensaje"` desaparece. Test primero en
  `LectorDeMensajeMultipartTest`: `Validos` suma `nImagenes`, `unAudioYNImagenesEnOrden`,
  `dosDeCincoMegasJustoEnElTotal`, `conMaximoCincoAdmiteCinco`; `DelArchivo` cambia `dosArchivos`
  por `unaImagenDeMas`, y suma `dosAudios`, `porEncimaDelTotal`, `unoNoAdmitidoEntreVariosDiceCual`
  (descripción con «Archivo 3»), `noLeeLasPartesQueSobran` (como `dejaDeLeerAlPasarElTope`); `DelTexto`
  suma `dosTextos`.
- [x] 4.3 `AssistantController`: constructor explícito con `AssistantPort` y
  `@Value("${asistente.max-imagenes}") int maxImagenes`, que lanza `IllegalStateException` si es
  menor que 1; `messageConArchivo` pasa N al lector. Propiedad `asistente.max-imagenes:
  ${ASISTENTE_MAX_IMAGENES:3}` en `src/main/resources/application.yaml` y `max-imagenes: 3` en
  `src/test/resources/application.yaml`. Test primero en `AssistantControllerTest` (`ConArchivo`):
  `dosImagenesDeCincoMegasPasan`, `porEncimaDelTotalEsUn400SinLlamarAlAsistente`,
  `unaImagenDeMasEsUn400SinLlamarAlAsistente`, `variasSinTokenEsUn401`,
  `unMaximoMenorQueUnoNoDejaConstruirElControlador`; en `CloudRunConfigTest`,
  `elMaximoDeImagenesEsTresYLoCambiaLaVariable`. Verificación: los de 4.1 pasan con
  `verificar-bruno.ps1 -RecargarDatos -Objetivo assistant`.
- [x] 4.4 `docs/api/asistente.md`: la tabla del multipart con varias partes `file` (hasta N imágenes,
  N = 3 por defecto en `ASISTENTE_MAX_IMAGENES`, un audio, 5 MB cada uno y 10 MB en total), `audio/webm`
  y `audio/mp4` en la tabla de tipos, el ejemplo y el `curl` con varios `-F file=@…`, el párrafo de
  varios comprobantes, la viñeta que hoy dice que varios archivos y `audio/webm` son 400, y la fila
  de `file` en *Errores* («Archivo n: …», un error por vez). `docs/api/contrato-api.md`: comprobar
  que la fila del endpoint sigue valiendo. Verificación: `grep -n "webm\|varios archivos"
  docs/api/asistente.md` ya no dice que se rechacen.
- [x] 4.5 `docs/despliegue.md`: `ASISTENTE_MAX_IMAGENES` en la lista de variables con default, junto
  a `GEMINI_TIMEOUT`, diciendo que se cambia en el servicio sin desplegar.
- [x] 4.6 Replicar en `bruno-personal/asistente/` un `mensaje-con-varios-archivos.yml` con tres
  imágenes, un audio y un texto, como `mensaje-con-archivo.yml`, sin ejecutarlo.

## 5. Verificación manual contra Gemini

Sin skills de dominio.

- [x] 5.1 Con la app de la rama y Gemini real (procedimiento de FIN-57), mandar: `nota.webm` y
  `nota.m4a` de la tarea 1.1; tres recibos distintos dibujados con el script de FIN-57 y «con la
  Nequi»; dos recibos y una nota de voz que dice «los dos con la Nequi». Verificación: los escenarios
  «con el modelo real» del delta (un grupo con un gasto por recibo, la cuenta Nequi), el log sin el
  contenido de los archivos, y los grupos descartados al terminar.
- [x] 5.2 Medir el tiempo de tres recibos con una nota de voz. Si pasa de 20 s, anotar el
  `GEMINI_TIMEOUT` nuevo en `docs/despliegue.md` y pedirle al usuario cambiarlo en el servicio.
  Resultado: tres recibos con nota en 2,2, 9,0 y 1,7 s, pero dos recibos con nota llegaron una vez
  al timeout de 20 s y una nota WebM sola tardó 25 s en la tarea 1.2. Queda `GEMINI_TIMEOUT=30s`
  en la tabla de `docs/despliegue.md`, pendiente de que el usuario lo defina en el servicio.
- [x] 5.3 `docs/verificaciones-manuales.md`: la sección del asistente pasa a cubrir también varios
  archivos y el audio del navegador, con `grabar.html`, los envíos de 5.1, los resultados y tiempos
  medidos, y la fecha y el modelo.

## 6. Verificación

- [x] 6.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea y de rama.
  Resultado: 1785 tests, 0 fallos; línea 99,00 %, rama 93,28 %.
- [x] 6.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos`: `bru run . -r
  --env local` en verde, sin requests omitidos y `check-saldos.sql` sin descuadres, con los conteos.
  Resultado: 1052 requests, 696 tests y 2101 aserciones en verde, ninguno omitido; las 83 cuentas
  cuadran.

## Workflow follow-up

- Archivar el change en la misma rama, entregar con el PR a `dev` y pasar FIN-113 a `Por revisar`
  (`references/entregar.md` de `tareas-jira`).
