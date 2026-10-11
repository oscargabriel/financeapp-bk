# Design

## Context

- Los campos de crédito (`creditLimit`, `statementDay`, `paymentDueDay`, `monthlyInterestRate`) siguen
  un mismo camino (FA-105):
  - En el alta, `CamposDeCredito` los valida contra el tipo del cuerpo.
  - En el parche, el formato va en `UpdateAccountRequest`, y la regla «solo `CREDIT`» la aplica
    `UpdateAccountUseCase.camposDeCreditoSegunElTipo` contra el tipo guardado.
  - En la base, `ck_accounts_credit_fields` los obliga a ir en `null` fuera de una `CREDIT`.
- El plan de cuotas vive en `InstallmentPlan.cuotas()`. Hoy `interes()` devuelve 0 cuando
  `count == 1`. `PlanDeCompra` construye el plan con la tasa de la tarjeta (`null` → 0).
- Las cuotas guardan su monto en `transactions.amount` y su capital en `installment_principal`. El
  listado, el cupo y los reportes leen esos valores, y nada recalcula el plan después del alta.
- `test-data.sql` y `demo-data.sql` insertan tarjetas nombrando las columnas, sin la nueva.

## Goals / Non-Goals

**Goals:** el contrato del campo, decidido aquí como pedía la tarea, y la regla del interés a 1 cuota
en un solo lugar del dominio.

**Non-Goals:** recalcular compras existentes y mostrar en la compra si se cobró el interés a 1 cuota.

## Decisions

### Nombre: `chargesSingleInstallmentInterest`

Es un boolean, y el nombre se lee como una afirmación sobre la tarjeta: «cobra interés a una cuota».
- Se descartó `singleInstallmentInterest`: suena a un monto, como `monthlyInterestRate`.
- Se descartó `interestOnOneInstallment`: no dice que es una opción de la tarjeta.

En la base, `charges_single_installment_interest`.

### Valor por defecto y `null`

- En una `CREDIT` el campo es siempre `true` o `false`. Si el alta no lo envía, queda en `false`, que
  es el comportamiento de FA-108.
- En las demás cuentas la respuesta lo trae en `null`, como los otros campos de crédito.
- En el parche, `null` es «no cambia», igual que en el resto del parche.

A diferencia de la tasa, aquí no hace falta un tercer estado: «no la he cargado» y «no cobra» se
comportan igual al calcular. Un `null` en una tarjeta obligaría al front a decidir qué marcar en la
casilla.

Fuera de una `CREDIT` se rechaza **cualquier** valor, también `false`. Así se mantiene la regla de
los demás campos de crédito: el front no envía campos de crédito en otros tipos.

### Columna `NOT NULL DEFAULT FALSE`, no nullable

```sql
charges_single_installment_interest BOOLEAN NOT NULL DEFAULT FALSE
```

`ck_accounts_credit_fields` suma `AND charges_single_installment_interest = FALSE` a la rama de las
cuentas que no son `CREDIT`. El adapter lee la columna y la devuelve como `null` cuando el tipo no es
`CREDIT`, y al escribir enlaza `false` para un valor `null`.

- **Por qué así:** `ADD COLUMN ... NOT NULL DEFAULT FALSE` deja las tarjetas existentes en `false` sin
  un `UPDATE`. Así no se dispara `trg_accounts_updated_at` sobre cada tarjeta. Además, los `INSERT` de
  `test-data.sql`, `demo-data.sql` y cualquier alta hecha con psql siguen valiendo sin nombrar la
  columna.
- **Descartado:** columna nullable, en `null` fuera de `CREDIT` y obligatoria en una `CREDIT`.
  - Es más simétrica con los demás campos de crédito.
  - Pero exige un `UPDATE` de relleno, que mueve el `updated_at` de cada tarjeta.
  - También exige tocar todos los `INSERT` de tarjetas de los scripts de datos, que dejarían de
    cumplir el `CHECK`.

### El plan recibe la opción

`InstallmentPlan` gana el componente `boolean chargesSingleInstallmentInterest`. `interes()` devuelve 0
con `count == 1` solo si la opción es `false`. Con la opción en `true`, la cuota única aplica la misma
fórmula que las demás: la tasa sobre el capital pendiente antes de la cuota, que con una sola cuota es
el total. Con tasa 0 la fórmula ya da 0, así que no hace falta otra rama.

`PlanDeCompra` pasa `Boolean.TRUE.equals(tarjeta.chargesSingleInstallmentInterest())`. Preview y alta
comparten `PlanDeCompra`, así que los dos cambian juntos.

### La opción no se copia en la compra

La tarea pedía decidir si la opción se copia en la compra, como la tasa. **No se copia:**

- El interés de cada cuota queda guardado en la cuota al registrarla. Ni el listado, ni el cupo, ni
  la edición en grupo recalculan el plan. Cambiar la opción después no puede mover una compra
  registrada aunque la compra no la recuerde.
- La tasa se copia porque la compra la muestra (`monthlyInterestRate`). Copiar la opción obligaría a
  agregar un campo a cada compra y una columna a `installment_purchases`. Ese campo solo significaría
  algo cuando `installmentCount` es 1.
- Si una compra a 1 cuota cobró interés, se ve en la respuesta: `remainingAmount` mayor que
  `remainingPrincipal`, o `interest` en la cuota.

**Descartado:** guardar en la compra la tasa efectiva (0 en una compra a 1 cuota sin la opción).
Cambiaría el significado de `monthlyInterestRate` en las compras ya registradas, que hoy traen la tasa
de la tarjeta aunque no hayan cobrado interés.

## Risks / Trade-offs

- **La app vieja con la base nueva**: no nombra la columna. Sus `INSERT` toman el `DEFAULT` y cumplen
  el `CHECK`, así que sigue funcionando. El update va antes de desplegar.
- **Una compra a 1 cuota no dice por sí sola si la tarjeta tenía la opción** → es la consecuencia de
  no copiarla, y se acepta: el monto guardado es el dato que importa.
- **Datos locales**: el update no reinicia datos y los scripts de datos no cambian. Aun así, el front
  tiene que recibir el campo nuevo, y el PR lo dice.

## Migration Plan

1. `docs/database/update/20261011_01_interes_una_cuota.sql`: agrega la columna y rehace
   `ck_accounts_credit_fields`. Se aplica en Neon **antes** de desplegar la app.
2. Reversión: primero la app, después el update comentado al final del archivo, que borra la columna
   y vuelve al `CHECK` anterior. Las opciones guardadas se pierden.
