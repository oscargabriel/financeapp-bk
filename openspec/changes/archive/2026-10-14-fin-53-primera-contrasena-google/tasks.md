# Tasks

## 1. Bruno en RED

Skills: `bruno-cli`.

- [x] 1.1 `bruno/users/ver-perfil.yml`: las claves exactas del perfil suman `hasPassword` y
  `googleLinked`, con `true` y `false` para ese usuario registrado con contraseña.
- [x] 1.2 `bruno/auth-google/`, requests que ya existen:
  - `registro-perfil.yml` trae `hasPassword` `false` y `googleLinked` `true`;
  - `vincular-perfil-tras-cambio.yml` trae los dos en `true`;
  - `cambiar-clave-sin-clave.yml` y `cambiar-correo-sin-clave.yml` pasan a 409 `INVALID_STATE` en
    `currentPassword`, y sus `docs` dejan de decir «provisional».
- [x] 1.3 `bruno/auth-google/`, requests nuevos detrás de `cambiar-correo-sin-clave.yml`:
  - `cambiar-correo-sin-clave-ni-actual.yml`: `PATCH` con otro correo y sin `currentPassword`, 409
    `INVALID_STATE` en `currentPassword`;
  - `modificar-nombre-sin-clave.yml`: `PATCH` con un nombre nuevo, 200 con `hasPassword` `false` y
    `googleLinked` `true`.

  `perfil-tras-intentos.yml` sigue comprobando que el correo no cambió.
- [x] 1.4 `bruno/auth-google/`, la definición de la primera contraseña, al final de la carpeta y en
  este orden. Los tokens del stub llevan un sexto campo `iat` cuando hace falta (decisión 2 de
  design.md):
  - `definir-clave-sin-credencial.yml` y `definir-clave-con-basic.yml`: 401 `UNAUTHENTICATED` en
    `authorization`;
  - `definir-clave-login-previo.yml`: `POST /auth/google/login` con el token del registro; guarda
    `accessDefinir` y `refreshDefinir`;
  - `definir-clave-sin-cuerpo.yml`: 400 `VALIDATION_ERROR` en `idToken` y en `newPassword`;
  - `definir-clave-invalida.yml`: `newPassword` de 7 caracteres, 400 en `newPassword`;
  - `definir-clave-token-invalido.yml`: 400 `INVALID_CREDENTIALS` en `idToken`;
  - `definir-clave-token-viejo.yml`: `iat` de hace 600 s, 400 `INVALID_CREDENTIALS` en `idToken`;
  - `definir-clave-token-futuro.yml`: `iat` una hora adelante, como el que Spring deduce de un token
    sin `iat` (decisión 2), 400 `INVALID_CREDENTIALS` en `idToken`. Se agregó durante la
    implementación, con el hallazgo de esa decisión;
  - `definir-clave-otra-cuenta.yml`: otro `sub`, 400 `INVALID_CREDENTIALS` en `idToken`;
  - `definir-clave-con-clave.yml`: con el access del usuario `vincular-*`, que tiene contraseña, 409
    `INVALID_STATE` en `newPassword`;
  - `definir-clave-acceso-sigue.yml`: `GET /users/me` con `accessDefinir` da 200, porque ningún
    rechazo cerró la sesión;
  - `definir-clave.yml`: 204;
  - `definir-clave-acceso-viejo.yml`: `GET /users/me` con `accessDefinir`, 401 `UNAUTHENTICATED` en
    `authorization`;
  - `definir-clave-refresh-viejo.yml`: `POST /auth/refresh` con `refreshDefinir`, 401
    `UNAUTHENTICATED` en `refreshToken`;
  - `definir-clave-login-clave.yml`: `POST /auth/login` con el correo y la contraseña nueva, 200;
    guarda el access;
  - `definir-clave-perfil.yml`: `hasPassword` `true` y `googleLinked` `true`;
  - `definir-clave-login-google.yml`: `POST /auth/google/login`, 200;
  - `definir-clave-segunda.yml`: con el access del login, 409 `INVALID_STATE` en `newPassword`.
- [x] 1.5 Correr `verificar-bruno.ps1 -RecargarDatos` y anotar aquí el RED: cuántos requests de los
  tocados fallan, y que fallan por la razón esperada (404 de la ruta, 400 en vez de 409, claves que
  faltan en el perfil).

  RED (`-Puerto 8081`, porque el 8080 estaba ocupado): 987 requests, 20 fallan, 1914/1956 asserts.
  Fallan los 20 esperados:
  - el perfil sin `hasPassword`/`googleLinked`: `ver-perfil`, `registro-perfil`,
    `vincular-perfil-tras-cambio`, `modificar-nombre-sin-clave` y `definir-clave-acceso-sigue`;
  - 400 en vez de 409 en los tres `cambiar-*-sin-clave`;
  - 405 de `POST /users/me/password`: hoy solo existe `PUT`. Fallan así los 7 `definir-clave-*` que
    lo llaman con Bearer;
  - en cascada, `acceso-viejo` y `refresh-viejo` (200: nada cerró la sesión) y `login-clave`,
    `perfil` y `segunda` (401: no hay contraseña).

  Pasan ya, como corresponde, `definir-clave-sin-credencial`, `-con-basic`, `-login-previo` y
  `-login-google`.

## 2. Identidad de Google y perfil

Skills: `java-architect`.

