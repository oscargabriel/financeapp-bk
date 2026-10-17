# Design

## Context

FIN-57 dejó el multipart del asistente leído como `Flux<PartEvent>` por `LectorDeMensajeMultipart`,
que corta un archivo apenas pasa de 5 MB y rechaza una segunda parte `file`. Los tipos son el enum
`AssistantFileType`, cada uno con su firma, y `AssistantMessage(texto, archivo)` lleva un archivo o
ninguno. `GeminiAssistantAdapter` arma `contents[0].parts` con el texto y un `inlineData`, y el stub
no mira el archivo. El design de FIN-57 (`archive/2026-10-14-fin-57-asistente-audio-imagen`) tiene
el porqué de todo eso, y aquí no cambia.

El tiempo medido en FIN-57 con un recibo y texto fue de 13,2 s, frente a un `GEMINI_TIMEOUT` de
20 s. Con tres imágenes y un audio va a ser más.

## Goals / Non-Goals

**Goals:**
- Que el front mande en un solo multipart lo que el epic permite, con el contrato más simple
  posible para un `FormData` del navegador.
- Que un solo archivo y el JSON sigan iguales, byte a byte en la respuesta y en lo que recibe
  Gemini.

**Non-Goals:**
- Exponer los límites (FIN-114). Este change deja los valores donde FIN-114 los pueda leer, sin
  endpoint.
- Validar el contenido más allá de la firma: no se decodifica ningún audio ni imagen.

## Decisions

### 1. Varias partes con el mismo nombre `file`

Cada archivo va en su propia parte `file`, y el tipo de cada uno lo da su `Content-Type` con su
firma, como hoy. El front hace `formData.append("file", …)` una vez por archivo.

- **Descartado: partes con nombre por tipo** (`images` y `audio`). Obliga a que el cliente clasifique
  lo que el back ya clasifica por la firma, y un archivo en la parte equivocada sería un error más
  que describir. Además rompe al cliente de un solo `file`, que hoy manda un audio en `file`.
- **Descartado: nombres indexados** (`file[0]`, `file[1]`). No aportan nada que el orden de las
  partes no dé, y Spring no los trata distinto.

### 2. Topes: 5 MB por archivo, 10 MB entre todos

El tope por archivo no cambia. El total de 10 MB sale de dos cuentas:

- Gemini recibe todo en base64 dentro de una sola petición (decisión 5 de FIN-57): 10 MB son unos
  13,4 MB en base64, que con la instrucción caben holgados bajo los 20 MB de `inlineData` por
  petición con los que se diseñó FIN-57.
- Memoria por petición: FIN-57 ya aceptó hasta 5 MB más su base64 por petición con un solo usuario
  real. 10 MB lo duplica, sigue acotado y no depende de N.

Con N en 3 y archivos de 5 MB, el total corta antes que N; no importa, porque el front reduce las
imágenes (decisión del epic del 16-10-2026) y el proxy de Vercel no deja pasar más de 4,5 MB. El
total es la red del back, no el límite que el usuario va a ver.

- **Descartado: total configurable.** Lo fija el proveedor, no la prueba con el usuario, que es lo
  que el epic quiere ajustar. Si FIN-114 o una prueba lo piden, se vuelve propiedad.
- **Descartado: total proporcional a N** (`N × 5 MB + 5 MB`). Con N = 3 serían 20 MB, más de lo
  que cabe en base64 en una petición.

### 3. Los límites fijos en el dominio, N en el adapter de entrada

- `AssistantFile.TOPE_BYTES` (5 MB) sigue donde está.
- `AssistantMessage` pasa a `(String texto, List<AssistantFile> archivos)` y valida en su
  constructor lo que no depende de configuración: el total de 10 MB (`TOPE_TOTAL_BYTES`) y como
  mucho un audio. Igual que `AssistantFile`, lanza `IllegalArgumentException`: es una invariante,
  no la validación que ve el cliente.
