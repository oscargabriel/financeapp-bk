## Why

FA-100. Surgió en FA-77 (08-10-2026): desde ese cambio `application.yaml` lee `GEMINI_API_KEY` y
`GEMINI_MODEL` sin default, y el servicio `financeapp-bk-git` no tiene ninguna de las dos. El merge a
`dev` no despliega, pero el próximo push a `main` (FA-128) produciría una revisión que no arranca.

El modelo quedó sin decidir en FA-77. El usuario lo eligió al tomar esta tarea (10-10-2026):
**`gemini-3.5-flash-lite`**, el mismo con el que se hizo la prueba de humo contra Gemini real en
FA-101. Era la alternativa frente a `gemini-3.5-flash`, más capaz y más caro; el asistente solo
elige una de tres funciones y extrae sus argumentos, y con el lite eso ya funcionó. En local pasa a
`gemini-3.1-flash-lite`.

**La key es la misma que la de local**, por decisión del usuario al configurar el servicio
(10-10-2026), lo que contradice el criterio 2 de la tarea. Antes se comprobó que la key no aparece en
ningún commit: los yaml con credenciales y `bruno/.env` nunca se versionaron. El precio queda escrito
en *Rotar un secreto* de `docs/despliegue.md`: cuota compartida, uso indistinguible, y una filtración
o una revocación afectan a las dos.

## What Changes

- **En GCP, a mano y por el usuario** (este repo no versiona la configuración del servicio):
  - Secreto `gemini-api-key` en Secret Manager, con la misma key de Google AI Studio que usa local
    (ver arriba).
  - El servicio monta ese secreto como `GEMINI_API_KEY` (versión `latest`) y define
    `GEMINI_MODEL=gemini-3.5-flash-lite` en texto plano. Al actualizarlo, Cloud Run crea una
    revisión nueva con la imagen actual de `main`, que ignora las dos variables: no hay riesgo de
    caída antes del despliegue.
  - La cuenta del servicio ya tiene `secretAccessor` sobre todo el proyecto (FA-48): no hace falta
    tocar IAM.
- `docs/despliegue.md`:
  - Las filas de `gemini-api-key` y `GEMINI_MODEL` dejan de decir «Todavía no existe en el servicio»,
    y la de `GEMINI_MODEL` lleva el modelo elegido.
  - Se quita el aviso «Antes de promover FA-77 a `main`…», que deja de ser cierto.
  - *Cuenta de servicio* pasa de «sus ocho credenciales» a nueve.
  - *Rotar un secreto* agrega lo propio de `gemini-api-key`: la key se rota en AI Studio, se
    actualiza también en los yaml locales mientras sea compartida, y la vieja se borra allí después
    de comprobar la nueva.
- `AGENTS.md`: el comentario de `asistente.gemini.model` en el ejemplo del perfil local deja de
  decir que el modelo no está decidido y nombra los dos.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna. Es configuración del servicio y documentación: el comportamiento del asistente no cambia.
El change lleva `skip_specs: true`. La spec `asistente` ya exige leer las dos variables sin default.

## Cobertura de los criterios

| Criterio | Estado |
|---|---|
| 1. Modelo elegido por el usuario, anotado en `docs/despliegue.md` | Se implementa: `gemini-3.5-flash-lite` en la tabla de variables |
| 2. Secreto `gemini-api-key` montado como `GEMINI_API_KEY`, con key distinta de la local | Montado por el usuario. **La key es la misma que la local**, por decisión suya (10-10-2026); se confirma con `gcloud run services describe` |
| 3. `GEMINI_MODEL` como variable en texto plano del servicio | Igual que el 2 |
| 4. `docs/despliegue.md` deja de decir «Todavía no existe en el servicio» | Se implementa |
| 5. Tras el despliegue a `main`, `/api/status` 200 y un `POST /api/assistant/messages` real con `intent` que no es 502 | **Pasa a FA-128** por decisión del usuario (10-10-2026): FA-128 es la que despliega a `main`. Se agrega como criterio a esa tarea al cerrar esta |

## Impact

- `docs/despliegue.md` y `AGENTS.md`. Sin cambios en el código, `application.yaml`, el esquema,
  Bruno ni `bruno-personal/`.
- Servicio `financeapp-bk-git` y Secret Manager del proyecto de GCP: una revisión nueva del servicio
  con la imagen actual.

## Fuera de alcance

- Desplegar `dev` a `main` y la prueba real del asistente en producción: FA-128.
- Medir o comparar modelos.
