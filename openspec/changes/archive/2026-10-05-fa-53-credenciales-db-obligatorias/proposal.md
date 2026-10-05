# Proposal

Origen: FA-53, surgida en FA-47 (26-09-2026) al probar el perfil `prod` quitando cada variable
obligatoria.

## Why

`spring.r2dbc.username` y `spring.r2dbc.password` valen `${DB_USERNAME}` y `${DB_PASSWORD}` sin
default, como el resto de secretos. Pero los demás se leen con `@Value` y su ausencia aborta el
arranque en el acto con `Could not resolve placeholder '<VAR>'`. Estos dos los enlaza el binder de
Spring Boot en `R2dbcProperties`, que deja pasar el placeholder sin resolver como texto literal. La
app intenta autenticarse en Neon con el usuario `${DB_USERNAME}` y, a los ~20 s,
`DatabaseStartupCheck` aborta con "postgres no respondió tras 5 intentos". Ese mensaje apunta a la
red y no a la variable que falta. Con `STARTUP_DB_CHECK_ENABLED=false`, la app arranca y falla en la
primera consulta.

## What Changes

- La app se niega a arrancar si `spring.r2dbc.username` o `spring.r2dbc.password` no se pueden
  resolver, y el error nombra la variable que falta. No depende de `startup.db-check`.
- El error no incluye el valor de ninguna credencial.
- Un test lo cubre. El comentario de `CloudRunConfigTest` que documenta la limitación se corrige.
  La tarea lo llama `ProdProfileConfigTest`, el nombre que tenía antes de FA-48.
- `AGENTS.md` y `docs/despliegue.md` dejan de decir que estas dos variables fallan en
  `DatabaseStartupCheck`.

## Capabilities

### New Capabilities

- `arranque`: cuándo la aplicación se niega a arrancar por su configuración y qué dice al hacerlo.
  Hoy no hay spec de esta área. Nace con el requisito de las credenciales de la base, y lo demás
  (los otros secretos, `DatabaseStartupCheck`) entra cuando un change lo toque.

### Modified Capabilities

Ninguna.

## Fuera de alcance

- Una variable definida pero vacía (`DB_USERNAME=`). Se resuelve a cadena vacía y sigue fallando en
  la autenticación, como cualquier credencial equivocada. Los criterios de la tarea hablan de la
  variable ausente. Si se quiere cubrir ese caso, va como tarea nueva.
- Un mensaje propio en lugar del de Spring (`Could not resolve placeholder 'DB_USERNAME'`). Es el
  mismo texto que ya da hoy cualquier otro secreto ausente.
- Cambios en `DatabaseStartupCheck` o en `PostgresHealthCheckAdapter`.

## Impact

- Una clase nueva en `infrastructure/config/startup/` y su test.
- `CloudRunConfigTest` (solo el comentario), `AGENTS.md` y `docs/despliegue.md`.
- Sin cambios de API, de `bruno/` ni de la base.
