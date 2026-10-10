## Why

FA-127. El front no mostraba en el formulario de movimiento una cuenta en otra moneda, y una de las
causas es que `docs/api/contrato-api.md`, en la sección de `GET /api/catalogs/currencies`, decía que
el alta de movimientos solo admitía COP. FA-51 cambió eso: cada movimiento va en la moneda de su
cuenta, y lo que llega en otra se convierte y queda pendiente.

FA-51 (`de1f996`) ya quitó la frase literal. La sección dice ahora que las monedas del catálogo
son las que acepta `currencyCode` en el alta de cuentas y de movimientos. Le faltan dos cosas que
pide el criterio de la tarea:

- No dice que el alta admite movimientos sobre **cuentas** de cualquier moneda activa.
- No dice que las series y las compras en cuotas siguen limitadas a cuentas en COP. Eso solo
  aparece en *Montos*, al inicio del documento, y en las tablas de sus endpoints.

La copia del front (`financeapp-fr/contrato-api.md`) se pone al día en su próxima sincronización.
FA-126 cubre el lado del front.

## What Changes

- `docs/api/contrato-api.md`, sección `GET /api/catalogs/currencies`. El párrafo pasa a decir que:
  - son las monedas que acepta `currencyCode` en `POST /api/accounts`;
  - el alta de movimientos admite cuentas de cualquiera de ellas, y un movimiento en una moneda
    distinta de la de su cuenta se convierte y queda pendiente (enlace a `POST /api/transactions`);
  - las series y las compras en cuotas, por ahora, solo admiten cuentas en COP (enlaces a sus
    secciones).

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna. Es documentación del contrato y no cambia el comportamiento: el change lleva
`skip_specs: true`. La spec `transacciones` ya describe la regla desde FA-51.

## Cobertura de los criterios

| Criterio | Estado |
|---|---|
| 1. La sección del catálogo de monedas dice que el alta admite cuentas en cualquier moneda activa y que las series y las cuotas siguen solo en COP | Se implementa en `docs/api/contrato-api.md`. La frase vieja ya la había quitado FA-51 |
| 2. Sin cambios de comportamiento: `gradlew build` y `bru run` en verde | Se verifica al cerrar |

## Impact

- Solo `docs/api/contrato-api.md`. Sin cambios en el código, el esquema, Bruno ni `bruno-personal/`.

## Fuera de alcance

- La copia del contrato en `financeapp-fr`: se sincroniza desde ese repo.
- Las series y las cuotas en otras monedas: es FA-123.
