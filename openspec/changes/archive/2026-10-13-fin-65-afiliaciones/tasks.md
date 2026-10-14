# Tasks

## 1. Bruno en RED

Skills: `bruno-cli`.

- [x] 1.1 Carpeta `bruno/affiliations/` (`seq` 16, auth Bearer heredada con `tokenAfiliadoA`). Su
  `folder.yml` documenta el escenario: tres usuarios registrados en la corrida
  (`afiliados-a-`, `afiliados-b-` y `afiliados-c-<timestamp>@bruno.local`) y un correo sin cuenta
  (`afiliados-nadie-<timestamp>@bruno.local`). La carpeta termina sin relaciones abiertas, así que
  se puede repetir sin recargar datos. Requests, en orden:
  - alta y login de A, B y C (guardan `tokenAfiliadoA`, `tokenAfiliadoB`, `tokenAfiliadoC`);
  - `listar-vacio.yml`: 200 `[]`;
  - `enviar.yml`: A → B con el correo en mayúsculas y espacios; 201, `REQUESTED`, `SENT`, correo
    normalizado, nombre y apellido `null`; guarda `idAfiliacion`;
  - `enviar-sin-cuenta.yml`: A → nadie; 201 con la misma forma; guarda `idSinCuenta`;
  - `listar-enviadas.yml`: las dos de A, sin nombre en ninguna;
  - `listar-recibida.yml` (B): una, `RECEIVED`, con el correo, nombre y apellido de A;
  - `enviar-repetida.yml`, `enviar-contraria.yml` (B → A), `enviar-sin-cuenta-repetida.yml`: 409
    `DUPLICATE_RESOURCE` en `email`;
  - `enviar-a-si-mismo.yml`: 400 `VALIDATION_ERROR` en `email`;
  - `enviar-correo-invalido.yml` y `enviar-sin-correo.yml`: 400 `VALIDATION_ERROR` en `email`;
  - `aceptar-propia.yml` (A), `cancelar-recibida.yml` (B), `desvincular-solicitud.yml` (A): 409
    `INVALID_STATE` en `status`;
  - `aceptar-ajena.yml`, `rechazar-ajena.yml`, `cancelar-ajena.yml` y `desvincular-ajena.yml` (C): 404
    `NOT_FOUND` en `id`;
  - `aceptar-inexistente.yml`: 404 en `id`; `aceptar-id-mal-formado.yml`: 400 `VALIDATION_ERROR` en
    `id`;
  - `rechazar.yml` (B): 204; `listar-tras-rechazar.yml` (A): solo la de nadie;
  - `enviar-tras-rechazar.yml` (A → B): 201 con un id distinto; guarda `idAfiliacion`;
  - `aceptar.yml` (B): 200, `ACCEPTED`, `RECEIVED`, datos de A; `aceptar-de-nuevo.yml`: 409;
  - `listar-aceptada.yml` (A): `ACCEPTED`, `SENT`, con nombre y apellido de B;
  - `enviar-ya-afiliados.yml` (B → A): 409 `DUPLICATE_RESOURCE`;
  - `desvincular.yml` (B, el destinatario): 204; `listar-tras-desvincular.yml` (B): `[]`;
  - `enviar-tras-desvincular.yml` (B → A): 201; `listar-recibida-tras-desvincular.yml` (A): la de B
    `RECEIVED`;
  - `cancelar.yml` (B, su enviada): 204; `listar-tras-cancelar.yml` (A): solo la de nadie;
  - `cancelar-sin-cuenta.yml` (A): 204; `listar-final.yml` (A): `[]`;
  - `listar-sin-credencial.yml`, `enviar-sin-credencial.yml`, `aceptar-sin-credencial.yml`: 401
    `UNAUTHENTICATED` en `authorization`, `WWW-Authenticate: Bearer`;
  - `listar-con-basic.yml`, `enviar-con-basic.yml`, `desvincular-con-basic.yml`: el mismo 401.
- [x] 1.2 Comentario de `docs/database/test-data.sql` que lista los prefijos de `@bruno.local`:
  agregar `affiliations/` con `afiliados-*-<timestamp>@bruno.local`.
- [x] 1.3 Verificar que fallan por la razón correcta: `verificar-bruno.ps1 -Objetivo affiliations`
  con la app de `dev`. Se espera el 404 `NOT_FOUND` de una ruta inexistente con Bearer válido, y el
  401 en los de credencial. Anotar aquí el conteo.

  50 requests, 12 pasan y 38 fallan; 34 de 107 asserts. Pasan las altas y logins (6) y los seis de
  credencial, que ya caen en la cadena Bearer. Los demás responden 404 de ruta inexistente. Se
  sumaron cuatro requests que el plan no listaba: `cancelar-aceptada.yml` (escenario de la spec), y
  `enviar-a-desactivado.yml` y `cancelar-desactivado.yml` contra `inactivo@financeapp.local`
  (escenario «Usuario desactivado»).

## 2. Dominio

Skills: `java-architect`.

