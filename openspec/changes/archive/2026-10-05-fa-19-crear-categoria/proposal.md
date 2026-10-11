# Proposal

Origen: FA-19. Al tomarla se acordó en el chat (05-10-2026) que `appliesTo` es obligatorio, que la
categoría nueva queda al final de la lista del usuario, y que el caso "nombre duplicado pero
borrado" se verifica en Bruno dentro de FA-21, cuando exista el `DELETE`.

## Why

Hoy el usuario solo tiene las categorías que le copió el registro. Si un gasto no encaja en
ninguna, no hay cómo crear una propia desde el API: hay que insertarla con psql.

## What Changes

- `POST /api/categories`: crea una categoría del usuario del token con `name`, `appliesTo` y,
  opcionales, `icon` y `color`. Responde 201 con el mismo contrato de `GET /api/categories`.
- El id es un UUID v7 generado en la aplicación y `is_system` queda en `FALSE`.
- El nombre se recorta; el color se guarda en mayúsculas; un `icon` o `color` en blanco se guarda
  como `null`. `appliesTo` acepta mayúsculas o minúsculas, como el `type` del alta de cuentas.
- La categoría nueva queda al final del listado: `sort_order` es el mayor de las categorías vivas
  del usuario más 10.
- Un nombre que ya usa otra categoría viva del usuario, sin distinguir mayúsculas, responde 409
  `DUPLICATE_RESOURCE` sobre `name`. Uno que solo usa una categoría borrada se puede reutilizar: el
  índice único es parcial.

No es un cambio incompatible: ningún endpoint existente cambia de contrato.

## Capabilities

### New Capabilities

- `categorias`: las categorías propias del usuario. Este change trae solo el alta; el listado
  (`GET /api/categories`) entra a la spec cuando un change lo toque.

### Modified Capabilities

Ninguna.

## Fuera de alcance

- Fijar o cambiar `sort_order` desde el cliente, o reordenar la lista: no está en la tarea.
- Modificar y borrar categorías: FA-20 y FA-21.
- La prueba en Bruno de que un nombre liberado por un borrado lógico se reutiliza: necesita el
  `DELETE` de FA-21, y hacerla hoy obligaría a escribir sobre el usuario del escenario, cosa que la
  colección no hace. Se agrega como criterio a FA-21.
- Validar `icon` contra el set de Lucide: el contrato ya dice que el API no lo valida.

## Impact

- Dominio: `CreateCategoryPort`, `CreateCategoryCommand`, `NewCategory` y un puerto de salida
  `CategoryRepositoryPort` con `create`.
- Aplicación: `CreateCategoryUseCase`.
- Web: `POST` en `CategoryController` y un `CreateCategoryRequest` con Bean Validation; un patrón
  de color nuevo en `Formatos`.
- Persistencia: `CategoryR2dbcAdapter` con el `INSERT ... RETURNING` y la traducción del choque
  del índice único a 409.
- `bruno/categories/`: requests nuevos con el usuario nuevo de cada corrida; replicado en
  `bruno-personal/categorias/`.
- `docs/api/contrato-api.md`: el endpoint nuevo y el índice.
- Sin cambios en la base de datos.
