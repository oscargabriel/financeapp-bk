# Design

## Context

`accounts.current_balance` lo mantiene `trg_transactions_sync_balance` con todo movimiento confirmado,
también los de fecha futura. El saldo que ve el usuario, el **vigente**, es `current_balance` menos
`finance.scheduled_balance_delta(id)`, y es el `currentBalance` que ya traen las `Account` de
`AccountQueryPort.findByUser` y de `AccountRepositoryPort.findActiveByIdAndUser`.

Dos cosas mueven saldos sin que haya una petición de escritura del usuario:

- Un programado empieza a contar solo cuando llega su fecha.
- `SeriesAlDia` inserta las ocurrencias atrasadas de las series sin fin **desde las lecturas**:
  `GET /accounts`, los reportes y `monthly-spending`.

Las decisiones de alcance las tomó el usuario el 11-10-2026, al proponer:

- los programados no se validan;
- reducir o borrar un ingreso que deja la cuenta en negativo se rechaza;
- el rechazo es 409 `INSUFFICIENT_FUNDS`;
- las series se permiten con advertencia, y FA-143 queda para analizarlas.

## Goals / Non-Goals

**Goals**

- Que ninguna petición del usuario baje una cuenta no `CREDIT` por debajo de cero.
- Que el rechazo diga qué cuenta, en qué campo y con un texto que se pueda mostrar.

**Non-Goals**

- Garantizar la regla frente a escrituras concurrentes.
- Validar las series (FA-143).
- Corregir datos existentes.

## Decisions

### 1. La regla vive en los casos de uso, no en la base

Una clase nueva en `application/usecase`, `SaldoNoNegativo`, calcula el efecto de un movimiento
sobre el saldo vigente de cada cuenta, y los casos de uso la llaman antes de escribir.

Descartado: un `CHECK` sobre `accounts` o un trigger que rechace el saldo negativo.

- Lo que la base ve es `current_balance`, que incluye lo programado. Un CHECK sobre ese valor
  bloquearía programar un gasto aunque el saldo de hoy alcance, y dejaría gastar hoy contra un
  ingreso que todavía no llega.
- Un trigger también dispararía en las inserciones de `SeriesAlDia`, y haría fallar una lectura como
  `GET /accounts`.
- El error de la base no sabe en qué elemento del lote ni en qué campo ponerse. El front lo necesita.

Precio aceptado: dos peticiones simultáneas sobre la misma cuenta pueden pasar las dos el chequeo,
porque se lee y luego se escribe. En una app de un usuario por cuenta el riesgo es bajo. Si se
vuelve un problema, la salida es leer la cuenta con `FOR UPDATE` dentro de la transacción de
escritura.

### 2. Qué saldo se compara y cuándo se rechaza

Se compara el saldo vigente. El efecto de un movimiento sobre él es:

| Movimiento | Cuenta origen | Cuenta destino |
|---|---|---|
| `EXPENSE` | `-amount` | — |
| `INCOME` | `+amount` | — |
| `TRANSFER` | `-amount` | `+destinationAmount` o, si no viene, `+amount` |

Un movimiento solo tiene efecto si está `CONFIRMED` y su `occurredAt` no es posterior al reloj. Un
pendiente o un programado tienen efecto cero.

Se rechaza una cuenta no `CREDIT` cuando la operación la **baja** (delta neto < 0) y el saldo
resultante es < 0. Por qué la condición del delta:

- una cuenta que ya está en negativo, por datos viejos o por un programado que llegó, puede recibir
  dinero aunque no salga de cero;
- una operación que no la toca no se rechaza por el estado en que ya estaba.

Descartado: comparar contra el saldo proyectado (con lo programado). Lo pidió descartar el usuario:
una serie de arriendo sin la del salario bloquearía el registro.

Descartado: validar el recorrido completo en el tiempo, es decir, que el saldo nunca haya sido
negativo en ninguna fecha. Rechazaría un gasto de la semana pasada registrado tarde, aunque hoy el
saldo alcance, y cambiaría el comportamiento con datos que ya existen.

### 3. Cada operación, como diferencia de efectos

