# Proposal

Origen: FA-93. Pedido del usuario el 08-10-2026, al cerrar FA-74 (login del front contra el API
local): «crear una tarjeta de notion para cargar datos de prueba en la base de datos que sirvan para
una demo y para las diferentes pruebas de aplicacion antes de seguir con mas pruebas».

## Why

`docs/database/test-data.sql` crea el escenario mínimo que necesitan los asserts de `bruno/`: dos
meses y medio de movimientos, casos puntuales y montos elegidos para que los totales cuadren. No
alcanza para una demo ni para probar las pantallas del front que vienen (FA-82 a FA-88: inicio,
gastos, cuentas, categorías y reportes), y no se puede engordar sin romper los totales que la
colección y las specs fijan sobre ese usuario.

## What Changes

- `docs/database/demo-data.sql`, nuevo, re-ejecutable: borra el usuario `demo@financeapp.local` con
  todo lo suyo y lo vuelve a crear. Con psql, igual que `test-data.sql`, y con la misma contraseña
  (`claveDePrueba123`), documentada en el encabezado.
  - Categorías: la copia de la semilla, como hace el registro, más dos propias: una con movimientos y
    una sin ninguno.
  - Cuentas con id fijo: efectivo, débito, tarjeta de crédito con cupo, día de corte y día de pago,
    ahorros, inversión, una cuenta COP con saldo cero sin movimientos y una cuenta en USD sin
    movimientos (el API solo acepta movimientos en COP). Todas con saldo inicial.
  - Movimientos del mes en curso y los cinco anteriores, con fechas relativas al mes en curso en hora
    de Bogotá: gastos fijos e ingresos de cada mes, gastos variables repartidos entre las categorías
    de la semilla y las propias, montos con decimales y algunos grandes, y transferencias entre
    cuentas (retiros de cajero, pago de la tarjeta). Del mes en curso solo entran los que ya
    ocurrieron: un movimiento con fecha futura no tiene sentido en una demo.
  - Un mes con pocos movimientos (hace tres meses) como caso de borde.
  - Metas globales y por categoría en algunos meses, una de ellas superada, para que
    `GET /api/monthly-spending` tenga algo que mostrar en presupuesto.
- No toca nada global: ni monedas, ni tasas, ni el usuario o los datos de `test-data.sql`. Cargar
  los dos scripts en cualquier orden deja la colección de `bruno/` en verde.
- Front y back comparten la base local, separados por usuario (decidido con el usuario el
  08-10-2026): el back prueba con `prueba@financeapp.local` y los `@bruno.local`, el front con
  `demo@financeapp.local` y, si registra usuarios desde la interfaz, con correos `@front.local`.
  `demo-data.sql` borra los `@front.local` al recargar, igual que `test-data.sql` borra los
  `@bruno.local`.
- `AGENTS.md`, sección *Base de datos*: qué es `demo-data.sql`, cómo cargarlo, con qué usuario
  entrar y qué usuarios son de cada lado.

La pregunta abierta de la tarea —SQL directo o carga por el API— se resuelve por SQL; el porqué está
en `design.md`.

## Capabilities

### New Capabilities

Ninguna (`skip_specs: true`): son datos de la base local, no comportamiento de la app.

### Modified Capabilities

Ninguna.

## Fuera de alcance

- Cargar estos datos en Neon. Igual que `test-data.sql`, el script es solo para la base local.
- Requests en `bruno/` contra el usuario de demo: la colección prueba el escenario de
  `test-data.sql`, y sus asserts no deben depender de datos pensados para cambiar con la demo. La
  verificación del criterio «login → 200 y los tres reportes con datos» se hace a mano contra el API
  levantado y queda registrada en `tasks.md`.
- Más meses, tipos o usuarios de demo, o datos para flujos que el API todavía no tiene (Telegram,
  importación): esta carga cubre lo que el front necesita hoy. Una base local separada para el
  front y un escenario más grande se diseñan después entre los dos, en una tarea nueva del tablero.

## Impact

- Archivos: `docs/database/demo-data.sql` (nuevo) y `AGENTS.md`.
- Sin cambios en `src/`, en el esquema ni en `bruno/`.
