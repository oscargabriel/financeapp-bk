# Tasks

## 1. Dominio y puertos

Skills: `java-architect`.

- [x] 1.1 `ia/domain/model/AssistantFileType`, con un valor por tipo de la tabla de la decisión 3, su
  MIME y su firma, y `de(mime, bytes)` que devuelve el tipo solo si los dos coinciden. Verifica
  `AssistantFileTypeTest`: cada tipo con su firma mínima, un tipo fuera de la lista (`video/mp4`,
  `application/pdf`), un OGG declarado `image/jpeg` y bytes más cortos que la firma.
- [x] 1.2 `AssistantFile(tipo, contenido)` con el tope de 5 MB y copia defensiva del arreglo, y
  `AssistantMessage(texto, archivo)`: al menos uno de los dos, y `texto` nunca en blanco. Verifica
  `AssistantMessageTest`.
- [x] 1.3 `AssistantPort.atender(userId, AssistantMessage)` y
  `AssistantModelPort.interpretar(AssistantMessage, contexto)`. Ajustar el caso de uso, los dos
  adapters del modelo, el método JSON del controlador y `AsistenteMother`, sin cambiar el
  comportamiento. Verifica: los tests actuales de `ia` en verde, y en `AssistantUseCaseTest` uno nuevo
  de que el mensaje con archivo llega entero al modelo y lo que vuelve se resuelve igual que un texto.

## 2. Bruno en RED

Skills: `bruno-cli`.

- [x] 2.1 Archivos de prueba pequeños en `bruno/assistant/archivos/`: un PNG de 1×1, una cabecera
  OGG, una cabecera MP4 y un PDF mínimo. El de más de 5 MB no se versiona: ese caso se prueba en
  JUnit (3.2).
- [x] 2.2 Requests multipart en `bruno/assistant/`, con el stub, todos fallando antes de que exista
  el método multipart:
  - `archivo-con-texto.yml`: PNG + `data` con `crear_movimiento` → 200 `CREATE_TRANSACTION`, guarda
    el grupo; `archivo-descartar.yml` lo descarta (204) para no cambiar los totales de los demás
    requests;
  - `archivo-solo-texto.yml`: solo `data` → la misma respuesta que en JSON;
  - `archivo-sin-texto.yml`: solo el PNG → 200 `UNSUPPORTED` (el stub no mira el archivo);
  - `archivo-video.yml` (`video/mp4`), `archivo-pdf.yml`, `archivo-tipo-falso.yml` (OGG como
    `image/jpeg`) y `archivo-dos.yml` → 400 `VALIDATION_ERROR` en `file`;
  - `archivo-sin-nada.yml` (multipart con `data` en blanco y sin archivo) → 400 en `data`;
    `archivo-parte-desconocida.yml` (`foto`) → 400 en `foto`;
  - `archivo-sin-credenciales.yml` y `archivo-con-basic.yml` → 401 `UNAUTHENTICATED` en
    `authorization`.

  Verifica: `verificar-bruno.ps1 -RecargarDatos -Objetivo assistant` falla en esos requests (415
  donde hoy no hay método multipart) y en ningún otro.

## 3. Método multipart y su lector

Skills: `java-architect`, `java-exceptions`.

- [x] 3.1 `LectorDeMensajeMultipart` en `adapter/in/web` sobre `Flux<PartEvent>` (decisión 2): corte
  al pasar de 5 MB sin leer el resto, una sola parte `file`, partes desconocidas, reglas de `data` y
  resolución del tipo con `AssistantFileType`. Verifica `LectorDeMensajeMultipartTest`, un caso por
  escenario de *Formato del archivo* y *Formato del texto en multipart*.
- [x] 3.2 Método `consumes = multipart/form-data` en `AssistantController`. Verifica
  `AssistantControllerTest`: archivo + texto llega al puerto; un JPEG de exactamente 5 MB pasa a
  pesar del tope del codec; 5 MB + 1 da 400 en `file` sin llamar al puerto; los 401 sin token
  y con Basic; y un `ListAppender` sobre el log raíz comprueba que un archivo rechazado no deja su
  contenido en el log. El lector no tiene logger: la prueba va aquí, donde pasa también por el
  handler global. Después, `bruno/assistant/` en verde.

## 4. Gemini con el archivo

Skills: `java-architect`.

- [x] 4.1 `GeminiAssistantAdapter` manda `inlineData` con el MIME y el base64 en el mismo turno que el
  texto, y sin parte de texto si no lo hay. Verifica `GeminiAssistantAdapterTest`: imagen con texto
  (una sola petición, dos partes en orden), audio sin texto (una parte), y un 500 con imagen da 502
  sin el base64 en el log.
- [x] 4.2 `asistente/instrucciones.txt` con las reglas de la decisión 5. Verifica
  `GeminiAssistantAdapterTest`: la `systemInstruction` enviada contiene cada regla.
- [x] 4.3 `StubAssistantAdapter` usa solo `mensaje.texto()`; sin texto, sin función. Verifica
  `StubAssistantAdapterTest`.

## 5. Documentación

- [x] 5.1 `docs/api/contrato-api.md`, `POST /api/assistant/messages`: las dos formas del cuerpo, los
  tipos y el tope, el multipart con ejemplo, los errores en `file`, `data` y la parte desconocida, y
  que el archivo no se guarda. Verifica: cada escenario de la delta tiene su línea en el contrato.
- [x] 5.2 `AGENTS.md`, *Dónde va cada validación de un cuerpo*: el multipart se valida en su lector
  de `adapter/in/web`, porque `Flux<PartEvent>` no pasa por `@Valid` (decisión 2). Verifica: el
  párrafo nombra la excepción y el porqué.
- [x] 5.3 `docs/verificaciones-manuales.md`: procedimiento del asistente con el modelo real. Explica
  cómo generar la nota de voz (síntesis de voz de Windows a WAV) y el recibo (PNG dibujado con
  `System.Drawing`), los tres requests con `curl` y lo que se espera. Correrlo con la clave de Gemini
  del perfil local y anotar el resultado y el tiempo de cada llamada en el comentario de cierre.
  Verifica: los tres escenarios «con el modelo real» de la delta.
- [x] 5.4 `openspec/specs/asistente/spec.md`, `## Purpose`: el mensaje puede ser texto, una nota de
  voz o una imagen. Verifica: `openspec validate fin-57-asistente-audio-imagen` en verde.
- [x] 5.5 Replicar en `bruno-personal/` un request multipart de imagen con texto (ruta, cuerpo,
  autenticación), sin ejecutarlo. Verifica: comparado con `bruno/assistant/archivo-con-texto.yml`.

## 6. Verificación

- [x] 6.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea reales.
- [x] 6.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con el
  conteo real de requests y de assertions.

## Workflow follow-up

- Archivar el change en la rama (`/opsx:archive`), abrir el PR a `dev` y mergearlo con
  `merge-pr-dev.ps1`.
- Al cerrar, revisar si algún issue `Frontend` abierto usa `POST /api/assistant/messages`. El
  contrato cambió, y la copia `financeapp-fr/contrato-api.md` tiene la sección.
