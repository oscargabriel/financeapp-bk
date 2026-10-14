## Why

FIN-85, del epic FIN-74 (Calidad y mantenimiento). Ninguna decisión del epic aplica: todas son del
flujo del tablero y de los skills, y este change toca el script de verificación. El epic no tiene
`## Fuera de alcance`.

Surgió en FIN-84. Desde FIN-58, `bru` descartaba dos requests de `bruno/assistant/` por inválidos
(«Warning: Skipping invalid file», `YAMLParseError`) y los contaba como «Skipped», pero salía con
código 0. `.claude/scripts/verificar-bruno.ps1` toma ese código como el resultado de la colección
(`$codigo = $LASTEXITCODE`), así que la verificación de varias entregas quedó en verde sin esos dos
escenarios.

Un request omitido es un escenario que no se verificó. Para el script, una corrida con omitidos no
puede quedar en verde.

## What Changes

- `verificar-bruno.ps1` le pide a `bru` el reporte JSON (`--reporter-json`) en
  `build/verificar-bruno/` y, terminada la corrida, busca los resultados con `skipped: true`. Ahí
  caen los archivos que `bru` no pudo leer y los requests que un script salte con
  `bru.runner.skipRequest`. Hoy la colección no salta ninguno a propósito.
- Si hay alguno, el script nombra cada uno con su ruta dentro de `bruno/` y el error que dio `bru`,
  y sale con el **código 5** cuando `bru` salió en 0. Si `bru` falló por otra cosa, conserva el
  código de `bru`, como ya pasa con el código 4.
- Si `bru` sale en 0 y el reporte no existe, el script también sale con 5: un verde sin poder
  comprobar los omitidos repetiría el problema que corrige este change.
- El código 5 queda documentado en el encabezado del script, en `AGENTS.md` (sección *Comandos*,
  junto al código 4 de los saldos) y en el skill `verificar`, que hoy dice que todo código distinto
  de 0, 2 y 3 viene de `bru`. Eso ya era falso con el 4, y el párrafo se corrige con los dos.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna. Es una herramienta de verificación y no cambia el comportamiento del API. El change lleva
`skip_specs: true`.

## Cobertura de los criterios

| Criterio de FIN-85 | Cómo se cumple |
|---|---|
| 1. Sale con un código distinto de 0 si `bru` omite archivos o reporta `Skipped`, y los nombra | Lectura del reporte JSON, código 5 y lista de omitidos (`tasks.md` 1.2) |
| 2. El código nuevo, documentado en el encabezado del script y en `AGENTS.md` | `tasks.md` 1.3, y además en `verificar` |
| 3. Verde con la colección actual, código nuevo con un request inválido de prueba | `tasks.md` 1.1 (RED) y 1.4 |
| 4. `gradlew build` y `verificar-bruno.ps1 -RecargarDatos` en verde | Grupo de verificación |

## Impact

- `.claude/scripts/verificar-bruno.ps1`, `AGENTS.md` y `.claude/skills/verificar/SKILL.md`.
- El reporte queda en `build/verificar-bruno/bru-resultados.json`, sin versionar, junto al
  `bootrun.log`. Va sin cabeceras ni cuerpos (`--reporter-skip-all-headers`,
  `--reporter-skip-body`) para no escribir en disco los JWT de la corrida. Esos flags solo cambian
  el archivo; la salida de `bru` en la consola no cambia.
- Ningún cambio de código Java, esquema, contrato ni `bruno/`.

## Fuera de alcance

- Detectar archivos inválidos sin levantar la app (un escaneo previo de la colección). `bru` ya los
  detecta al cargarla, y el script solo tiene que dejar de ignorarlos.
- `bruno-personal/`: no se corre con `bru run` ni pasa por este script.
