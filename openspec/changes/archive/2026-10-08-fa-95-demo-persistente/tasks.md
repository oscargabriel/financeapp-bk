# Tasks

Skills: ninguna de la tabla del paso 5 (no toca código Java ni `bruno/`).

## 1. Documentación

- [x] 1.1 `AGENTS.md`, sección *Base de datos*: párrafo sobre la demo persistente para el front
  (por qué, qué la borra y qué no, y que las fechas no avanzan con los meses, con la referencia a
  FA-94). Verificar: leer la sección completa y comprobar que no contradice la tabla de usuarios ni
  el párrafo de Neon.
- [x] 1.2 FA-94 en Notion: la nota del usuario en `## Contexto` y el criterio de renovar los meses
  sin borrar. Verificar: `notion-fetch` de FA-94.

## 2. Verificación

- [x] 2.1 `gradlew build` y `verificar-bruno.ps1 -RecargarDatos` en verde con los conteos reales,
  aunque el change solo toque documentación.
  Resultado: `gradlew build` con 637 tests, 0 fallos, 0 omitidos y cobertura de línea 98,24 %
  (1117/1137). Bruno: 257/257 requests, 218/218 tests, 583/583 aserciones.
- [x] 2.2 `openspec validate fa-95-demo-persistente --strict` en verde.
