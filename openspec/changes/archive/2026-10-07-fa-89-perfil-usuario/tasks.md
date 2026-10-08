# Tasks

## 1. Bruno en RED

Skills: `bruno-cli`.

- [x] 1.1 Crear `bruno/users/` (`folder.yml` con `seq: 9` y `auth: inherit`) con un usuario propio:
  `perfil-<timestamp>@bruno.local`, que `test-data.sql` ya limpia. El token va a `tokenPerfil`, no a
  `accessToken`. Requests, en orden:
  - `alta-con-celular`: register con `" +573001234567 "` → 201 con `phone` `+573001234567`.
  - `alta-celular-invalido`: `"phone": "300 123"` → 400 `VALIDATION_ERROR` en `phone`.
  - `login-perfil` → guarda `tokenPerfil`.
  - `ver-perfil` → 200 con las 7 claves del contrato, sin `password` ni `passwordHash`.
  - `modificar-perfil`: `firstName`, `phone`, `timezone` y `baseCurrencyCode: "USD"` → 200 con los
    cambios y `baseCurrencyCode` `COP`.
  - `perfil-tras-modificar`: el `GET` refleja el `PATCH`.
  - `modificar-perfil-borrar-opcionales`: `{"phone": "", "lastName": " "}` → 200 con los dos en `null`.
  - `modificar-perfil-parche-vacio`: `{}` → 400 en `body`.
  - `modificar-perfil-solo-moneda` → 400 en `body`.
  - `modificar-perfil-campos-invalidos`: `firstName` en blanco, `phone` `12-34`, `email` sin arroba,
    `timezone` `Marte/Base` → 400 con un error por campo.
  - `cambiar-correo-sin-clave` → 400 `VALIDATION_ERROR` en `currentPassword`.
  - `cambiar-correo-clave-equivocada` → 400 `INVALID_CREDENTIALS` en `currentPassword`.
  - `cambiar-correo-ajeno`: `{{testUserEmail}}` en mayúsculas con la clave correcta → 409
    `DUPLICATE_RESOURCE` en `email`.
  - `cambiar-correo`: a `perfil2-<timestamp>@BRUNO.local` → 200 con el correo en minúsculas.
  - `login-correo-nuevo` → 200.
  - `cambiar-clave-equivocada` → 400 `INVALID_CREDENTIALS` en `currentPassword`.
  - `cambiar-clave-invalida`: `newPassword` de 7 caracteres → 400 en `newPassword`.
  - `cambiar-clave` → 204.
  - `login-clave-nueva` → 200. `login-clave-anterior` → 401 `INVALID_CREDENTIALS`.
  - `perfil-sin-credenciales` y `perfil-con-basic` (`GET`, `PATCH` y `PUT`) → 401 `UNAUTHENTICATED`
    en `authorization` con `WWW-Authenticate: Bearer`.
- [x] 1.2 `docs` de `bruno/users/folder.yml`: por qué usa un usuario propio (cambia correo y clave)
  y por qué el token va a `tokenPerfil`.
- [x] 1.3 Verificar que los requests fallan hoy por la razón correcta. Desde `bruno/`, correr
  `bru run auth users -r --env local` con la app de `dev`. Lo esperado:
  - las rutas de `/users/me` dan 404 con Bearer;
  - el alta con celular da 201 pero sin `phone`;
  - el alta con el celular inválido da 201 en vez de 400;
  - los 401 ya pasan, porque la cadena JWT cubre toda ruta.

## 2. Celular en el alta

Skills: `java-architect`, `java-exceptions`.

- [x] 2.1 `Formatos.CELULAR` (vacío, o `+` opcional y 7 a 15 dígitos, con espacios en el borde) y
  `phone` opcional en `RegisterUserRequest`. Verifica `RegisterUserRequestTest`: el válido, los dos
  mal formados de la spec y el vacío.
- [x] 2.2 `phone` en `RegistrationCommand` y `User`. `RegisterUserUseCase` lo recorta y guarda `null`
  si viene en blanco. `UserR2dbcAdapter` lo inserta con `bindNull` si falta. Verifica
  `RegisterUserUseCaseTest` (con y sin celular) y `UserMother`.
- [x] 2.3 `phone` en `RegisterUserResponse`, campo por campo como el resto. Verifica
  `AuthControllerTest`: alta con celular y alta sin celular, con `phone` `null`.

## 3. Leer el perfil

Skills: `java-architect`, `java-exceptions`, `java-security`.

