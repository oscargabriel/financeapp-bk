# Design

## Context

El porqué está en `proposal.md`. Hoy el texto entra por `AssistantMessageRequest` (`@Valid`, tope
1000) y llega como `String` a `AssistantPort.atender(userId, texto)`. De ahí pasa a
`AssistantModelPort.interpretar(texto, contexto)`, que implementan `GeminiAssistantAdapter`
(`generateContent`, una parte `text`) y `StubAssistantAdapter` (para `bruno/`, solo fuera de `prod`).

Restricciones que dan forma al diseño:

- `spring.codec.max-in-memory-size` es 1 MB para todo el API, y pasarlo da 413 `PAYLOAD_TOO_LARGE`.
- La tarea exige que el archivo no toque el disco. El lector de multipart que usa Spring por defecto
  (`DefaultPartHttpMessageReader`, el de `@RequestPart`/`FilePart`) escribe en un archivo temporal
  toda parte de archivo que pase su `maxInMemorySize`.
- El bot (FIN-61) va a pasarle al asistente lo que descargue de Telegram sin pasar por HTTP: el
  archivo tiene que existir en el puerto de entrada, no solo en el controlador.

## Goals / Non-Goals

**Goals:**

- Un solo camino desde el puerto de entrada hasta el modelo para texto, audio e imagen.
- Que un archivo inválido se rechace antes de leerlo entero, y nunca llegue al modelo.

**Non-Goals:**

- Transcribir el audio del lado del back: va al modelo tal cual (decisión de FIN-11).
- Un tope de memoria global para peticiones concurrentes con archivo (ver *Risks*).

## Decisions

### 1. Multipart en la misma ruta, con un método por tipo de contenido

`AssistantController` tiene dos métodos sobre `POST /messages`: el de hoy con
`consumes = application/json`, que no cambia, y uno nuevo con `consumes = multipart/form-data`. Los
dos construyen el mismo `AssistantMessage` y llaman al mismo puerto. Un `Content-Type` distinto de
esos dos sigue dando 415, como hoy.

- **Descartado: JSON con el archivo en base64** (decidido con el usuario). Obligaría a subir el tope
  global del codec, que hoy protege a todos los endpoints, y el base64 pesa un 33 % más.
- **Descartado: una ruta aparte** (`/assistant/files`). El mensaje es el mismo, con o sin archivo, y
  la respuesta también.

### 2. El multipart se lee como `Flux<PartEvent>`, contando bytes

El método multipart recibe `@RequestBody Flux<PartEvent>`. `PartEventHttpMessageReader` entrega el
contenido de cada parte de archivo en trozos y no escribe nada en disco. Un lector propio en
`adapter/in/web` (`LectorDeMensajeMultipart`) recorre las partes y hace esto:

- acumula `file` en memoria y corta con 400 apenas pasa de 5 MB, sin leer el resto;
- rechaza una segunda parte `file` y cualquier parte que no se llame `data` ni `file`;
- con `file` completo, resuelve el tipo (decisión 3);
- arma el `AssistantMessage` y aplica las reglas de `data`: opcional con archivo, obligatorio sin
  él, tope 1000, y en blanco junto a un archivo cuenta como ausente.

Cada error es una `BadRequestException` 400 `VALIDATION_ERROR` con su campo, emitida como señal del
`Mono`. No hace falta un `case` nuevo en `WebExceptionHandler`.

- **Descartado: `@RequestPart FilePart` con `spring.webflux.multipart.max-in-memory-size` por encima
  de 5 MB.** El reader por defecto bufferiza cada parte entera antes de que el controlador vea un
  byte. Además, el comportamiento del límite de disco en 0 no está documentado: dejar «no se escribe
  en disco» colgado de una propiedad es frágil.
- **Contradice a AGENTS.md** (*Dónde va cada validación de un cuerpo*), que pide validar el formato
  del cuerpo con Bean Validation en el record de request. Un `Flux<PartEvent>` no pasa por `@Valid`.
  El JSON sigue esa regla y el multipart la cumple en su lector. Una tarea del change agrega a
  AGENTS.md esta excepción.

### 3. Los tipos admitidos son un enum del dominio, con su firma

`ia/domain/model/AssistantFileType`: un valor por tipo MIME admitido, con la firma que lo reconoce
en los primeros bytes. El tipo declarado tiene que estar en el enum y su firma tiene que coincidir.
`AssistantFile(AssistantFileType tipo, byte[] contenido)` solo se construye con un tipo resuelto, así
que un archivo sin validar no llega al dominio. El tope de 5 MB vive en el mismo record.

| Tipo | Firma |
|---|---|
| `image/jpeg` | `FF D8 FF` |
| `image/png` | `89 50 4E 47 0D 0A 1A 0A` |
| `image/webp` | `RIFF` + 4 bytes + `WEBP` |
| `image/heic`, `image/heif` | `ftyp` en el byte 4, con marca `heic`, `heix`, `hevc`, `hevx`, `heim`, `heis`, `mif1` o `msf1` |
| `audio/ogg` | `OggS` |
| `audio/mpeg` | `ID3`, o sincronía de trama `FF Ex`/`FF Fx` |
| `audio/wav` | `RIFF` + 4 bytes + `WAVE` |
| `audio/aac` | ADTS: `FF F1` o `FF F9` |
| `audio/flac` | `fLaC` |

