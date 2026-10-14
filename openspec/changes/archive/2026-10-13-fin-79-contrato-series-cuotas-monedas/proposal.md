## Why

FIN-79, del epic FIN-7 (Multimoneda y tasas de cambio). Surgió en FIN-37 (frontend), al habilitar
«Repetir» y «En cuotas» con cuentas de otras monedas. Aplica la decisión del epic «Cada movimiento se
registra en la moneda de su cuenta» (FA-51, 10-10-2026): las series y las compras en cuotas la siguen
desde FA-123. El epic no tiene `## Fuera de alcance`.

La lista «Lo que el API todavía no tiene» de `docs/api/contrato-api.md` (línea 2357) dice todavía
«Series y compras en cuotas en monedas distintas de COP». La línea viene del primer contrato
(commit 14ec002) y nadie la quitó al entregar FA-123. Contradice el resto del contrato y las specs:

- `accountId` de `POST /api/recurrences` (línea 1543): «en una moneda activa. La serie y sus
  ocurrencias van en la moneda de la cuenta».
- `PATCH /api/recurrences/{id}` (línea 1613): «La moneda de la serie no cambia».
- `accountId` de `POST /api/installment-purchases` (línea 1692): «en una moneda activa […] La compra
  y sus cuotas van en la moneda de la tarjeta».
- `GET /api/catalogs/currencies` (línea 968) y la regla general de la línea 97 (FA-123).
- `openspec/specs/cuotas/spec.md:18` y `openspec/specs/recurrentes/spec.md:17-18`: «en cualquier
  moneda activa».

Ninguna otra parte del contrato afirma que series o cuotas sean solo en COP: las demás menciones de
`COP` son ejemplos de JSON o la unidad mínima por moneda («1 peso, o 0.01 USD»).

## What Changes

- `docs/api/contrato-api.md`: se borra la línea «Series y compras en cuotas en monedas distintas de
  COP.» de «Lo que el API todavía no tiene». El resto de la lista no cambia.
- Ningún cambio de código, esquema, Bruno ni specs.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna. El contrato pasa a decir lo que `cuotas` y `recurrentes` ya especifican. El change lleva
`skip_specs: true`.

## Cobertura de los criterios

| Criterio de FIN-79 | Cómo se cumple |
|---|---|
| 1. La lista ya no menciona series ni cuotas en monedas distintas de COP | Tarea 1.1 |
| 2. Ninguna otra parte del contrato afirma que series o cuotas son solo en COP | Tarea 1.2: revisión de las menciones de `COP` y de moneda en el contrato |
| 3. `gradlew build` y `bru run` en verde | Grupo de verificación |

## Impact

- Solo `docs/api/contrato-api.md`. No toca `bruno/` ni `bruno-personal/`.
- La copia del front (`financeapp-fr/contrato-api.md`) ya quitó la misma línea en FIN-80, que está
  en `Por revisar` sin enlace con este issue. Por la regla del contrato de `tareas-jira`, al cerrar se
  enlaza FIN-79 `Blocks` FIN-80; no hace falta un issue `Frontend` nuevo.
- `docs/superpowers/specs/2026-09-12-roadmap-etapas-design.md:86` dice «La etapa 8 arranca solo en
  COP»: es el diseño histórico del roadmap, no el contrato, y no se toca.

## Fuera de alcance

- Cualquier otra entrada de «Lo que el API todavía no tiene», aunque esté vieja.
