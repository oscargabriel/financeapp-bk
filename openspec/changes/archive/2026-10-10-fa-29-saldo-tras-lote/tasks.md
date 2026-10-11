# Tasks

## 1. Comprobación de saldos

- [x] 1.1 `docs/database/check-saldos.sql`:
  - Solo lee, sobre todas las cuentas.
  - Para cada cuenta calcula el saldo esperado: el saldo inicial, más `balance_delta` de los
    `CONFIRMED` que salen de ella, más `COALESCE(destination_amount, amount)` de los `CONFIRMED`
    que entran.
  - Lista las cuentas descuadradas, con el correo de su dueño.
  - Si hay alguna, termina con `RAISE EXCEPTION`.

  Verificación:
  - pasa sobre la base recién recargada;
  - falla si, dentro de una transacción que se revierte, se descuadra a mano un `current_balance`.
- [x] 1.2 `docs/database/test-checks.sql` se versiona, y su comprobación 2 pasa a la misma regla,
  filtrada al usuario del escenario. Verificación: la comprobación 2 da `coincide = t` en todas las
  cuentas de `prueba@`.

## 2. Paso del cierre

Skills: `bruno-cli`.

- [x] 2.1 `verificar-bruno.ps1`:
  - Al terminar `bru run`, y solo si la app llegó a correr la colección, ejecuta `check-saldos.sql`
    con las mismas credenciales que la recarga.
  - Si hay descuadres, imprime la salida y sale con código distinto de 0.

  Verificación: una corrida completa con `-RecargarDatos` termina en verde y muestra la
  comprobación.
- [x] 2.2 Documentación del paso:
  - `AGENTS.md`, en *Comandos*: la comprobación que agrega el script y cómo correrla a mano;
  - `modelo-datos.md`, en *Probar en local*.

## 3. Verificación

- [x] 3.1 `gradlew build` en verde, con el conteo de tests.
- [x] 3.2 `verificar-bruno.ps1 -RecargarDatos` en verde, con los conteos y el resultado de
  `check-saldos.sql`.
