# Design

## Context

Los pendientes que otra persona compartió llevan `transactions.shared_by_user_id` (FIN-66), y la
respuesta los muestra con `sharedBy` `{email, firstName, lastName}`, los datos actuales de esa
persona. Rechazar uno lo borra con `BORRAR_PENDIENTE`; descartar un grupo (FIN-58) borra varios con
`BORRAR_PENDIENTES` (`id = ANY(:ids) AND status = 'PENDING'`). Los dos pasan por `conPartes` de
`TransactionR2dbcAdapter`, que en la misma transacción deja `REJECTED` la parte de quien debe y anula
las partes pendientes del gasto de quien pagó.

## Decisions

### El filtro va por correo, no por afiliación

`?sharedBy=<correo>`, comparado con `users.email` de quien compartió. El correo se normaliza como en
el registro (`trim`, `toLowerCase`) y se compara con el actual, el mismo que la respuesta muestra en
`sharedBy.email`.

Descartado: `?affiliationId=<id>`, como `/affiliations/{id}/shares`. Una persona desvinculada que
solo deja ingresos pendientes (de una transferencia o de una parte pagada) no tiene partes
pendientes, así que no sale en `/affiliations` ni en `/affiliations/balances`, y el front no tendría
con qué id filtrarla. El epic pide filtrar también por las desvinculadas. Con el correo, el front saca
las personas de la propia lista de pendientes. Un correo sin pendientes da `[]`, igual tenga o no
cuenta, así que el filtro no revela qué correos están registrados.

El formato se valida en el controlador, como los demás query params (AGENTS.md: los controladores
parsean a mano): vacío, más de 255 caracteres o fuera de `Formatos.EMAIL` es 400 en `sharedBy`. La
normalización va en el caso de uso.

### Ruta y cuerpo del lote

`POST /api/transactions/pending/reject` con `{"ids": [...]}`. No choca con `/{id}/reject`: el
`PathPattern` de Spring prefiere el segmento literal, y hoy esa ruta con `pending` como id responde
400 en `id`.

Descartados:

- una lista pelada como cuerpo, como el alta: el objeto deja lugar a campos futuros sin romper el
  contrato, y los errores salen como `ids[i]`, que se leen solos;
- `DELETE` con cuerpo: muchos clientes y proxies lo descartan, y el rechazo individual ya es un
  `POST`.

### Todo o nada, con 404 antes que 409

Decisión del usuario: si algún id no es un pendiente del usuario, no se rechaza ninguno, como el alta
en lote y la confirmación de un grupo. Así el front sabe que lo que ve después es lo que eligió.

Descartado: rechazar lo que siga pendiente y responder 204, como descartar un grupo. El front no
sabría cuáles quedaron.

Los errores llevan la posición en `ids[i]`. Un id que no está entre los movimientos del usuario
(inexistente, ya rechazado o ajeno) es 404 `NOT_FOUND`; uno propio confirmado, 409 `INVALID_STATE`.
Una respuesta tiene un solo status, así que si hay de los dos sale el 404 con los no encontrados: es
el error que el cliente no puede arreglar mirando sus propios datos, y es el mismo orden que el
rechazo individual (primero se busca, después se mira el estado).

### Una lectura y un borrado

El caso de uso lee los movimientos del usuario con esos ids en una consulta (`id = ANY(:ids) AND
user_id = :userId`), arma los errores por posición, y si no hay ninguno borra con un método nuevo del
repositorio, `deleteAllPending`, que reutiliza `BORRAR_PENDIENTES` y `conPartes` de FIN-58 y es todo
o nada: si borra menos filas que ids, porque otro request aprobó o rechazó alguno en medio, deshace la
transacción —partes incluidas— como `confirmAll`. Esa carrera responde 404 `NOT_FOUND` en `ids`, sin
posición, como el rechazo individual responde 404 cuando la escritura no afecta filas.

Descartados: buscar cada id con `findByIdAndUser` (hasta 100 consultas por petición contra Neon) y
reutilizar `deletePendingIn` tal cual, que borra lo que encuentre y devuelve cuántos: dejaría el lote
a medias en la carrera.

### Validación del cuerpo

En el record de request, como pide AGENTS.md (FA-55): `@NotNull` y `@Size(min = 1, max = 100)` sobre
`ids`, y `@NotNull` y formato de UUID sobre cada elemento. Los repetidos se detectan con una
constraint propia en `dto/validation/` que compara los UUID ya interpretados, para que el mismo id en
mayúsculas y en minúsculas también cuente como repetido. El campo de los errores de elemento tiene que
salir como `ids[i]`; si el handler no lo arma así para un componente de lista, se ajusta allí.

## Risks / Trade-offs

- **El correo en la URL** queda en los logs de acceso del proxy de Cloud Run. Es el correo de la otra
  persona, que el usuario ya ve en su lista. El log de la app escribe la URI completa solo en
  `DEBUG` (`WebConfig.requestLogger`), que el servicio no tiene encendido. Se acepta.
- **El filtro usa el correo actual.** Si la persona cambia su correo, el front tiene que volver a pedir
  la lista para tomar el nuevo, como ya pasa con `sharedBy`.
