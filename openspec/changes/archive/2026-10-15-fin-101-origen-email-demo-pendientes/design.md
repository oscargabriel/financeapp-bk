# Design

## Context

`origin` vive en tres lugares: el enum del dominio, el check `ck_transactions_origin` (columna
`VARCHAR(10)`, donde `EMAIL` cabe) y la tabla del contrato. El adapter R2DBC convierte con
`TransactionOrigin.valueOf` al leer y `name()` al escribir, y la aprobación y el PATCH copian el
origen del guardado: con el valor en el enum, el resto ya lo conserva.

`bruno/pending/` recorre a `pendientes@` en una secuencia que aprueba y rechaza los tres pendientes
sembrados y verifica los saldos y reportes después de cada paso (seq 2 a 26). Un cuarto pendiente
que se apruebe en medio cambia esos números.

## Decisions

### El pendiente `EMAIL` de `pendientes@` se aprueba al final de la carpeta

Va en su propio grupo, en `Cuenta pendientes`, y es el más antiguo de los pendientes: sale último en
la lista. Sigue pendiente durante toda la secuencia de saldos y reportes, que no lo cuentan, así que
esos asserts no cambian. Cambian solo los que cuentan pendientes: la lista inicial (3 → 4), la de
cuentas desactivadas (2 → 3) y la de tras rechazar (0 → solo el de correo). Al final, después de los
casos de error, un request lo aprueba y comprueba `"origin": "EMAIL"`, `CONFIRMED` y su `groupId`, y
otro confirma que la lista queda vacía. `check-saldos.sql` corre después y cubre el saldo que mueve.

Descartado: aprobarlo junto a los otros (seq 20-21), porque obliga a rehacer los montos de
`saldos-tras-aprobar`, `saldo-tras-aprobar`, `reporte-tras-aprobar` y `saldos-tras-rechazar` sin
probar nada nuevo. También descartado: cambiarle el origen a uno de los pendientes existentes. El
que se rechaza no se aprobaría, y el gasto y la transferencia comparten grupo, que es un mensaje del
asistente: un grupo con orígenes distintos no existe en la app.

### El origen en la capa web y en la de aplicación

Un test de controlador fija que la lista de pendientes devuelve `"origin": "EMAIL"`, y uno de caso de
uso que aprobar uno a uno y por grupo lo conserva. El rechazo no devuelve cuerpo y no lee el origen:
no lleva test propio. El escenario del cuerpo con `"origin": "EMAIL"` lo cubre el mismo test del
controlador que hoy prueba `TELEGRAM`, con el valor nuevo.

### Orden en Neon: update, despliegue y después la demo

La app de hoy revienta al leer una fila `EMAIL` (`valueOf`). El update solo amplía el check, así que
puede ir antes o después de desplegar, pero la demo, que trae esas filas, se recarga **después de
las dos cosas**. El encabezado del update lo dice.

## Risks / Trade-offs

- [Una fila `EMAIL` en una base con la app vieja rompe la lista de pendientes de ese usuario] → solo
  los scripts de datos la escriben, y el orden de arriba lo evita en Neon. En local, la app de la
  rama ya lo conoce.
