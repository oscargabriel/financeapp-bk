# Design

## Context

La base local se arma a mano con `schema.sql` y `seed.sql`, y `test-data.sql` le pone encima el
escenario de `bruno/`. Ese script borra solo sus dos usuarios (`prueba@`, `inactivo@`) y los
`%@bruno.local` que dejan los requests de alta; además escribe dos filas globales, la tasa USD→COP
del día y la moneda inactiva `XTS`. El `reset-data.sql` local del usuario, sin versionar, vacía
todas las tablas de uso. El saldo de cada
cuenta lo mantienen los triggers de `transactions`, así que basta con insertar los movimientos.

## Goals / Non-Goals

**Goals:**
- Datos verosímiles para recorrer el front, que se recargan con un comando y no interfieren con
  `bruno/`.
- Fáciles de ajustar: agregar un movimiento es agregar una fila a una lista.

**Non-Goals:**
- Totales exactos fijados en una spec o en asserts: los datos de demo pueden cambiar cuando el front
  los necesite, sin tocar nada más.

## Decisions

### 1. Script SQL con psql, no carga por el API

Es la pregunta abierta de la tarea.

- **SQL**: igual que `test-data.sql`. Re-ejecutable con un `DELETE` del usuario (todo cae en
  cascada), deja poner fechas de hace seis meses sin depender de que el API las acepte, y carga en
  un segundo.
- **Por el API** (una colección de Bruno de alta): ejercitaría las validaciones, pero no puede borrar
  al usuario —no hay endpoint— y cada corrida tendría que inventar un correo nuevo o fallar en el
  registro; serían más de 150 requests secuenciales con su login; y la validación ya la ejercita
  `bruno/` con asserts, que una carga de demo no tendría.

Descartado el API. El precio es que el script escribe filas que el API nunca validó; se acota
copiando las reglas que la base no impone (movimientos solo en cuentas COP, categoría compatible con
el tipo, cuentas activas) y comprobándolo con los reportes del API en la verificación.

### 2. Misma contraseña que `test-data.sql`

`claveDePrueba123`, con el mismo hash BCrypt. psql no calcula BCrypt sin `pgcrypto`, y un hash nuevo
habría que generarlo fuera y documentar una segunda clave. Es un dato de la base local, no una
credencial. Descartado: un hash propio, que no aporta nada en local y suma una clave que recordar.

### 3. Independiente de `test-data.sql`

Correo `demo@financeapp.local`, ids fijos con prefijos que `test-data.sql` no usa (`40…` usuario,
`50…` cuentas, `60…` categorías propias), y ninguna fila global: ni tasas ni monedas. La cuenta en
USD no tiene movimientos, así que no necesita tasa. Así los dos scripts se cargan en cualquier orden
y cada uno borra solo lo suyo. Los ids fijos de las cuentas sirven para que el front pueda guardar
un enlace a una cuenta entre recargas; los de los movimientos se generan con `gen_random_uuid()`,
como en `test-data.sql`.

### 4. Movimientos como dos listas: fijos de cada mes y variables por mes

- Los **fijos** (salario, arriendo, servicios, internet, suscripciones, gimnasio, retiro de cajero,
  pago de la tarjeta) se escriben una vez y se cruzan con los meses 0, -1, -2, -4 y -5.
- Los **variables** van en una lista con la columna del mes, una fila por movimiento: mercado,
  restaurantes, transporte, salud, compras grandes, ingresos ocasionales.
- El mes -3 no recibe los fijos: es el mes con pocos movimientos, con tres filas propias.

Cada fecha es el primer día del mes en hora de Bogotá más un desfase, como en `test-data.sql`. Del
mes en curso se descartan los que caen después de `now()`: el resultado depende del día en que se
cargue, pero dos cargas el mismo día dan el mismo estado, que es lo que pide el criterio. Descartado:
generar montos con `random()`, que haría cada carga distinta y la demo imposible de repetir.

### 5. Front y back separados por usuario dentro de la misma base

El riesgo lo señaló el usuario al revisar la propuesta: la base local la usan las pruebas del front y
las del back, y una no debe ensuciar a la otra. El API ya aísla todo por usuario (lo cubren los
escenarios de aislamiento de `reportes` y `bruno/reports/otro-usuario-*`), así que basta con que
cada lado tenga sus usuarios y su script los limpie:

| Lado | Usuarios | Los recarga o limpia |
|---|---|---|
| Back (`bruno/`) | `prueba@`, `inactivo@financeapp.local`, `*@bruno.local` | `test-data.sql` |
| Front | `demo@financeapp.local`, `*@front.local` | `demo-data.sql` |

Los dos riesgos que quedan son de uso, no de datos: que el front entre como `prueba@` y le cambie
los totales a la colección (se documenta; además `verificar-bruno.ps1 -RecargarDatos` recarga ese
usuario antes de correr), y que registre usuarios con otro dominio (no se borran solos; un
vaciar la base los limpia).

Descartado por ahora, a elección del usuario: una base local aparte para el front
(`financeapp_demo`). Aísla del todo, pero duplica la carga del esquema y cada `update/` habría que
aplicarlo dos veces. Queda como tarea nueva junto con el escenario más grande.

## Risks / Trade-offs

- [El primer día del mes, el mes en curso casi no tiene movimientos] → Es lo que vería un usuario
  real ese día; los cinco meses anteriores siguen completos. Si estorba en una demo, basta cargarlo
  otro día o mover el desfase de los fijos.
- [Un cambio de esquema rompe el script sin que la suite lo note] → Igual que `test-data.sql`. La
  verificación de este change lo carga y consulta los reportes; un cambio futuro de esquema lo
  detecta quien lo cargue, porque el script corre con `ON_ERROR_STOP`.
- [Vaciar la base también borra la demo] → Se documenta en `AGENTS.md`: después de un vaciado se
  recargan los dos scripts.