- **Lote**: suma de efectos de los elementos, después de convertir monedas (FA-51). Así `amount` ya
  está en la moneda de la cuenta, y los que cambian de moneda ya quedaron `PENDING`. Se valida el
  neto por cuenta para que el lote, que es todo o nada, no dependa del orden. El error se ubica
  recorriendo el lote en orden desde el saldo vigente: va en el primer elemento que saca dinero de la
  cuenta y deja el acumulado < 0. Esto corre después de la validación de referencias: si hay 400, no
  se llega aquí.
- **PATCH**: efecto del resultante menos efecto del guardado, por cuenta. Cubre cambiar cuenta, tipo,
  monto, destino y fecha sin casos especiales. Va después de las reglas que hoy dan 400.
- **DELETE**: menos el efecto del guardado. Antes de borrar hay que leerlo con `findByIdAndUser`, que
  ya existe. El 404 de un id ajeno o inexistente se mantiene igual.
- **Aprobar**: el efecto del movimiento ya confirmado. Va después del chequeo de cuentas desactivadas:
  con una desactivada solo sale `INVALID_STATE`.
- **Cuenta**:
  - `initialBalance` < 0 en un tipo no `CREDIT` es 400 `VALIDATION_ERROR`. En el alta lo decide el
    cuerpo, con una constraint de clase `@SaldoInicialSegunElTipo` en `dto/validation/`, como
    `@CamposDeCredito`. En el parche lo decide el tipo guardado, en `UpdateAccountUseCase`, junto a
    `camposDeCreditoSegunElTipo`.
  - Si el inicial baja, el delta es `nuevo - guardado` sobre el saldo vigente, y se rechaza con 409 en
    `initialBalance` antes del update, así que no se escribe ningún campo.

### 4. Contrato del error

Código nuevo `INSUFFICIENT_FUNDS` en `ErrorCodes`, con HTTP 409: es un rechazo que depende del
estado guardado, como `RESOURCE_IN_USE` e `INVALID_STATE`. Sale un error por cuenta.

El `field`:

- `amount` en PATCH y aprobación;
- `[i].amount` en el lote;
- `id` en DELETE, que no tiene cuerpo;
- `initialBalance` en cuentas.

La `description` es `"Saldo insuficiente en <nombre>: quedaría en <saldo> <moneda>"`. El saldo va
sin ceros de relleno (`-50000`, `-12.5`).

Descartado: 400 `VALIDATION_ERROR`. El front no distinguiría un error de formato de uno de saldo.
Descartado: 422, un HTTP que el API no usa en ninguna otra parte.

### 5. Programados, series y cuotas

- **Programados**: no se validan al crearse ni al llegar su fecha, que es cuando el trigger y la
  resta de `scheduled_balance_delta` los hacen contar sin ninguna escritura.
- **Ocurrencias que crea `SeriesAlDia`**: no pasan por estos casos de uso y no se validan.
- **Alta de una serie y `PATCH` con `ALL`**: no se validan, con la advertencia en el contrato y en la
  spec de `recurrentes`. Su análisis queda en FA-143.
- **Cuotas**: solo existen en tarjetas `CREDIT` y quedan fuera de la regla.

### 6. Cuentas que ya están en negativo

No se migra nada, ni en la base local ni en Neon. Esas cuentas siguen funcionando con la regla del
punto 2: reciben dinero y no aceptan salidas.

Para listarlas en cualquiera de las dos bases (solo lectura):

```sql
SELECT u.email, a.name, a.type,
       a.current_balance - finance.scheduled_balance_delta(a.id) AS saldo_vigente
  FROM finance.accounts a
  JOIN finance.users u ON u.id = a.user_id
 WHERE a.type <> 'CREDIT'
   AND a.deleted_at IS NULL
   AND a.current_balance - finance.scheduled_balance_delta(a.id) < 0;
```

## Risks / Trade-offs

- **Concurrencia.** Ver la decisión 1.
- **Requests de `bruno/` que hoy dejan cuentas en negativo**, por ejemplo aprobaciones sobre
  `Bolsillo pendientes`, que tiene saldo 0, van a dar 409. Se ajustan el escenario o los montos sin
  cambiar lo que cada request verifica, y se anota cuáles.
- **El `DELETE` suma una lectura.** Antes era un solo `DELETE` filtrado. El costo es una consulta
  por id.
