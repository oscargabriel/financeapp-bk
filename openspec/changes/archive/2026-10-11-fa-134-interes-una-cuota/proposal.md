# Proposal

Origen: FA-134. Pedido desde la raíz de coordinación el 11-10-2026 con el texto «al crear una cuenta
que sea de credito preguntar si genera intereses a 1 cuota y afecta cuando se crean movimientos». El
usuario aclaró que aplica a las compras en cuotas: que una compra a 1 cuota cobre interés depende de
la configuración de la tarjeta. Lo espera FA-141 (front), que muestra la opción al crear y editar una
tarjeta.

## Why

Hoy una compra a 1 cuota nunca cobra interés (FA-108): la regla está fija en el cálculo del plan. Hay
tarjetas que sí cobran interés a una cuota, y con esas el preview y el alta muestran una cuota menor
que la del extracto. La tarjeta necesita decir cuál de los dos comportamientos tiene.

## What Changes

- Campo nuevo en las cuentas `CREDIT`: `chargesSingleInstallmentInterest` (boolean). `true` es una
  tarjeta que cobra interés en las compras a 1 cuota.
- `POST /api/accounts` lo acepta en una `CREDIT`. Si no llega, la tarjeta queda en `false`: el
  comportamiento de hoy.
- `PATCH /api/accounts/{id}` lo acepta en una `CREDIT`. Ausente o `null` es «no cambia», como los
  demás campos.
- `GET`, `POST` y `PATCH` lo devuelven: `true` o `false` en una `CREDIT`, `null` en los demás tipos.
- En otro tipo, enviarlo con valor (también `false`) es 400 `VALIDATION_ERROR` en
  `chargesSingleInstallmentInterest`, igual que los demás campos de crédito.
- Las tarjetas que ya existen quedan en `false`, sin migrar datos a mano.
- Compras en cuotas: con la opción en `true` y tasa mayor que cero, el preview y el alta de una compra
  con `installmentCount` 1 cobran la tasa mensual sobre el total. El redondeo es el de las demás
  cuotas: pesos enteros, la mitad hacia arriba. Sin la opción, o con tasa 0 o sin tasa, sigue sin
  interés. Con 2 cuotas o más no cambia nada.
- La opción **no se copia en la compra**. Las cuotas guardan su interés al registrarse, y nada lo
  recalcula después, así que cambiar la opción no mueve las compras ya registradas (ver `design.md`).
- `contrato-api.md`, la colección `bruno/` y `bruno-personal/` al día.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `cuentas`:
  - la forma de la cuenta en las respuestas;
  - los campos del parche y los campos de crédito según el tipo;
  - un requisito nuevo para la opción.
- `cuotas`:
  - el interés de una compra a 1 cuota en «Capital e interés de las cuotas»;
  - el escenario de la compra a una cuota del alta.

## Impact

- Código:
  - Dominio: `Account`, `NewAccount`, `CreateAccountCommand`, `UpdateAccountCommand` e
    `InstallmentPlan`.
  - Casos de uso: `CreateAccountUseCase`, `UpdateAccountUseCase` y `PlanDeCompra`.
  - Web: `CreateAccountRequest`, `CamposDeCredito`, `UpdateAccountRequest` y `AccountResponse`.
  - Persistencia: `AccountR2dbcAdapter`.
- Base de datos: columna nueva `finance.accounts.charges_single_installment_interest` en
  `schema.sql` y en un update de `docs/database/update/`, que se aplica en Neon **antes** de desplegar.
  No modifica filas existentes fuera de la columna nueva, y `test-data.sql` y `demo-data.sql` no
  cambian.
- API: un campo más en la cuenta. El cuerpo y la respuesta de las compras en cuotas no cambian.
- Front (FA-141): lee y escribe el campo. Ver el preview de una compra a 1 cuota con interés.

## Fuera de alcance

- Copiar la opción en la compra, o mostrar en la compra si se cobró interés a 1 cuota. Se descarta en
  `design.md`.
- Recalcular las compras ya registradas al cambiar la opción o la tasa.
- Volver a dejar la opción en `null` por el parche: en una `CREDIT` siempre es `true` o `false`.
- El interés de las compras con tarjeta que no se registran en cuotas (`POST /api/transactions`).