- `AssistantFileType` gana `esAudio()`, por el prefijo `audio/` del MIME.
- N lo lee `AssistantController` con `@Value("${asistente.max-imagenes}")` (constructor explícito,
  como pide AGENTS.md para un `@Value`) y se lo pasa a `LectorDeMensajeMultipart.leer(partes, n)`.
  El constructor lanza `IllegalStateException` si N es menor que 1, para que el contexto no
  levante.

`LectorDeMensajeMultipart` hace la validación que ve el cliente, con su campo y su mensaje, antes de
construir el `AssistantMessage`. Las dos capas comprueban el total y el audio único; el dominio
nunca lanza porque el lector ya cortó.

- **Descartado: N en el dominio.** El dominio no lee configuración, y Telegram (FIN-61) manda un
  archivo por mensaje: N es del canal web.
- **Descartado: un bean de límites en `config/`.** Hoy solo lo usa el controlador. FIN-114 decide
  cómo los publica y, si necesita un bean, lo saca de aquí.

`application.yaml`: `asistente.max-imagenes: ${ASISTENTE_MAX_IMAGENES:3}`. El de test la replica
con 3. `CloudRunConfigTest` comprueba el default y que la variable lo cambie.

### 4. Orden de las comprobaciones en el lector

Mientras lee, por parte:

1. Un nombre que no es `data` ni `file`, o un segundo `data`: 400 como hoy.
2. La parte `file` número N + 2: 400 en `file` sin leer su contenido. Es el corte temprano: no tiene
   sentido leer 5 MB de una parte que ya sobra, sea imagen o audio.
3. El contenido de cada `file` se junta con un tope de `min(5 MB, lo que queda del total)`. Si pasa,
   400 en `file` con el mensaje del tope que se rompió (el del archivo o el total).

Al terminar, en orden: el texto (como hoy), cada archivo vacío o de tipo no resuelto, más de un
audio, más de N imágenes. El primer error gana y sale solo: el lector no acumula errores, igual que
hoy. Con más de un archivo, la descripción lleva el número del archivo que falló («Archivo 2: el
archivo tiene que ser…»), contado desde 1 en el orden de las partes. El campo sigue siendo `file`:
indexarlo (`file[1]`) cambiaría el error de un solo archivo, que el criterio pide dejar como está.

### 5. Las firmas de `audio/webm` y `audio/mp4`

| Tipo | Firma |
|---|---|
| `audio/webm` | `1A 45 DF A3` (EBML), el `DocType` `webm` en la cabecera EBML, y un `Segment` cuyo `Tracks` llega antes del primer `Cluster` sin ninguna pista con `TrackType` 1 (video) |
| `audio/mp4` | `ftyp` en el byte 4 con una marca que **no** es de HEIF, y ninguna caja `hdlr` con tipo `vide` |

- El video se busca porque `audio/mp4` y `video/mp4` son el mismo contenedor, y lo mismo pasa con
  WebM: sin esa comprobación, un video declarado audio llegaría a Gemini, y el epic dice «video no».
  En MP4 se busca `hdlr` + 8 bytes + `vide` en todo el archivo, porque `moov` puede ir al final. En
  WebM se recorren los elementos EBML del `Segment` (ID y tamaño de cada uno) hasta `Tracks`, y
  dentro de él cada `TrackEntry` con su `TrackType`. Buscar bytes no sirve en ningún sentido: `83 81
  01` en todo el archivo daría falsos positivos dentro de los datos Opus, y cortar en la primera
  aparición de `1F 43 B6 75` falla con lo que graba Chrome, cuyo `SeekHead` contiene el ID de
  `Cluster` antes de `Tracks` (visto en la tarea 1.1): el corte quedaba antes de las pistas y un video
  pasaba. Un `Cluster` antes de `Tracks`, o un `Segment` sin `Tracks`, se rechaza.
