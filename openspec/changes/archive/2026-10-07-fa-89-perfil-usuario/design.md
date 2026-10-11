# Design

## Context

El usuario vive en `finance.users`, que ya tiene `phone VARCHAR(20)`. Hoy solo lo tocan el alta
(`RegisterUserUseCase`, que inserta) y el login (`findActiveByEmail`, que lee id y hash). El usuario
de cada petición sale del `sub` del token (`UsuarioDelToken`), y las cuentas ya tienen un `PATCH`
cuyo patrón se imita: todo opcional, `null` no cambia nada y un parche vacío da 400 en `body`.

## Decisiones

### La contraseña actual equivocada da 400, no 401

`currentPassword` incorrecta responde **400 `INVALID_CREDENTIALS` en `currentPassword`**.

- Se descartó **401**: el front trata un 401 como sesión vencida (FA-74) y cerraría la sesión de
  alguien que solo se equivocó al escribir. El token sí es válido.
- Se descartó **403**: dice "no tienes permiso", y el usuario lo tiene; lo que falló es un dato del
  cuerpo.

El code `INVALID_CREDENTIALS` es el mismo del login, así que el front lo reconoce. El campo lo
distingue: `currentPassword` aquí, `credentials` en el login.

### `baseCurrencyCode` se omite, no se rechaza

El `PATCH` de cuentas rechaza con `@Null` los campos que no se pueden cambiar (`type`,
`currentBalance`), porque ignorarlos haría creer al cliente que los cambió. Aquí el usuario decidió
lo contrario para `baseCurrencyCode`, el 07-10-2026.

El formulario del perfil puede mandar el perfil completo que leyó, y la moneda va a ser editable
cuando exista FA-91. Rechazarla obligaría al front a quitarla del cuerpo ahora y a volver a ponerla
después. Como la respuesta trae la moneda que quedó, el cliente ve que no cambió.

No se valida el formato del campo omitido: validarlo sería tratarlo como parte del contrato.

### Borrar `lastName` y `phone` con el texto en blanco

Con `null` como "no cambia", que es la convención de los parches del proyecto, hace falta otra forma
de vaciar un campo opcional. Se eligió **el texto en blanco**, que es lo que manda un formulario
cuando el usuario borra el campo.

- Se descartó **JSON Merge Patch**, donde `null` borra: rompería la convención de los otros parches.
- Se descartó **un campo aparte** (`clearPhone`): agrega contrato para un caso que el blanco ya cubre.

`firstName`, `email` y `timezone` son obligatorios en la tabla, así que en blanco siguen siendo
error.

### Cuándo se exige la contraseña para el correo

Solo si el correo normalizado (`trim` y minúsculas, como lo guarda el alta) difiere del guardado. Si
el parche trae `currentPassword` sin cambiar el correo, se ignora: no se verifica, para no convertir
el `PATCH` en un oráculo de contraseñas sin necesidad. El caso de uso compara contra el usuario leído
de la base, no contra el token, porque el token no lleva el correo.

### Un usuario desactivado o borrado con token vigente da 401

El JWT dura hasta su expiración aunque el usuario se desactive. Las consultas del perfil filtran
`is_active` y `deleted_at IS NULL`, igual que `findActiveByEmail`, y "no hay usuario" se traduce al
mismo 401 `UNAUTHENTICATED` de `UsuarioDelToken`.

- Se descartó **404**: diría que el recurso no existe cuando lo que falla es la identidad.

### El perfil es un modelo propio, sin hash

La lectura devuelve un record de dominio `UserProfile` (sin `passwordHash`) y no `User`. Mismo
criterio que `UserCredentials` en el login: cada consulta trae solo lo que necesita. La verificación
de la contraseña usa una consulta aparte del hash por id, solo cuando hace falta.

### Correo duplicado por carrera

Como en el alta: el caso de uso pregunta si el correo está libre (excluyendo al propio usuario), y el
adapter traduce la violación del único `ux_users_email` al mismo 409. Así un cambio concurrente no
sale como 500.

### Rutas

`/api/users/me` y `/api/users/me/password`. `me` deja claro que el usuario no se elige y deja libre
`/api/users/{id}` por si algún día hay administración. La contraseña va con `PUT` porque reemplaza el
valor entero y no es parte del recurso que devuelve el `GET`.

## Riesgos

- Un token robado sigue sirviendo después de cambiar la contraseña hasta que vence (1 h). Fuera de
  alcance: FA-17.
- Desde un token válido se puede probar `currentPassword` sin límite de intentos, igual que el login
  desde fuera. Fuera de alcance; se anota en la proposal.