La lista sale de los formatos que Gemini documenta para `inlineData`. Telegram manda las notas de
voz en OGG/Opus y las fotos en JPEG.

- **Descartado: confiar en el `Content-Type` declarado.** Lo controla el cliente: un video declarado
  `image/jpeg` llegaría al modelo, y la tarea pide rechazarlo sin llamarlo.
- **Descartado: la lista en el controlador.** FIN-61 la necesita también, y vive en otro adapter de
  entrada.

### 4. El puerto recibe un mensaje, no un texto

```java
public record AssistantMessage(String texto, AssistantFile archivo) { }  // uno de los dos, o ambos
Mono<AssistantReply> atender(UUID userId, AssistantMessage mensaje);       // AssistantPort
Mono<AssistantDecision> interpretar(AssistantMessage mensaje, AssistantContext contexto); // AssistantModelPort
```

`texto` es `null` cuando no viene, nunca en blanco. El caso de uso no mira el archivo: lo pasa al
modelo, y lo que vuelve se resuelve igual que hoy. Por eso una imagen produce el mismo
`NEEDS_CLARIFICATION` que un texto sin cuenta.

### 5. Gemini recibe el archivo como `inlineData`, en el mismo turno

`contents[0].parts` lleva el texto, si lo hay, y después
`{"inlineData": {"mimeType": ..., "data": <base64>}}`. Sin texto, la parte de texto no se manda.

- **Descartado: la Files API de Gemini.** Guarda el archivo en Google 48 horas y suma una llamada.
  `inlineData` admite hasta 20 MB por petición, y 5 MB en base64 (≈ 6,7 MB) caben.

La instrucción (`instrucciones.txt`) suma tres reglas y amplía la 8:

- la nota de voz vale como si el usuario la hubiera escrito;
- de la foto de un recibo, factura o voucher se toman el monto total, la fecha y una descripción
  corta (el comercio o lo comprado), y si la imagen no es un comprobante no se llama ninguna función;
- la cuenta sale solo del texto o del audio, nunca de la imagen, aunque el comprobante muestre un
  medio de pago (criterio de la tarea);
- la regla 8 cubre también las instrucciones que traigan el audio o la imagen.

### 6. El stub usa solo el texto

`StubAssistantAdapter` interpreta `mensaje.texto()` como hoy y no mira el archivo. Un mensaje con
archivo y sin texto es una respuesta sin función (`UNSUPPORTED`). Con eso `bruno/` ejerce todo el
camino multipart: el formato, el 401 y que lo que diga el texto se registra igual que en JSON.

Lo que el modelo real hace con el audio y la imagen, es decir los tres escenarios «con el modelo
real» de la spec, no lo puede ejercer el stub. Va como procedimiento en
`docs/verificaciones-manuales.md`, con dos archivos generados en local: una nota de voz hecha con la
síntesis de voz de Windows y un recibo dibujado como PNG. Se corre una vez en este change con la
clave de Gemini del perfil local.

### 7. Privacidad del archivo

- **Disco**: decisiones 2 y 5. Nada del camino usa un archivo temporal ni la Files API.
- **Base**: el alta guarda los campos de siempre, porque el archivo no llega al core.
- **Log**: `GeminiAssistantAdapter` ya loguea solo el status y el tipo de excepción. El lector y el
  caso de uso no loguean el mensaje. `spring.codec.log-request-details` sigue en su default
  (`false`). Lo prueban dos tests con `ListAppender`: el 502 con imagen en `GeminiAssistantAdapterTest`
  y el rechazo de un archivo en `AssistantControllerTest`, sobre el log raíz, para que cubra también
  el handler global (el lector no tiene logger).

## Risks / Trade-offs

- [Memoria] Cada petición con archivo retiene hasta 5 MB de bytes y unos 6,7 MB de base64 mientras
  dura la llamada a Gemini. Con la concurrencia por defecto de Cloud Run (80), varias subidas a la
  vez pueden agotar la memoria de la instancia → la app tiene un solo usuario real hoy. Si eso
  cambia, se limita la concurrencia del endpoint o del servicio en una tarea aparte.
- [Tiempo] Un audio o una imagen pueden tardar más que un texto en Gemini, y el tiempo límite es
  `GEMINI_TIMEOUT` (20 s) → la verificación manual mide el tiempo real. Si se acerca al límite, se
  sube la variable en el servicio.
- [`PartEventHttpMessageReader`] Este change es el primero que lo usa en el repo → la primera tarea
  del controlador prueba, con el slice, que un archivo de 5 MB pasa a pesar del tope de 1 MB del
  codec y que uno de 5 MB + 1 se corta con 400.
- [Formatos] Gemini podría rechazar un tipo de la lista con un 400 → eso ya es 502
  `EXTERNAL_SERVICE_ERROR`. La verificación manual cubre WAV (lo que produce la síntesis de Windows;
  en esta máquina no hay `ffmpeg` para pasarlo a OGG) y PNG. El OGG de Telegram se ejerce en la
  verificación de FIN-61, que recibe notas de voz reales.
