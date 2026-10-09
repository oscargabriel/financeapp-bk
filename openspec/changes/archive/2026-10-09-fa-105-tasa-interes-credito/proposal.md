# Proposal

Origen: FA-105. Salió de un pedido hecho desde la raíz de coordinación el 09-10-2026, junto con
movimientos programados (FA-106), gastos recurrentes (FA-107), compras en cuotas (FA-108) y las
tareas del front. El primer criterio de la tarea pide decidir el contrato; lo decidido está en
`design.md`.

## Why

Una tarjeta de crédito ya tiene cupo, día de corte y día de pago, pero no tasa de interés. La compra
en cuotas (FA-108) necesita la tasa para calcular el interés de cada cuota, y la pantalla Mis Cuentas
del front necesita leerla y editarla. Las dos tareas esperan a esta.

## What Changes

- Las cuentas ganan `monthlyInterestRate`: la tasa de interés **mensual** de una tarjeta, expresada
  como **porcentaje** (`2.15` es el 2,15 % mensual), de 0 a 10 con hasta 4 decimales. Es opcional,
  como el cupo: una tarjeta sin tasa cargada la trae en `null`.
- `POST /api/accounts` la acepta en una cuenta `CREDIT`. En cualquier otro tipo, enviarla es 400
  `VALIDATION_ERROR` en `monthlyInterestRate`, igual que con `creditLimit`.
- `PATCH /api/accounts/{id}` la acepta sobre una cuenta `CREDIT` con la semántica del resto del
  parche: ausente o en `null` se conserva. Sobre otro tipo es 400 en su campo.
- Una tasa negativa, mayor que 10 o con más de 4 decimales es 400 `VALIDATION_ERROR` en
  `monthlyInterestRate`, en el alta y en el parche.
- `GET`, `POST` y `PATCH /api/accounts` devuelven `monthlyInterestRate` en cada cuenta: con valor o
  `null` en una `CREDIT`, siempre `null` en las demás. El campo es nuevo en la respuesta: no rompe a
  ningún cliente.
- La spec deja escrito el 401 de `POST` y `GET /api/accounts`, que existía sin spec, porque ahora se
  tocan esos endpoints.

No es un cambio incompatible.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `cuentas`: la forma de la cuenta en las respuestas, el parche y los campos de crédito según el tipo
  incluyen la tasa; un requisito nuevo describe la tasa (unidad, rango, precisión, alta y parche), y
  otro el 401 del alta y el listado.

## Fuera de alcance

- Calcular interés: las cuotas con interés son FA-108. Esta tarea solo guarda y expone la tasa.
- Qué hace una compra en cuotas con una tarjeta sin tasa cargada: lo decide FA-108.
- Tasa efectiva anual, conversión entre tasa anual y mensual, o tasas distintas por tipo de compra
  (avances, compras, cartera).
- Vaciar la tasa de una tarjeta con el PATCH: como con el cupo y los días, `null` no cambia nada.
- Historial de tasas: la tasa vale desde que se cambia, sin registro de la anterior.
- El resto de la spec del alta y del listado de cuentas, que sigue sin escribirse.
- El asistente de IA no lee ni escribe la tasa.

## Impact

- Esquema: columna `monthly_interest_rate NUMERIC(6,4)` en `finance.accounts` con su `CHECK` de rango,
  y `ck_accounts_credit_fields` la incluye. En `schema.sql` y como update en `docs/database/update/`,
  con su reversión; se aplica en Neon antes de desplegar la app. `modelo-datos.md` documenta la
  columna.
- Datos: `test-data.sql` y `demo-data.sql` dan una tasa a una de sus tarjetas. En local, después de
  aplicar el update se recargan los datos, y el PR lo avisa para el front.
- Dominio: `Account`, `NewAccount`, `CreateAccountCommand` y `UpdateAccountCommand`.
- Aplicación: `CreateAccountUseCase` y `UpdateAccountUseCase`.
- Web: `CreateAccountRequest`, `UpdateAccountRequest`, `AccountResponse` y la validación de
  `CamposDeCredito`, con una regla de formato de la tasa reutilizable.
- Persistencia: `AccountR2dbcAdapter` lee y escribe la columna.
- `bruno/accounts/`: aserciones nuevas y requests nuevos; replicados en `bruno-personal/cuentas/`.
- `docs/api/contrato-api.md`: el campo en la respuesta, el alta y el parche.