- [x] 3.1 Record de dominio `UserProfile` (sin hash), puerto `GetUserProfilePort` y
  `GetUserProfileUseCase`. `UserRepositoryPort.findActiveProfile(UUID)` devuelve vacío si el usuario
  no existe, está inactivo o está borrado, y el caso de uso lo traduce a 401 `UNAUTHENTICATED` en
  `authorization`. Verifica `GetUserProfileUseCaseTest`.
- [x] 3.2 Consulta en `UserR2dbcAdapter` con `is_active` y `deleted_at IS NULL` en el SQL. La
  verifica Bruno (`ver-perfil`), porque los adapters R2DBC no se prueban en la suite.
- [x] 3.3 `UserController` con `@RequestMapping("/users/me")` y el `GET`, más `UserProfileResponse`
  construido campo por campo. Verifica:
  - `UserControllerTest`: forma de la respuesta y ausencia de hash;
  - `UsersIT` (`RANDOM_PORT`): 401 sin credenciales y con Basic en las tres rutas, con
    `WWW-Authenticate: Bearer`.

## 4. Modificar el perfil

Skills: `java-architect`, `java-exceptions`.

- [x] 4.1 `UpdateUserProfileRequest`:
  - `firstName`, `email` y `timezone` con `NO_EN_BLANCO` y el formato del alta;
  - `lastName` y `phone` con su formato, que admite el vacío;
  - `currentPassword` sin reglas de formato;
  - sin componente `baseCurrencyCode`, para que se descarte como cualquier propiedad desconocida;
  - `sinCambios()` sobre los cinco campos modificables.

  Verifica `UpdateUserProfileRequestTest`, y en `UserControllerTest` que `{"baseCurrencyCode":"USD"}`
  da 400 en `body` y no un error de deserialización.
- [x] 4.2 `UpdateUserProfilePort` y `UpdateUserProfileUseCase`:
  - normaliza (`trim`; correo en minúsculas; `lastName` y `phone` en blanco pasan a `null`);
  - si el correo cambia, exige `currentPassword` (400 `VALIDATION_ERROR`), la verifica contra el hash
    (400 `INVALID_CREDENTIALS`) y comprueba que el correo esté libre fuera del propio usuario (409);
  - no verifica la contraseña cuando el correo no cambia.

  Verifica `UpdateUserProfileUseCaseTest`: un caso por escenario de la spec, más el usuario inactivo.
- [x] 4.3 `UserRepositoryPort`: `findActivePasswordHash(UUID)`, `existsByEmailForOtherUser(String, UUID)`
  y `updateProfile(...)`, que devuelve el perfil actualizado (`updated_at` lo pone el trigger `trg_users_updated_at`). El adapter
  traduce la violación de `ux_users_email` a 409 `DUPLICATE_RESOURCE` en `email`. Lo verifica Bruno.
- [x] 4.4 `PATCH` en `UserController`, con el 400 de parche vacío en el controlador, como en cuentas.
  Verifica `UserControllerTest`.

## 5. Cambiar la contraseña

Skills: `java-architect`, `java-exceptions`.

- [x] 5.1 `ChangePasswordRequest`: `currentPassword` con `@NotBlank`, y `newPassword` con
  `@NotBlank`, `AL_MENOS_8` y `@MaxBytesUtf8(72)`. Verifica `ChangePasswordRequestTest`.
- [x] 5.2 `ChangePasswordPort` y `ChangePasswordUseCase`: verifica la actual (400
  `INVALID_CREDENTIALS` en `currentPassword`), hashea la nueva y la guarda con
  `UserRepositoryPort.updatePassword(UUID, String)`. Verifica `ChangePasswordUseCaseTest`, incluido
  que la nueva se pasa por el hasher y nunca se guarda en claro.
- [x] 5.3 `PUT /users/me/password` con 204. Verifica `UserControllerTest`.

## 6. Documentación

- [x] 6.1 `docs/api/contrato-api.md`:
  - `phone` en el cuerpo y la respuesta de `register`;
  - secciones de `GET` y `PATCH /api/users/me` y `PUT /api/users/me/password`, con los errores y por
    qué la clave equivocada da 400 y no 401;
  - el índice;
  - en «Lo que el API todavía no tiene»: cambiar la moneda base (FA-91) y la recuperación de
    contraseña (FA-90).

  Verifica que cada ejemplo coincida con las respuestas de Bruno.
- [x] 6.2 Replicar en `bruno-personal/` el `GET`, el `PATCH` y el `PUT` del perfil (ruta, cuerpo y
  Bearer) y el `phone` del alta, sin ejecutarlos.

## 7. Verificación

- [x] 7.1 `.\gradlew.bat build` en verde: conteo de tests y cobertura de línea reales.
- [x] 7.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde:
  conteo de requests y assertions reales.