- [x] 2.1 `AffiliationStatus` (`REQUESTED`, `ACCEPTED`, `REJECTED`, `CANCELLED`, `UNLINKED`),
  `AffiliationDirection` (`SENT`, `RECEIVED`) y el record `Affiliation` (id, requesterId,
  addresseeEmail, addresseeId nullable, status, createdAt, updatedAt).
- [x] 2.2 `AffiliationAction` con la tabla de la decisión 2 de design.md, y la regla que dice si una
  acción aplica a una relación para un usuario. Verificar con `AffiliationActionTest`: las cuatro
  acciones en su estado y papel válidos, y cada combinación inválida (papel equivocado, estado
  equivocado, sin destinatario).
- [x] 2.3 Record `AffiliationView` (id, status, direction, email, firstName, lastName, createdAt,
  updatedAt) y los puertos: de entrada `SendAffiliationRequestPort`, `ListAffiliationsPort` y
  `ChangeAffiliationPort`; de salida `AffiliationRepositoryPort` (`save`, `existsOpenVisible`,
  `findOpenByUser`, `findForParticipant`, `findViewForParticipant`, `changeStatus`).
  `AffiliationMother` en `core/support/`.

## 3. Casos de uso

Skills: `java-architect`, `java-exceptions`.

- [x] 3.1 `SendAffiliationRequestUseCase`: normaliza el correo, resuelve el destinatario con
  `UserRepositoryPort.findActiveByEmail`, 400 si es el propio usuario, 409 si
  `existsOpenVisible`, e inserta con `UuidV7` y la hora del `Clock`. Devuelve la vista `SENT` sin
  nombre. Verificar con `SendAffiliationRequestUseCaseTest`: con cuenta, sin cuenta (las dos
  respuestas idénticas salvo el id), correo con mayúsculas y espacios, a sí mismo, duplicada.
- [x] 3.2 `ListAffiliationsUseCase`. Verificar con `ListAffiliationsUseCaseTest`.
- [x] 3.3 `ChangeAffiliationUseCase`: 404 si `findForParticipant` está vacío, 409 `INVALID_STATE` en
  `status` si la acción no aplica o si `changeStatus` devuelve `false`, y la vista tras el cambio.
  Verificar con `ChangeAffiliationUseCaseTest`: las cuatro acciones, ajena, acción inválida y la
  carrera.

## 4. Web

Skills: `java-architect`, `java-exceptions`, `java-security`.

- [x] 4.1 `AffiliationsIT`: las seis rutas sin credencial y con el Basic dan 401 con
  `WWW-Authenticate: Bearer`. Verlo fallar antes del controlador (hoy dan 401 por la cadena, así que
  falla solo si una ruta nueva quedara fuera de ella: anotar si pasa ya en RED).

  En RED fallaron los 12, pero no por la cadena: el contexto no arrancaba porque los casos de uso
  pedian un `AffiliationRepositoryPort` que aun no existia. Pasa desde el grupo 5.
- [x] 4.2 `SendAffiliationRequest` (record con `@NotBlank`, `@Size(max = 255)` y
  `@Pattern(Formatos.EMAIL)`), `AffiliationResponse` con `person` anidado, y `AffiliationController`
  con las seis rutas y el `parseId` a mano. Verificar con `AffiliationControllerTest`: 201, 200,
  204, 400 por correo y por id, y que el usuario sale del token.

## 5. Persistencia

Skills: `java-architect`, `java-exceptions`.

- [x] 5.1 `AffiliationR2dbcAdapter`: las consultas de la decisión 5 de design.md, el listado con la
  tabla de la decisión 4 (unión con `users` para el nombre y el correo actual), el `UPDATE`
  condicionado por estado, y la violación de los dos índices únicos traducida a 409
  `DUPLICATE_RESOURCE` en `email`. Lo verifica `bruno/affiliations/` (la suite no tiene base).

## 6. Documentación

- [x] 6.1 `docs/api/contrato-api.md`: las seis rutas en el índice, una sección *Afiliaciones* con
  cuerpo, respuesta, la tabla de `person`, los errores y la regla de no revelar correos.
- [x] 6.2 `docs/database/modelo-datos.md`, sección *Finanzas compartidas*: las transiciones de estado
  ya no son una regla pendiente, las hace cumplir `/api/affiliations` (FIN-65).
- [x] 6.3 `bruno-personal/`: carpeta `afiliados/` con listar, enviar, aceptar, rechazar, cancelar y
  desvincular, con la misma ruta, cuerpo y autenticación que `bruno/affiliations/`. Sin ejecutarla:
  apunta a Neon.

## 7. Verificación

- [x] 7.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea.

  BUILD SUCCESSFUL: 1320 tests, 0 fallos, 0 omitidos; cobertura de línea 98,55 % (2515 de 2552).
- [x] 7.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con el
  conteo de requests y asserts, y `check-saldos.sql` sin cuentas descuadradas.

  691 de 691 requests, 542 de 542 tests y 1341 de 1341 asserts. `check-saldos.sql`: las 61 cuentas
  cuadran.
- [x] 7.3 Una segunda corrida de `-Objetivo affiliations` sin recargar, para comprobar que la carpeta
  se repite.

  Tras la corrida completa, otra de `-Objetivo affiliations` sin recargar: 50 de 50 requests, 107 de
  107 asserts.
