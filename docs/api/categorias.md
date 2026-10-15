# Contrato del API — categorías

Parte del [contrato del API](contrato-api.md). Lo común a todos los endpoints —URL base, formato,
autenticación y errores— está en [generalidades.md](generalidades.md).

### `GET /api/categories`

Las categorías activas del usuario del token. **Bearer.**

**Query params**

| Param | Valores | Por defecto | Efecto |
|---|---|---|---|
| `appliesTo` | `EXPENSE`, `INCOME`, `BOTH` | todas | Filtra por el tipo de movimiento que admiten |

El filtro devuelve **las que sirven para ese tipo**. `EXPENSE` trae las de gasto y las `BOTH`.
`INCOME` trae las de ingreso y las `BOTH`. `BOTH` trae solo las `BOTH`. El valor tiene que ir en
mayúsculas: `expense` es 400 `VALIDATION_ERROR` en `appliesTo`.

**200 OK** — en el orden definido para el usuario y luego por nombre.

```json
[
  {
    "id": "0199a1c0-...",
    "name": "Mercado",
    "appliesTo": "EXPENSE",
    "icon": "shopping-cart",
    "color": "#2E7D32",
    "isSystem": true
  }
]
```

| Campo | Significado |
|---|---|
| `icon` | Nombre de un ícono. Nunca es `null`. Las categorías por defecto usan nombres del set [Lucide](https://lucide.dev/icons) (`shopping-cart`, `heart-pulse`, `wallet`); el API no los valida |
| `color` | `#RRGGBB`. Nunca es `null` |
| `isSystem` | `true` si vino de las categorías por defecto al registrarse |

Para el selector de categoría de un movimiento, pide `appliesTo=EXPENSE` en un gasto e
`appliesTo=INCOME` en un ingreso. Así solo aparecen las que el alta va a aceptar.

### `POST /api/categories`

Crea una categoría del usuario del token. **Bearer.**

**Cuerpo**

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `name` | string | sí | Hasta 60 caracteres. Se recorta. Único entre las categorías vivas del usuario sin distinguir mayúsculas |
| `appliesTo` | string | sí | `EXPENSE`, `INCOME` o `BOTH`. Aquí, a diferencia del filtro de `GET`, se acepta en minúsculas |
| `icon` | string | sí | Hasta 40 caracteres, no en blanco. Se recorta |
| `color` | string | sí | `#RRGGBB`, no en blanco. Se guarda en mayúsculas |

```json
{
  "name": "Plantas",
  "appliesTo": "EXPENSE",
  "icon": "sprout",
  "color": "#7CB342"
}
```

**201 Created** — la categoría con la misma forma de un elemento de `GET /categories`, con
`isSystem` en `false`. Queda **al final** de la lista del usuario: después de todas las que ya
tenía, incluidas las de la semilla.

**Errores:** 400 `VALIDATION_ERROR` por campo, todos en la misma respuesta; 409
`DUPLICATE_RESOURCE` en `name` si otra categoría viva del usuario ya usa ese nombre, también una de
la semilla. El nombre de una categoría borrada sí se puede reutilizar.

### `PATCH /api/categories/{id}`

Modifica una categoría del usuario del token. **Bearer.** Se envían solo los campos que cambian.
Las categorías de la semilla se modifican igual que las propias, y siguen con `isSystem` en `true`.

**Parche**

| Campo | Tipo | Reglas |
|---|---|---|
| `name` | string | Hasta 60 caracteres, no en blanco. Se recorta. Único entre las categorías vivas del usuario sin distinguir mayúsculas |
| `appliesTo` | string | `EXPENSE`, `INCOME` o `BOTH`, en cualquier caja |
| `icon` | string | Hasta 40 caracteres, no en blanco |
| `color` | string | `#RRGGBB`, no en blanco. Se guarda en mayúsculas |

- **Ausente o `null` es "no cambia".** Ningún campo se puede vaciar: un `icon` o un `color` en
  blanco son 400, igual que en el alta.
- Un parche sin ningún campo (`{}`, o todo en `null`) es 400 en `body`.
- Cambiar solo las mayúsculas del nombre de la propia categoría no es un choque.
- Pasar `appliesTo` a `EXPENSE` cuando la categoría tiene ingresos, o a `INCOME` cuando tiene gastos,
  es 409 y no cambia nada del parche. Pasar a `BOTH` siempre se puede.
- La categoría conserva su lugar en la lista.

```json
{
  "name": "Huerta",
  "icon": "leaf",
  "color": "#558B2F"
}
```

**200 OK** — la categoría completa como quedó, con la misma forma que un elemento de
`GET /categories`.

**Errores propios**

| HTTP | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `id` | El id de la ruta no es un UUID |
| 400 | `VALIDATION_ERROR` | `body` | El parche no trae ningún campo |
| 400 | `VALIDATION_ERROR` | el campo | Formato inválido o campo en blanco, todos en la misma respuesta |
| 404 | `NOT_FOUND` | `id` | La categoría no existe, está borrada o es de otro usuario: la respuesta es la misma |
| 409 | `DUPLICATE_RESOURCE` | `name` | Otra categoría viva del usuario ya usa ese nombre |
| 409 | `RESOURCE_IN_USE` | `appliesTo` | La categoría tiene movimientos del tipo que el alcance nuevo dejaría fuera |

### `DELETE /api/categories/{id}`

Borra una categoría del usuario del token. **Bearer.** El borrado es lógico: la categoría deja de
aparecer en `GET /categories` y en el catálogo, pero no desaparece de la historia. No hay forma de
recuperarla desde el API.

**204 No Content**, sin cuerpo.

- Se puede borrar aunque tenga movimientos, y también una de la semilla.
- No se puede borrar mientras la use alguna serie activa —las de `GET /api/recurrences`—: 409
  `RESOURCE_IN_USE` en `id`, con la cantidad de series en la `description` (FA-116). Hay que cancelar
  cada serie o cambiarle la categoría con `PATCH /api/recurrences/{id}`.
- Sus movimientos no cambian: conservan su `categoryId`, y el reporte de movimientos los sigue
  mostrando con el `categoryName` que tenía.
- Un PATCH de uno de esos movimientos que no cambie su categoría ni su tipo se acepta. Ningún
  movimiento nuevo, ni un PATCH que la elija, puede usarla: es el mismo 400 de una categoría que no
  existe.
- Su nombre queda libre para crear o renombrar otra.

| HTTP | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `id` | El id de la ruta no es un UUID |
| 404 | `NOT_FOUND` | `id` | La categoría no existe, ya está borrada o es de otro usuario: la respuesta es la misma |
| 409 | `RESOURCE_IN_USE` | `id` | La categoría la usa alguna serie activa |
