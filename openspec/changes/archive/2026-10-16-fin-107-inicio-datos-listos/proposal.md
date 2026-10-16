# Proposal

Origen: FIN-107, del epic FIN-2 (App web: base y experiencia general). Lo consume FIN-108, la tarea
`Frontend` que cambia Inicio para usar este endpoint.

## Why

La pantalla de Inicio del front hace hoy tres pedidos (`/monthly-spending`, `/reports/transactions`
con seis meses de gastos para mostrar diez filas, y `/reports/balance`) y calcula en
`financeapp-fr/src/app/inicio/resumen.ts` la serie completa, la variación, los últimos y más
costosos y el cupo usado. Esa lógica es del negocio, se duplica en el cliente y trae de más por la
red. El epic decidió que Inicio reciba los datos ya calculados.

Decisiones del epic FIN-2 que aplica:

- La pantalla de Inicio recibe del API los datos ya calculados (serie mensual completa, variación,
  últimos y más costosos, cupo usado, saldo débito); el front solo les da formato y los dibuja
  (16-10-2026).
- El cupo usado de una tarjeta sigue siendo `−currentBalance / creditLimit`, acotado entre 0 y 100:
  no descuenta las cuotas futuras (16-10-2026).

## What Changes

- Endpoint nuevo `GET /api/reports/home`, Bearer, sin parámetros, que devuelve en una respuesta:
  - la serie de los 6 meses que terminan en el mes en curso, del más antiguo al más reciente, con
    los meses sin movimientos en cero, y `totalIncome` y `totalSpent` en la moneda base;
  - el gastado del mes en curso y su variación porcentual frente al mes anterior, `null` si el
    anterior es cero;
  - los últimos 5 gastos ya ocurridos, con descripción, categoría, fecha y monto en su moneda y en
    la base;
  - los 5 gastos más costosos del mes en curso por su monto en la moneda base, cada uno con su
    proporción frente al mayor;
  - el saldo débito total de FIN-104 y las cuentas activas, cada tarjeta con su porcentaje de cupo
    usado.
- El mes en curso se decide en la zona horaria del usuario, igual que el mes de cada movimiento.
- Los montos y porcentajes salen como números; el formato lo da el front.
- Los endpoints actuales (`/monthly-spending`, `/reports/transactions`, `/reports/balance`) no
  cambian: los usan otras pantallas.
- Contrato: sección nueva en `docs/api/reportes.md` y fila en `docs/api/contrato-api.md`.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `reportes`: requisitos nuevos para el resumen de Inicio (`GET /api/reports/home`): su contenido,
  la serie completa, la variación, los últimos y más costosos, el cupo usado, su autenticación y la
  extensión de las reglas de pendientes y programados a este endpoint.

## Impact

- `core`: puerto de entrada, caso de uso que compone los puertos de salida existentes
  (`MonthlySpendingQueryPort`, `BalanceQueryPort`, `AccountQueryPort`) y dos consultas nuevas en
  `TransactionReportQueryPort` para los últimos y los más costosos, con su controlador y DTO.
- `TransactionReportR2dbcAdapter`: dos consultas con `LIMIT`. Sin cambios de esquema ni de datos.
- `bruno/`: carpeta nueva `home/`; `bruno-personal/` recibe el request.
- El front (FIN-108) reemplaza los tres pedidos de Inicio por este. Hasta entonces el endpoint no
  tiene consumidor y nada se rompe.

## Fuera de alcance

- Cambiar o retirar `/monthly-spending`, `/reports/transactions` o `/reports/balance`.
- Las metas en Inicio: el endpoint no trae `budgetAmount` porque la pantalla no las muestra.
- Descontar las cuotas futuras del cupo usado (decisión del epic, 16-10-2026).
- Elegir el mes o el tamaño de las listas por parámetro: Inicio siempre muestra el mes en curso.
- El cambio de Inicio en el front: es FIN-108.
