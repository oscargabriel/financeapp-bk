# Design

## Context

`finance.categories` ya existe: `applies_to` con DEFAULT `'EXPENSE'` y un CHECK de tres valores,
`sort_order` con DEFAULT 0, y el índice único `ux_categories_user_name` sobre
`(user_id, lower(name)) WHERE deleted_at IS NULL`. La lectura ya está armada
(`CategoryQueryPort`, `CategoryR2dbcAdapter`, `CategoryResponse`), y `POST /api/accounts` (FA-23)
es el patrón de alta a imitar: formato en el record de request, normalización e id v7 en el caso de
uso, y el choque de nombre traducido a 409 en el adapter.

## Goals / Non-Goals

**Goals:**
- Alta con el mismo reparto de validación y el mismo 409 que el alta de cuentas.
- Que la categoría nueva salga al final del listado sin otra consulta.

**Non-Goals:**
- Exponer `sort_order` en el contrato.
- Probar en esta tarea el nombre liberado por un borrado (ver Riesgos).

## Decisions

### `appliesTo` obligatorio, aunque la columna tenga DEFAULT

Un cliente que olvide el campo crearía una categoría de gasto sin enterarse, y después no podría
usarla en un ingreso sin saber por qué. Se descartó aceptar el DEFAULT de la columna. Se valida con
`@NotBlank` y `@ValorDeEnum(CategoryScope)`, que no distingue mayúsculas, como el `type` del alta de
cuentas; el query param `appliesTo` del listado sigue exigiendo mayúsculas exactas.

### `sort_order` calculado en el mismo INSERT

```sql
INSERT INTO finance.categories (id, user_id, name, applies_to, icon, color, sort_order)
SELECT :id, :userId, :name, :appliesTo, :icon, :color,
       COALESCE((SELECT max(sort_order) FROM finance.categories
                  WHERE user_id = :userId AND deleted_at IS NULL), 0) + 10
RETURNING id, name, applies_to, icon, color, is_system
```

Se descartaron el DEFAULT 0 (la categoría nueva saldría primera, delante de `Mercado`) y una
consulta previa desde el caso de uso (otro viaje a la base y otro método de puerto, para el mismo
resultado). El máximo cuenta solo las vivas: una borrada no ocupa lugar en la lista.

### Duplicado detectado por el índice, no por un SELECT previo

Igual que en cuentas: el INSERT choca con `ux_categories_user_name` y el adapter traduce
`DuplicateKeyException` a `BadRequestException` 409 `DUPLICATE_RESOURCE` sobre `name`. Un SELECT
previo dejaría una carrera entre la consulta y el INSERT y no ahorraría el manejo del choque. Como
el índice es parcial, el caso "nombre de una categoría borrada" lo resuelve la base sin código.

### Normalización en el caso de uso

`name` recortado; `appliesTo` a `CategoryScope`; `icon` recortado y `null` si queda vacío; `color`
recortado, en mayúsculas como la semilla, y `null` si queda vacío. El patrón de color va en
`Formatos` y acepta vacío, como los demás, para que el campo opcional en blanco no dé error.

## Risks / Trade-offs

- **Dos altas simultáneas pueden recibir el mismo `sort_order`.** El listado desempata por
  `lower(name)`, así que solo cambia el orden relativo entre esas dos. Se acepta: un usuario por
  cuenta, sin escritores concurrentes en la práctica.
- **`sort_order` es SMALLINT.** Con saltos de 10 se desborda pasadas unas 3.200 categorías vivas y
  el INSERT fallaría con 500. No se protege: no es un volumen real para un usuario.
- **El nombre liberado por un borrado no se prueba en Bruno en esta tarea.** La colección no
  escribe sobre el usuario del escenario, que es el único con una categoría borrada, y el usuario
  nuevo de cada corrida no puede borrar sin el `DELETE` de FA-21. Lo cubre la definición parcial
  del índice en `schema.sql`; el request que borra y vuelve a crear se agrega como criterio a FA-21.
