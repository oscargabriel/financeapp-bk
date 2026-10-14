## 1. Contrato

Skills: ninguna de dominio (no toca código ni `bruno/`).

- [x] 1.1 En la sección `GET /api/status` de `docs/api/contrato-api.md`, reemplazar «Está pensado para monitoreo, no para el frontend» por el uso de monitoreo más el del front: lo llama al abrir el login para despertar el servicio y la base tras un arranque en frío, e ignora la respuesta, sea 200 o 503. Se verifica con `Select-String -Pattern 'no para el' docs/api/contrato-api.md` sin resultados y `git diff` limitado a esa sección — 0 coincidencias; `git diff --stat`: 3 líneas agregadas, 2 borradas, solo en esa sección

## 2. Verificación

- [x] 2.1 `.\gradlew.bat build` en verde, con el conteo real de tests — con `--rerun-tasks`: 1463 tests, 0 fallos, 0 errores, 0 omitidos
- [x] 2.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con los requests, tests y assertions reales, sin omitidos, y las cuentas cuadradas — 934 requests (934 Passed), 642/642 tests, 1825/1825 assertions; las 71 cuentas cuadran
