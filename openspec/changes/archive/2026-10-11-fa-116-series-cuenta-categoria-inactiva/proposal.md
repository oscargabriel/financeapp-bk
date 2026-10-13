# Proposal

Origen: FA-116. Surgió al implementar FA-107 (series recurrentes, PR #55), que la dejó fuera de
alcance. FA-115 ya resolvió la parte de «borrar una cuenta» para los movimientos programados.

## Why

Una serie guarda su cuenta y su categoría, y la puesta al día de las series sin fin crea ocurrencias
sin volver a validarlas. Hoy, si el usuario desactiva la cuenta de una serie activa o borra su
categoría, la serie sigue creando ocurrencias sobre ellas, y las futuras ya creadas siguen ahí.

Con la cuenta borrada pasa lo mismo, por un hueco que dejó FA-115. Ese cambio bloquea el borrado
por los programados, pero si el usuario borra a mano la ocurrencia programada de una serie sin fin,
la cuenta queda sin programados y se puede borrar. Después la serie sigue creando ocurrencias en la
cuenta borrada.

## What Changes

- Decisión tomada con el usuario el 11-10-2026, entre tres opciones:
  - rechazar el cambio con 409 (la elegida);
  - cancelar la serie;
  - pausarla hasta que se reactive la cuenta.

  El porqué está en `design.md`.
- **No se desactiva una cuenta con series activas.** `PATCH /api/accounts/{id}` con
  `{"isActive": false}` sobre una cuenta activa responde 409 `RESOURCE_IN_USE` en `isActive`, y su
  `description` dice cuántas series tiene. Nada del parche se aplica.
- **No se borra una categoría con series activas.** `DELETE /api/categories/{id}` responde 409
  `RESOURCE_IN_USE` en `id` y no borra nada.
- **Tampoco se borra una cuenta con series activas,** aunque no tenga programados. El error de `id`
  de FA-115 sigue siendo uno solo: su `description` dice cuántas series y cuántos programados tiene.
- **Serie activa** significa lo mismo que en el listado de series: no cancelada y con ocurrencias
  por venir.
- **La puesta al día no crea ocurrencias** para una serie sin fin si su cuenta está desactivada o
  borrada, o si su categoría está borrada. Con las reglas de arriba, eso solo pasa con datos de
  antes de este cambio o con una carrera. Esas series tampoco aparecen en `GET /api/recurrences`:
  no tienen ocurrencias por venir, igual que una serie terminada.
- `contrato-api.md`, la colección `bruno/` y los `docs` de `bruno-personal/` al día.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `cuentas`:
  - requisito nuevo «Solo se desactiva una cuenta sin series activas»;
  - se modifica «Solo se borra una cuenta sin movimientos programados» para que cuente también las
    series activas.
- `categorias`: requisito nuevo «Solo se borra una categoría sin series activas».
- `recurrentes`:
  - requisito nuevo «La puesta al día respeta la cuenta y la categoría»;
  - se modifica «Listado de series activas» para excluir las series sin fin que no pueden crear
    ocurrencias.

## Impact

- Código:
  - `UpdateAccountUseCase`, `DeleteAccountUseCase` y `DeleteCategoryUseCase` consultan las series
    activas;
  - dos métodos nuevos en `RecurrenceRepositoryPort` que las cuentan, por cuenta y por categoría;
  - en `RecurrenceR2dbcAdapter`, esos dos métodos y las consultas de la puesta al día y del listado.
- API:
  - un 409 nuevo en `PATCH /api/accounts/{id}` y otro en `DELETE /api/categories/{id}`;
  - más casos del 409 que ya existía en `DELETE /api/accounts/{id}`.

  No hay códigos nuevos, y no cambian rutas, cuerpos ni autenticación.
- Base de datos: sin cambios de esquema. `test-data.sql` siembra tres series sin fin atrasadas: una
  sobre una cuenta desactivada, otra sobre una cuenta borrada y otra con una categoría borrada.
- FA-117 (cuotas sobre una tarjeta desactivada o borrada) puede seguir este mismo patrón, pero no se
  toca aquí.

## Fuera de alcance

- Las compras en cuotas sobre una tarjeta desactivada → FA-117.
- Desactivar una cuenta que tiene movimientos programados sueltos, sin serie. La regla vigente dice
  que el cambio de estado no toca el saldo ni los movimientos, y un programado suelto no se repite.
- Qué hacer con las series de datos viejos que ya quedaron sobre una cuenta o categoría no válida.
  Dejan de crear ocurrencias y salen del listado, pero no se cancelan. Si la cuenta se reactiva, la
  siguiente lectura crea las ocurrencias atrasadas (ver `design.md`, *Risks*).
- Una restricción en la base que respalde las reglas. La comprobación vive en los casos de uso, como
  la del saldo y la de los programados.
