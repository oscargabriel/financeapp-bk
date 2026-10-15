# Design

## Context

FIN-52 dejó tres piezas que este change reutiliza y, en un punto, revierte:

- **Usuarios sin contraseña.** `finance.users.password_hash` es nullable y `google_sub` tiene un
  índice único parcial. `ck_users_metodo` garantiza que un usuario sin contraseña tiene `google_sub`.
- **La verificación del ID token.** La hace `GoogleIdTokenVerifierPort`, que devuelve
  `GoogleIdentity`:
  - con un token inválido, sale vacío;
  - con las llaves de Google inalcanzables, da el 502;
  - en local hay un stub con `GOOGLE_PROVEEDOR=stub`.
- **Un provisional que este change revierte** (decisión 7 de FIN-52).
  `UserRepositoryPort.findActivePasswordHash` devuelve `coalesce(password_hash, '')`, y el texto
  vacío no coincide con ninguna contraseña. Por eso el cambio de contraseña y el del correo le
  responden a ese usuario 400 `INVALID_CREDENTIALS`.

El cambio de contraseña (`ACTUALIZAR_CLAVE`) sube `token_version` en la misma sentencia (FIN-47),
lo que cierra todas las sesiones.

## Goals / Non-Goals

**Goals:**
- Que el perfil se lea sin traer nunca el hash.
- Que la primera contraseña no se pueda definir dos veces ni pisar una existente, ni siquiera con dos
  peticiones simultáneas.

**Non-Goals:**
- **Que la regla de recencia sea configurable.** Es una constante; si hace falta moverla, se
  convierte en propiedad.
- **Aplicar la recencia al login o al registro con Google.**

## Decisions

### 1. Una ruta propia: `POST /api/users/me/password`

Definir la primera contraseña y cambiarla son operaciones distintas: cada una pide una prueba de
identidad distinta y aplica a un estado distinto del usuario. Una ruta propia mantiene simple cada
cuerpo y cada validación. `POST` porque crea el valor que `PUT` reemplaza. Va con **Bearer**, como el
resto de `/users/me`: el que actúa es el usuario de la sesión, y el ID token es la prueba adicional.

Descartadas:
- **El mismo `PUT` con `idToken` en lugar de `currentPassword`.** Un cuerpo con dos formas
  excluyentes obliga a una constraint cruzada, y el error de «sobra uno / falta otro» confunde al
  cliente.
- **`POST /api/auth/google/password` en la cadena Basic.** Tendría que identificar al usuario solo por
  el ID token: cualquiera con la credencial compartida y un token de Google le pondría contraseña a
  esa cuenta sin tener su sesión, y eso debilita la protección que pide la tarea.

### 2. «Reciente» es un `iat` a 5 minutos o menos de ahora, comprobado en el caso de uso

El ID token de Google vale una hora. Sin una regla de recencia, un token capturado en el login
serviría durante esa hora para definir la contraseña, y la tarea pide confirmar la identidad **en ese
momento**. Se exige que `iat` esté entre `ahora - 5 min` y `ahora + 5 min`, con el `Clock` de la app.

- **El límite superior tiene dos motivos.** El desfase de reloj con Google: un token recién emitido
  puede traer un `iat` unos segundos adelante de nuestro reloj, y no debe rechazarse. Y un hallazgo
  de la implementación: si el token no trae `iat`, `MappedJwtClaimSetConverter` de Spring Security lo
  llena con `exp - 1 s`, una hora en el futuro, así que aceptar cualquier `iat` futuro dejaría pasar
  un token sin `iat`. Google siempre lo manda; `GoogleIdTokenVerifierAdapterTest` fija ese
  comportamiento de Spring. El vencimiento lo sigue verificando el decoder.
- **Dónde vive cada cosa.** `GoogleIdentity` agrega `emitidoEn` (`Instant`); el verificador lo saca
  del claim `iat`. La regla vive en el caso de uso porque es del negocio de esta operación, no de la
  verificación del token: el login y el registro no la aplican.
- **El stub acepta un sexto campo opcional**, `stub|sub|email|nombre|apellido|<iat en segundos>`. Sin
  él, el token sale emitido «ahora» según el `Clock`. Con él, `bruno/` prueba el token viejo.

Descartadas:
- **`max_age` / `auth_time` de OpenID.** Google Identity Services no los expone en el botón ni en
  One Tap.
- **Una ventana configurable.** Nada la necesita hoy.

### 3. Los errores del ID token son 400, no 401

