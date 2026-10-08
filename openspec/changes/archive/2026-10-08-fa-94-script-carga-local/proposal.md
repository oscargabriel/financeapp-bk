# Proposal

Origen: FA-94. Nació en FA-93 como «base separada para el front y escenario más grande», y el usuario
la redefinió al revisarla el 08-10-2026: «ya lo revise, ambos se ejecutan sobre la misma base de
datos, de momento trabajemos con lo que se necesita en front mas adelante cuando la aplicacion este
mas madura poblamos mas las tablas, en cuanto a modificaciones de la base de datos estoy pensando en
un script para poblar las tablas asi sirve tanto para el front sus pruebas como para yo hacer mis
puebas, tambien tener un correo para mi donde pueda tener datos para pruebas, si se modifican las
tablas se avisa que se reiniciaron los datos, de momento solo se estan haciendo solo select todavia
no a escalado a modificaciones de datos». Después eligió el correo `dev@financeapp.local`, los mismos
datos que la demo, un solo comando para todo y avisar el reinicio solo en el script y el PR.

## Why

Hoy los datos de la base local se cargan con dos scripts sueltos, y el usuario no tiene un usuario
propio para probar: `prueba@` es de `bruno/` y sus totales no se pueden tocar, y `demo@` es del
front. Cuando un `update/` cambie una tabla, alguien tiene que acordarse de recargar todo y de
avisar. FA-95 dejó escrito que la demo no se recarga sin acordarlo, pero como el front solo hace
SELECT, recargarla no le borra nada.

## What Changes

- `demo-data.sql` se parametriza con variables de psql (correo y un dígito para los ids). Corrido
  solo, sigue cargando `demo@financeapp.local` con los mismos ids y limpiando los `@front.local`.
- `docs/database/cargar-datos-local.sql`, nuevo: el comando único. Carga `test-data.sql`, la demo de
  `demo@financeapp.local` y la misma demo para `dev@financeapp.local`, con ids propios. Se puede
  correr varias veces.
- `AGENTS.md`, *Base de datos*:
  - el comando de carga y los tres lados de la tabla de usuarios (back, front y `dev@`);
  - el front solo lee por ahora, así que recargar está permitido. Esto reemplaza el párrafo de
    FA-95;
  - un `update/` que modifique tablas reinicia los datos de la base local con el comando de carga,
    y lo dice en su encabezado y en el PR.

## Capabilities

### New Capabilities

Ninguna (`skip_specs: true`): son datos y documentación de la base local.

### Modified Capabilities

Ninguna.

## Fuera de alcance

- Poblar más las tablas cuando la app madure: FA-96.
- Una base local aparte para el front: descartada por el usuario.
- Avisar en el tablero de Notion: el usuario eligió que el aviso vaya solo en el script y el PR.
- Cambiar `verificar-bruno.ps1`: sigue recargando solo `test-data.sql`, que no toca a `demo@` ni a
  `dev@`.

## Impact

- `docs/database/demo-data.sql`, `docs/database/cargar-datos-local.sql` (nuevo), `AGENTS.md` y
  `docs/database/modelo-datos.md`.
- Sin cambios en `src/`, en el esquema ni en `bruno/`.
