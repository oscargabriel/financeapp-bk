# Spec Delta

## ADDED Requirements

### Requirement: Solo se borra una categoría sin series activas
Una categoría del usuario con series activas —no canceladas y con ocurrencias por venir, como en
`GET /api/recurrences`— MUST NOT borrarse. La respuesta SHALL ser 409 con un error `RESOURCE_IN_USE`
en el campo `id` cuya `description` dice cuántas series la usan, y nada SHALL cambiar. Al cancelar
esas series o cambiarles la categoría, SHALL poder borrarse.

#### Scenario: Categoría de una serie sin fin
- **WHEN** el usuario borra su categoría `Club`, que usa una serie sin fin activa
- **THEN** la respuesta es 409 con un error `RESOURCE_IN_USE` en el campo `id` que dice que la usa 1 serie activa, y `Club` sigue en `GET /api/categories`

#### Scenario: Borrar tras cancelar la serie
- **WHEN** el usuario cancela con `DELETE /api/recurrences/{id}` la serie que usa `Club` y después borra la categoría
- **THEN** la respuesta es 204

#### Scenario: Una serie terminada no impide el borrado
- **WHEN** la única serie que usa la categoría es una con fin cuyas ocurrencias ya pasaron todas, y el usuario la borra
- **THEN** la respuesta es 204

#### Scenario: Categoría de otro usuario con series
- **WHEN** un usuario recién registrado borra una categoría de otro usuario que usa una serie activa
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `id`
