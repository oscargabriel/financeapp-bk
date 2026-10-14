## Why

FIN-84, del epic FIN-11 (Asistente y Telegram). Es un error de la colección, no del API: ninguna
decisión del epic aplica, y su `## Fuera de alcance` (video) no se toca.

Al verificar FIN-67, `bru run` reportó «2 Skipped»: Bruno descarta por inválidos
`bruno/assistant/confirmar-categoria-borrada.yml` y `bruno/assistant/grupo-sin-saldo-confirmar.yml`
(«YAMLParseError: Unexpected scalar at node end at line 26»). Los dos llevan en `runtime.assertions`
un `value:` sin comillas que empieza por `[` (`[0].categoryId`, `[1].amount`), y YAML lo abre como
una lista. Vienen de FIN-58 (commit c67078e) y desde entonces no se ejecutan, así que los escenarios
*Categoría borrada* y *Saldo que alcanza para cada uno pero no para los dos* del requisito
*Validaciones al confirmar un grupo* (`openspec/specs/transacciones/spec.md`) no tienen cobertura
en Bruno.

El resto de la colección ya escribe esos campos entre comillas (`value: "[0].categoryId"` en
`bruno/categories/gasto-con-categoria-borrada.yml`). Bruno evalúa el texto `[0].categoryId` como un
literal de plantilla y lo compara como cadena, así que basta con las comillas de YAML.

Un escaneo de las 904 YAML de `bruno/` con el mismo parser que usa `bru` (paquete `yaml` de
`@usebruno/cli`) encuentra solo esos dos archivos con error, y ninguna assertion cuyo valor salga
como lista u objeto.

## What Changes

- `bruno/assistant/confirmar-categoria-borrada.yml`: `value: "[0].categoryId"`. Verifica 409, un
  solo error, `INVALID_STATE` y `field` `[0].categoryId`, como pide el escenario *Categoría borrada*.
- `bruno/assistant/grupo-sin-saldo-confirmar.yml`: `value: "[1].amount"`. Verifica 409, un solo
  error, `INSUFFICIENT_FUNDS` y `field` `[1].amount`, como pide el escenario *Saldo que alcanza para
  cada uno pero no para los dos*.
- Ningún cambio de código, esquema ni contrato.

Al empezar a ejecutarse, los dos requests pueden fallar por algo que la colección nunca vio: que el
API no cumpla el escenario o que el estado que dejan los requests anteriores no sea el que suponen.
Si el fallo es del API, se para y se decide con el usuario antes de tocar código (ver `tasks.md`).

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna. Corrige la colección que verifica `transacciones`, sin cambiar su comportamiento. El change
lleva `skip_specs: true`.

## Cobertura de los criterios

| Criterio de FIN-84 | Cómo se cumple |
|---|---|
| 1. Los dos requests se cargan sin aviso y corren en `bru run` | Las comillas; `bru run` sin «Skipped» |
| 2. Cada uno verifica status, code y field de su escenario | Assertions de los dos archivos, contra `transacciones/spec.md` |
| 3. Ningún otro archivo de `bruno/` tiene un valor que YAML lea como lista u objeto | Escaneo con el parser de `bru` sobre toda la carpeta |
| 4. `gradlew build` y `bru run` en verde, sin omitidos | Grupo de verificación de `tasks.md` |

## Impact

- Dos archivos de `bruno/assistant/`. Ruta, cuerpo y autenticación no cambian, así que no hay nada
  que replicar en `bruno-personal/`, que no lleva assertions.
- FIN-83, creado al verificar FIN-66, describe el mismo error con los mismos archivos: este change
  lo cubre y se descarta como duplicado.

## Fuera de alcance

- Que `verificar-bruno.ps1` falle cuando `bru` omite un archivo inválido. Hoy sale con 0 aunque haya
  «Skipped», y por eso el error pasó desde FIN-58 sin que nadie lo notara. Es una mejora del script,
  no necesaria para cerrar este issue: quedó en FIN-85.
