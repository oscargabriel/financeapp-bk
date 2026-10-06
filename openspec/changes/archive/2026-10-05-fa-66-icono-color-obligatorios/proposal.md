# Proposal

Origen: FA-66, creada el 05-10-2026 al proponer FA-20, cuando el usuario decidió que una categoría
"tiene que tener algún valor" de icono y color desde el alta. Al tomarla se decidió también
(05-10-2026) rellenar las categorías que ya existen sin ellos y poner `NOT NULL` en la base.

## Why

FA-19 dejó `icon` y `color` opcionales en el alta, y FA-20 los hizo imposibles de vaciar en el
PATCH. El resultado es incoherente: una categoría creada sin color no puede quedar sin color
después, pero sí puede nacer así. El cliente, además, tiene que contemplar `null` en el listado.

## What Changes

- **BREAKING** `POST /api/categories`: `icon` y `color` pasan a ser obligatorios. Sin ellos, o en
  blanco, la respuesta es 400 `VALIDATION_ERROR` sobre cada campo. Antes se guardaban como `null`.
- Ninguna categoría del API vuelve a tener `icon` o `color` en `null`: `GET /api/categories`
  siempre los trae.
- Esquema: `finance.categories.icon` y `color` pasan a `NOT NULL`, y también los de
  `finance.default_categories`, porque el registro copia la semilla a `categories` y una semilla
  sin icono rompería el registro con un error de la base.
- Un update nuevo en `docs/database/update/` rellena los `null` existentes con el icono
  `ellipsis` y el color `#757575` (los de "Otros gastos" de la semilla) y aplica los `NOT NULL`.
  El mismo cambio va en `schema.sql`.
- El escenario de pruebas le da icono y color a "Ajustes", la única categoría que no los tenía.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `categorias`: el alta exige `icon` y `color`, y toda categoría del usuario los tiene.

## Fuera de alcance

- **Aplicar el update en Neon.** Es producción y se aplica a mano con psql, como manda AGENTS.md.
  Lo hace el usuario **después** de que este cambio se despliegue desde `main`: con la app nueva
  arriba y la base vieja no se rompe nada, pero si la base cambia antes que la app, una categoría
  creada sin icono por la app vieja daría 500.
- Cambiar el icono o el color por defecto de una categoría ya existente con valor.
- Validar `icon` contra el set de Lucide.

## Impact

- Web: `CreateCategoryRequest` con `@NotBlank` en `icon` y `color`.
- Aplicación: `CreateCategoryUseCase` deja de convertir el blanco en `null`.
- Persistencia: `CategoryR2dbcAdapter` deja de enlazar `null` en `icon` y `color` (alta y PATCH).
- Base: `schema.sql` y `update/20261005_01_categorias_icono_color_obligatorios.sql`; la
  comparación de esquemas de `modelo-datos.md` se corre después de emitirlo.
- `docs/database/test-data.sql`: "Ajustes" con icono y color.
- Tests: los del request, el caso de uso y los slices que asumían `null`; `CategoryMother`.
- `bruno/categories/`: el alta sin icono ni color pasa a ser 400, y los requests del alta que no
  los mandaban los mandan. `bruno-personal/categorias/crear.yml` actualizado.
- `docs/api/contrato-api.md`: el alta y el listado.
