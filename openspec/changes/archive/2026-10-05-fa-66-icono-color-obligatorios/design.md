# Design

## Context

`finance.categories.icon` (`VARCHAR(40)`) y `color` (`CHAR(7)`, con CHECK `#RRGGBB`) admiten
`NULL`, igual que en `finance.default_categories`. La semilla trae los dos en sus 22 filas. En el
escenario, "Ajustes" no los tiene a propósito, para probar que el listado los devolvía en `null`.
Desde FA-19 el alta guarda en `null` un icono o un color ausentes o en blanco, así que en Neon
puede haber más filas así.

El esquema se mantiene a mano con doble apunte: `schema.sql` más un update en `docs/database/update/`,
y la comparación de `modelo-datos.md` comprueba que los dos caminos den la misma base. Hasta hoy
solo existe el update de línea base, y `schema.sql` no cambió desde ella.

## Goals / Non-Goals

**Goals:**
- Que ninguna categoría pueda quedar sin icono ni color, ni por el API ni por la base.
- Un update que se pueda aplicar a una base con datos, incluida la de Neon.

**Non-Goals:**
- Aplicar el update en Neon (lo hace el usuario; ver Riesgos).
- Revisar la validación de `icon` contra un set de íconos.

## Decisions

### Rellenar y `NOT NULL`, decidido por el usuario

Se descartaron dejar los `null` existentes hasta que se editen, que obliga al cliente a seguir
contemplando `null` para siempre, y rellenar sin `NOT NULL`, que deja la garantía en manos de que
nadie inserte por psql.

### Valor de relleno: `ellipsis` y `#757575`

Son el icono y el color de "Otros gastos" y "Otros ingresos" en la semilla: el aspecto neutro que
el sistema ya usa para "sin clasificar". Se descartó un icono nuevo, porque no hay ningún otro
precedente en el proyecto.

### `NOT NULL` también en `default_categories`

El registro copia la semilla a `categories` con un `INSERT ... SELECT`. Con `NOT NULL` solo en
`categories`, una fila de semilla sin icono haría fallar cada registro con un error de la base, que
sale como 500. Se pone la restricción en el origen para que el error aparezca al editar la semilla.
`seed.sql` no cambia: ya trae los dos campos en todas las filas.

### El update

`20261005_01_categorias_icono_color_obligatorios.sql`, en una transacción:

```sql
UPDATE finance.default_categories SET icon  = 'ellipsis' WHERE icon  IS NULL;
UPDATE finance.default_categories SET color = '#757575'  WHERE color IS NULL;
UPDATE finance.categories         SET icon  = 'ellipsis' WHERE icon  IS NULL;
UPDATE finance.categories         SET color = '#757575'  WHERE color IS NULL;
ALTER TABLE finance.default_categories ALTER COLUMN icon SET NOT NULL, ALTER COLUMN color SET NOT NULL;
ALTER TABLE finance.categories         ALTER COLUMN icon SET NOT NULL, ALTER COLUMN color SET NOT NULL;
```

Un `UPDATE` por columna, para que una categoría con icono y sin color conserve su icono. Incluye
las categorías borradas: `NOT NULL` aplica a todas las filas. El trigger `updated_at` las marca
como modificadas, lo cual es cierto. Al final lleva comentada la reversión (`DROP NOT NULL`). Los
datos rellenados no se revierten, porque no hay forma de distinguirlos de un `ellipsis` elegido a
propósito.

### El código deja de contemplar `null`

`CreateCategoryRequest` exige `@NotBlank` en los dos, y el patrón de color sigue aceptando el vacío
para que el campo en blanco dé un solo error. `CreateCategoryUseCase` recorta en vez de convertir
el blanco en `null`, y el adapter deja de enlazar `null` en el alta y en el PATCH. El record
`Category` no cambia: sus componentes son `String`, y los tests que construían una categoría sin
icono pasan a darle uno.

## Risks / Trade-offs

- **Orden en producción.** Con la app nueva y la base vieja no pasa nada: la app ya exige los dos
  campos. Con la base nueva y la app vieja, un alta sin icono daría 500. Por eso el update se
  aplica en Neon **después** del despliegue desde `main`, y se dice al cerrar la tarea.
- **Cambio incompatible para clientes.** Un cliente que creaba categorías sin icono empieza a
  recibir 400. Hoy el único cliente es la colección personal, y se actualiza en este cambio.
