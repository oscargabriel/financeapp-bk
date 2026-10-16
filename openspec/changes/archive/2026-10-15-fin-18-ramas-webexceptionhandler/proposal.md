# Proposal

Origen: FIN-18 (migrada de Notion, FA-41), epic FIN-74 «Calidad y mantenimiento».

Decisiones del epic que aplica:

- `AGENTS.md` guarda reglas, no historia: un change no le agrega claves ni el porqué, que va en su
  `design.md` (14-10-2026). El texto nuevo de la sección de cobertura dice la regla del umbral de
  rama, no de qué tarea salió.

## Why

`WebExceptionHandler` produce todas las respuestas de error del API, y es la clase con más ramas sin
ejercitar que no viene de un validador de dominio: cada rama sin test es un `code`, `description` o
`field` del contrato que nadie comprueba. Mientras siga así, el build no puede exigir un umbral de
rama, y la cobertura de rama puede bajar sin que nada avise.

Los números del issue son de FA-10 (62,22 % global, 3/15 ramas en el handler). Medidos el
15-10-2026 sobre `dev`:

| Qué | Rama | Línea |
|---|---|---|
| Global (clases medidas) | 1346/1449 — 92,89 % | 3256/3304 — 98,55 % |
| `WebExceptionHandler` | 25/34 — 73,53 % | 56/72 — 77,78 % |
| `BadRequestException` | 1/2 | 14/14 |

En el handler faltan los `case` de `NumberFormatException` e `IllegalArgumentException`, el error
global (no de campo) de `WebExchangeBindException`, `ServerWebInputException` sin `reason`,
`ResponseStatusException` con un status distinto de 404 y 413, tres ramas de `detallesDe` (error de
un elemento que no es de campo, parámetro que no es `@RequestBody`, error de campo sin índice) y la
respuesta de respaldo cuando falla la serialización. En `BadRequestException` falta la lista de
detalles vacía.

## What Changes

- Test unitario nuevo de `WebExceptionHandler` que ejercita cada `case` del switch, incluido el
  `default`, y cada rama de `detallesDe`, `prefijo` y del respaldo de serialización, comprobando
  status, `code`, `description` y `field`. Deja el handler en 34/34 ramas.
- Test unitario nuevo de `BadRequestException` para sus dos ramas (lista de detalles vacía y no vacía).
- La comprobación de que ningún `case` está dominado por su supertipo se apoya en el compilador
  (JLS 14.11.1: un patrón dominado es error de compilación) y en que cada `case` tenga un test que
  lo alcanza con su propio `code`. Ver `design.md`.
- Umbral de rama en `jacocoTestCoverageVerification`: **90 %** de `BRANCH` `COVEREDRATIO`, junto al
  85 % de línea. El número real que debería quedar es ~93,6 %. El valor lo acordó el usuario al
  aprobar este change, el 15-10-2026 (el issue pide «el valor acordado a partir del número real que
  quede»).
- `AGENTS.md`, sección *Cobertura*: deja de decir que no hay umbral de rama y dice cuál es.
  `openspec/config.yaml` (contexto, «cobertura de línea >= 85 %»), el skill `verificar` (formato
  del reporte) y `docs/despliegue.md` nombran también el umbral de rama.

No cambia ninguna respuesta del API: si un test revela un `case` que responde distinto de lo que el
código declara, se para y se corrige el change.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna: el change agrega tests y un umbral de build, sin cambiar comportamiento
(`skip_specs: true`).

## Fuera de alcance

- `SecurityConfig` (1/2 ramas): el issue lo menciona de paso, pero no está en sus criterios.
- Las demás clases con ramas sin cubrir (`AssistantFileType` 27/38, `ReglasDeSerie$Validador`
  29/36, `AssistantUseCase` 55/62, `LectorDeMensajeMultipart` 30/36…). El umbral global las
  incluye, pero subirlas es trabajo de cada módulo.
- Umbrales por clase o por paquete: el umbral es uno solo, global, como el de línea.

## Impact

- `src/test/java/com/oscargabriel/financeapp/compartido/infrastructure/adapter/in/web/WebExceptionHandlerTest.java` (nuevo).
- `src/test/java/com/oscargabriel/financeapp/compartido/domain/exceptions/BadRequestExceptionTest.java` (nuevo).
- `build.gradle`: regla de `BRANCH` en `jacocoTestCoverageVerification`. Un change futuro que baje
  la rama global del 90 % deja de construir, en local y en Cloud Build (la imagen corre `gradlew build`).
- `AGENTS.md`, `openspec/config.yaml`, `.claude/skills/verificar/SKILL.md` y `docs/despliegue.md`
  (el paso de Cloud Build que dice «la cobertura bajo el 85 %»).
- Sin cambios en código de producción, contrato del API, base de datos ni `bruno/`.
