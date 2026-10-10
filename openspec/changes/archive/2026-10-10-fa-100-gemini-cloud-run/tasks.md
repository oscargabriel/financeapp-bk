# Tasks

Sin skills de dominio: el change no toca código, `bruno/` ni la seguridad.

## 1. Servicio de Cloud Run (lo ejecuta el usuario)

- [x] 1.1 Elegir la API key de producción. El usuario reutiliza la de `application-local.yaml`
  (10-10-2026), tras comprobar que no aparece en ningún commit. La key no pasa por el chat ni por
  ningún archivo del repo.
- [x] 1.2 Crear el secreto. El valor va a un archivo temporal sin salto de línea, como en *Rotar un
  secreto*:

  ```powershell
  $tmp = New-TemporaryFile
  Set-Content -Path $tmp -Value '<key de AI Studio>' -NoNewline
  gcloud secrets create gemini-api-key --replication-policy=automatic --data-file=$tmp
  Remove-Item $tmp
  ```

  Verificar: `gcloud secrets versions list gemini-api-key` muestra la versión 1 `enabled`.
- [x] 1.3 Montarlo en el servicio junto con el modelo. Crea una revisión nueva con la imagen actual
  de `main`, que ignora las dos variables:

  ```powershell
  gcloud run services update financeapp-bk-git --region europe-west1 `
    --update-secrets=GEMINI_API_KEY=gemini-api-key:latest `
    --update-env-vars=GEMINI_MODEL=gemini-3.5-flash-lite
  ```

  Verificar: `gcloud run services describe financeapp-bk-git --region europe-west1 --format="yaml(spec.template.spec.containers[0].env)"`
  lista `GEMINI_API_KEY` con `secretKeyRef` `gemini-api-key` y `GEMINI_MODEL` con su valor, y
  `/api/status` con la Basic sigue respondiendo 200.

  Resultado (10-10-2026, `describe` corrido por el usuario): `GEMINI_MODEL=gemini-3.5-flash-lite` y
  `GEMINI_API_KEY` desde `gemini-api-key:latest`, junto a los ocho secretos anteriores. El 200 de
  `/api/status` no se comprobó aquí: queda en FA-128, que lo verifica con la imagen nueva.

## 2. Documentación

- [x] 2.1 `docs/despliegue.md`, *Secretos*: la fila de `gemini-api-key` sin «Todavía no existe en el
  servicio», y fuera el aviso «Antes de promover FA-77 a `main`…». Verificar: `rg "Todavía no existe"
  docs/despliegue.md` sin resultados.
- [x] 2.2 `docs/despliegue.md`, *Variables en texto plano*: `GEMINI_MODEL` = `gemini-3.5-flash-lite`
  con el porqué (FA-100). Verificar: leyendo la tabla.
- [x] 2.3 `docs/despliegue.md`: *Rotar un secreto* con lo propio de `gemini-api-key`, y *Cuenta de
  servicio* con nueve credenciales. Verificar: leyendo las dos secciones.
- [x] 2.4 `AGENTS.md`: el comentario de `model:` en el ejemplo del perfil local deja de decir que el
  modelo no está decidido. Verificar: `rg "no esta decidido" AGENTS.md` sin resultados.

## 3. Verificación

- [x] 3.1 `.\gradlew.bat build` en verde, con el conteo real de tests.
- [x] 3.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  los conteos reales.
- [x] 3.3 Al cerrar en Notion: agregar a FA-128 el criterio 5 de FA-100 (`/api/status` 200 y un
  `POST /api/assistant/messages` real con `intent` que no es 502 tras el despliegue).