- [x] 2.1 `GoogleIdentity` suma `emitidoEn` (`Instant`, del claim `iat`; sin él, Spring pone
  `exp - 1 s`, ver decisión 2).
  - `GoogleIdTokenVerifierAdapter` lo saca de `Jwt.getIssuedAt()`.
  - `StubGoogleIdTokenVerifierAdapter` acepta un sexto campo opcional, en segundos epoch; sin él, usa
    el instante del `Clock`, y un sexto campo que no es número da token inválido.

  Verificar con `GoogleIdentityTest`, `GoogleIdTokenVerifierAdapterTest` (el `iat` del token firmado
  llega a la identidad) y `StubGoogleIdTokenVerifierAdapterTest` (con y sin sexto campo, y con un
  sexto campo inválido). `GoogleMother` se adapta.
- [x] 2.2 `UserProfile` suma `hasPassword` y `googleLinked`. `UserR2dbcAdapter` los calcula en
  `PERFIL_ACTIVO` y en el `RETURNING` de `ACTUALIZAR_PERFIL` (decisión 8). `UserProfileResponse` los
  expone. Verificar con los tests del controlador que leen el perfil (`UserControllerTest`) y con
  `ver-perfil.yml`.
- [x] 2.3 `UserRepositoryPort`:
  - `findActivePasswordHash` se reemplaza por `findActiveCredentials(UUID)`, que devuelve
    `UserCredentials` con el hash nulo si no hay contraseña, y se quita el `coalesce`;
  - método nuevo `setFirstPassword(UUID, String googleSub, String passwordHash)`, que devuelve
    `Mono<Boolean>` con la sentencia condicional de la decisión 7.

  Los adapters R2DBC no tienen test en la suite: los verifican los requests del grupo 1. Que compile
  y que `LoginUseCaseTest` siga en verde.

## 3. Casos de uso

Skills: `java-architect`, `java-exceptions`.

- [x] 3.1 `ErroresDelPerfil`, errores nuevos:
  - `sinContrasena()`: 409 `INVALID_STATE` en `currentPassword`;
  - `yaTieneContrasena()`: 409 `INVALID_STATE` en `newPassword`;
  - token de Google rechazado: 400 `INVALID_CREDENTIALS` en `idToken` (decisión 3).
- [x] 3.2 `ChangePasswordUseCase` usa `findActiveCredentials`; con el hash nulo responde
  `sinContrasena()` y no actualiza nada. Verificar con `ChangePasswordUseCaseTest`, con un test
  nuevo para el usuario sin contraseña, visto en RED primero.
- [x] 3.3 `UpdateUserProfileUseCase`: al cambiar el correo, el estado va antes que la presencia de
  `currentPassword` (decisión 5); sin contraseña, `sinContrasena()` aunque `currentPassword` falte.
  Verificar con `UpdateUserProfileUseCaseTest`, con tests nuevos para los dos casos (con y sin
  `currentPassword`) y para el parche de nombre de un usuario sin contraseña, que pasa.
- [x] 3.4 `FirstPasswordCommand`, `SetFirstPasswordPort` y `SetFirstPasswordUseCase`, en este orden:
  1. usuario activo;
  2. ya tiene contraseña: 409;
  3. verificación del token: vacío da 400, y el 502 del adapter pasa tal cual;
  4. `emitidoEn` nulo o fuera de `ahora ± 5 min` según el `Clock`: 400;
  5. `sub` distinto del vinculado: 400;
  6. `setFirstPassword` con el hash; `false` da 409.

  Verificar con `SetFirstPasswordUseCaseTest`:
  - un test por paso;
  - los bordes de exactamente 5 minutos atrás y adelante (se aceptan), y un `iat` una hora adelante,
    como el que Spring deduce sin `iat` (se rechaza);
  - el 502 que pasa;
  - que con ya tiene contraseña no se llama al verificador.

## 4. Endpoint

Skills: `java-architect`, `java-exceptions`.

- [x] 4.1 `FirstPasswordRequest` (`@NotBlank idToken`, y `newPassword` con las reglas de
  `ChangePasswordRequest`) y `POST /users/me/password` en `UserController`, que responde 204.
  Verificar con `UserControllerTest`:
  - el 204 y lo que se le pasa al puerto;
  - 400 en cada campo;
  - los errores del caso de uso con su status: 400 `idToken`, 409 `newPassword` y 502.
- [x] 4.2 `UsersIT`: `POST /api/users/me/password` se suma a los dos `@ValueSource` de 401 (sin
  credencial y con el Basic). No hace falta tocar `SecurityConfig`: la ruta cae en la cadena Bearer.

## 5. Documentación

- [x] 5.1 `docs/api/contrato-api.md`:
  - la fila de la ruta nueva en el índice;
  - `hasPassword` y `googleLinked` en el perfil (`GET` y `PATCH`);
  - la sección de `POST /api/users/me/password`, con cuerpo, errores en orden, el cierre de
    sesiones y que el cliente pida el ID token a Google justo al enviar;
  - `PUT /api/users/me/password` y el `PATCH` del correo con el 409 `INVALID_STATE`, en lugar del
    provisional de FIN-52;
  - la fila `INVALID_STATE` de *Errores* con los dos casos nuevos;
  - la nota del login con contraseña que nombra la vía para definirla.

  Verificar releyendo el contrato contra la delta de spec.
- [x] 5.2 Replicar en `bruno-personal/perfil/` el
  `POST /users/me/password` (ruta, cuerpo con `googleIdToken` y Bearer con `accessToken`), sin
  ejecutarlo: esa colección apunta a Neon y usa Google real.

## 6. Verificación

- [x] 6.1 `.\gradlew.bat build` en verde, con el conteo real de tests y la cobertura de línea
  ≥ 85 %.
- [x] 6.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde: la
  colección entera, ningún request omitido y saldos cuadrados, con los conteos reales.