La ruta va en la cadena Bearer, y el cliente trata un 401 como sesión vencida: refresca o cierra
sesión. Un ID token inválido, viejo o de otra cuenta responde 400 `INVALID_CREDENTIALS` en `idToken`,
el mismo criterio que la contraseña actual equivocada del `PUT`. Por eso el error no se reutiliza de
`ErroresDeGoogle.tokenInvalido()`, que es 401: se agrega una variante con 400. El 502 por llaves
inalcanzables viene del adapter y no cambia.

### 4. Los usuarios sin contraseña reciben 409 `INVALID_STATE`, sin un código nuevo

Dos casos reciben este error: definir la primera contraseña cuando ya hay una, y cambiar la
contraseña o el correo cuando no hay ninguna. Los dos son una acción que el estado del usuario no
admite, que es la definición de `INVALID_STATE` en el catálogo del contrato, y el cliente los
distingue por la ruta y el campo. El campo es el que el cliente tendría que corregir: `newPassword` en
el `POST` y `currentPassword` en el `PUT` y el `PATCH`.

Descartado: un código nuevo, como `PASSWORD_NOT_SET`. Agrega un caso al catálogo para lo que
`INVALID_STATE` ya describe.

### 5. El estado se comprueba antes de verificar el token

En el `POST`, «ya tiene contraseña» se resuelve antes de llamar a Google: no tiene sentido verificar
un token para una operación que de todos modos se va a rechazar. No filtra nada, porque es el estado
del propio usuario y el perfil ya lo dice.

En el `PATCH`, el estado va antes que la presencia de `currentPassword`. Así, un usuario sin
contraseña que no la envía recibe el 409 que le dice qué hacer, no un `VALIDATION_ERROR` que le pide
una contraseña que no tiene. El paso queda dentro del de «contraseña actual» del orden de validaciones
del parche; el orden entre pasos no cambia.

En el `PUT`, `currentPassword` sigue siendo `@NotBlank` en el DTO: un usuario sin contraseña que la
manda vacía recibe primero ese 400. Es un caso que el front no produce, porque con
`hasPassword: false` no muestra ese formulario.

### 6. El puerto lee las credenciales por id y se quita el `coalesce`

`findActivePasswordHash(UUID)` se reemplaza por `findActiveCredentials(UUID)`, que devuelve el mismo
`UserCredentials` del login: `passwordHash` nulo si no tiene contraseña, y `googleSub`. Así, los tres
casos de uso que hoy necesitan saber si el usuario tiene contraseña reciben el dato sin ambigüedad:
el cambio de contraseña, el del correo y la definición de la primera. El `coalesce` de `HASH_ACTIVO`
desaparece con esa consulta.

Descartados:
- **`Mono<Optional<String>>`.** Pierde `googleSub`, que el `POST` necesita para comparar con el
  token.
- **Mantener el texto vacío.** Es justamente lo que impide distinguir «sin contraseña» de «contraseña
  equivocada».

### 7. La primera contraseña se escribe con una condición

Una sentencia nueva hace el cambio y devuelve si actualizó una fila; cero filas es el 409:

```sql
UPDATE finance.users
   SET password_hash = :passwordHash, token_version = token_version + 1
 WHERE id = :id AND password_hash IS NULL AND google_sub = :googleSub
   AND deleted_at IS NULL AND is_active
```

- **Dos definiciones simultáneas no se pisan:** la segunda no encuentra `password_hash IS NULL`.
- **Sube `token_version` en la misma sentencia**, como `ACTUALIZAR_CLAVE`.
- **Comparar `google_sub` aquí repite la comprobación del caso de uso.** Hace que la escritura sea
  segura aunque el caso de uso cambie.

### 8. `hasPassword` y `googleLinked` se calculan en la consulta del perfil

`PERFIL_ACTIVO` y el `RETURNING` de `ACTUALIZAR_PERFIL` agregan `password_hash IS NOT NULL` y
`google_sub IS NOT NULL`. Ni el hash ni el `sub` salen de la base al leer el perfil, igual que hoy
`UserProfile` no trae el hash. `UserProfile` suma los dos booleanos, `UpdateUserProfileUseCase`
los copia sin cambiarlos, y `UserProfileResponse` los expone.

## Risks / Trade-offs

- **[El usuario se demora más de 5 minutos entre el popup de Google y el envío]** → 400 en `idToken`.
  Mitigación: el contrato dice que el cliente pida el ID token a Google justo al enviar el formulario,
  no al abrirlo.
- **[El 400→409 de `PUT`/`PATCH` cambia el contrato]** → Solo afecta a usuarios sin contraseña, que
  existen desde FIN-52, sin front que los use todavía: FIN-54 y FIN-55 siguen abiertas.
- **[Definir la contraseña cierra la sesión en la que se hizo]** → Es lo que pide la tarea («igual
  que el cambio de contraseña»). El cliente vuelve al login, con Google o con la contraseña nueva, y
  el contrato lo dice.