- La marca de MP4 se define por exclusión de las de HEIF y no por una lista de marcas de audio:
  qué marca escribe el `MediaRecorder` de Safari no está documentado, y una lista corta rechazaría
  grabaciones reales. La exclusión basta para que una HEIC declarada `audio/mp4` no pase.
- `audio/webm;codecs=opus` vale como `audio/webm`: `AssistantFileType.de` ya descarta los parámetros.
  A Gemini va el MIME sin parámetros.
- **Descartado: aceptar `video/webm` o `audio/x-m4a`.** El epic nombra `audio/webm` y `audio/mp4`,
  que son los que `MediaRecorder` declara para audio.

Gemini no lista `audio/webm` ni `audio/mp4` en la tabla de audio de la API para desarrolladores, pero
Vertex sí los documenta para el mismo modelo. Por eso el criterio pide verificarlo a mano, y eso va
antes que el resto de la implementación (tarea 1.1): si Gemini rechaza uno, se para y se decide con
el usuario.

### 6. Lo que recibe el modelo

`GeminiAssistantAdapter.partes` manda el texto, si lo hay, y después un `inlineData` por archivo, en
el orden de `archivos()`. Con un solo archivo, el cuerpo es el mismo de hoy. El stub sigue sin mirar
los archivos.

La regla 10 de `instrucciones.txt` pasa a decir que si el mensaje trae varias fotos de comprobantes,
cada uno es un movimiento aparte (una llamada a `crear_movimiento` por comprobante), y que el texto y
la nota de voz valen para todos. La regla 11 no cambia. Que eso funcione solo se ve contra Gemini.

### 7. Verificación manual con audio de navegador

En esta máquina no hay `ffmpeg`, y el WAV de la síntesis de Windows no sirve para probar WebM ni
MP4. `docs/verificaciones-manuales.md` suma un paso: una página `grabar.html` en
`build/verificacion-fin-113/` (ignorado por git), servida con `python -m http.server`, que carga la
`nota.wav` de FIN-57, la reproduce por Web Audio hacia un `MediaStreamDestination` y la graba con
`MediaRecorder` como `audio/webm;codecs=opus` y como `audio/mp4`. Chrome graba los dos tipos; si el
Chrome de la máquina no admite `audio/mp4`, se le pide al usuario una nota grabada en Safari.
También se dibujan tres recibos distintos con el mismo script de FIN-57.

Se mide el tiempo de tres recibos con un audio contra Gemini. Si pasa de 20 s, el valor nuevo de
`GEMINI_TIMEOUT` se anota en `docs/despliegue.md` y se le pide al usuario cambiarlo en el servicio:
el despliegue no toca variables.

## Risks / Trade-offs

- [Gemini rechaza `audio/webm` o `audio/mp4`] → sale 502 `EXTERNAL_SERVICE_ERROR`. La tarea 1.1 lo
  prueba antes de escribir código; si falla, el change se para y se decide con el usuario si el
  front convierte el audio o se cambia de proveedor.
- [Tiempo] Con cuatro archivos Gemini puede pasar de 20 s → se mide en la tarea final y se anota el
  valor nuevo. Mientras no se cambie en el servicio, el mensaje más pesado puede dar 502.
- [Memoria] Hasta 10 MB más su base64 por petición → aceptado con un solo usuario real, como en
  FIN-57. Si cambia, la concurrencia se limita en una tarea aparte.
- [`PartEventHttpMessageReader` con varias partes grandes] FIN-57 probó una de 5 MB contra el tope
  de 1 MB del codec → el slice prueba dos de 5 MB y el corte del total.
- [Falsos positivos de la firma] Un MP4 de audio con `hdlr…vide` en sus datos → improbable con una
  secuencia de 12 bytes; si aparece, se ve en la verificación manual con grabaciones reales. En WebM
  no aplica: se leen los elementos, no se buscan bytes.
- [Un error por vez] El front que mande cinco archivos mal recibe solo el primer error → el
  contrato lo dice; FIN-114 le da al front los límites para validar antes de enviar.
