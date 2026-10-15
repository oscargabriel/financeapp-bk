# Tasks

Sin skills de dominio: el change no toca código, seguridad, errores, logs ni `bruno/`.

## 1. Partir el contrato

- [x] 1.1 Escribir en el scratchpad el script que corta `docs/api/contrato-api.md` según el mapa de la decisión 1 de `design.md` y reescribe los enlaces que cruzan de archivo; correrlo y comprobar que `docs/api/` tiene los 14 archivos del mapa
- [x] 1.2 Reescribir `contrato-api.md` como índice (decisión 6): la tabla de endpoints con la columna del archivo, la lista de archivos y el párrafo de cabecera; verificar que no conserva ninguna sección `###`
- [x] 1.3 Escribir en el scratchpad el script de comprobación de la decisión 4 y correrlo: cada `###` del original (`git show dev:docs/api/contrato-api.md`) en exactamente un archivo, todos los enlaces internos de `docs/api/` resuelven, y el contenido reconstruido coincide con el original

## 2. Verificación

- [x] 2.1 `.\gradlew.bat build` en verde, con el conteo real de tests
- [x] 2.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con los conteos reales

## Workflow follow-up

- Archivar el change en la misma rama antes del PR.
- Al cerrar, avisar en las notas de implementación que la raíz tiene que ajustar la línea de
  `openspec/config.yaml` y los skills que nombran `contrato-api.md`, y que FIN-94 queda desbloqueada.
