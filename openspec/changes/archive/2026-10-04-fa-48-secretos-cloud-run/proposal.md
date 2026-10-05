# Proposal

Origen: FA-48, etapa de despliegue. La tarea se escribió el 22-09-2026. Desde entonces el usuario
creó el servicio `financeapp-bk-git` (Cloud Run, `europe-west1`) con sus secretos, así que el
trabajo pasa de "crear los secretos" a comprobar lo que quedó montado y escribirlo.

Hallazgo del 04-10-2026: las cinco revisiones del servicio corren `gcr.io/cloudrun/placeholder`, no
la imagen de la app; `/api/status` devuelve la página "Placeholder | Cloud Run". Los secretos están
configurados pero ninguna app los ha leído todavía en Cloud Run. Hacer que el servicio corra la app
no es parte de FA-48 (ver *Fuera de alcance*).

## Why

`application.yaml` lee sin default `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `BASIC_USERNAME` y
`BASIC_PASSWORD` para que un despliegue incompleto no arranque. Nada en el repo dice hoy cómo se
llaman esos secretos en Secret Manager, a qué variable va cada uno ni cómo se cambia uno sin tumbar
el servicio. Quien tenga que rotar la credencial Basic dentro de tres meses no tiene dónde mirar.

Lo que se encontró en el servicio el 04-10-2026, con `gcloud` de solo lectura:

- Ocho secretos montados como variables con `secretKeyRef` a `<nombre>:latest`, una versión
  habilitada cada uno: `db-host`, `db-port`, `db-name`, `db-username`, `db-password`, `jwt-secret`,
  `basic-username` y `basic-password`.
- `SPRING_PROFILES_ACTIVE` y `CORS_ALLOWED_ORIGINS` en texto plano; el segundo es `*`, decisión
  provisional del 26-09-2026 ya escrita en `application.yaml`.
- Una sola cuenta de servicio, `financeapp@…`, con `secretmanager.secretAccessor`, `run.admin` e
  `iam.serviceAccountUser` a nivel de proyecto.
- Escalado de 0 a 20 instancias, cuando `application.yaml` y `AGENTS.md` dimensionan el pool para 3.

## What Changes

- `docs/despliegue.md` nuevo, con la sección de secretos y configuración del servicio: qué secreto
  es cada cual, a qué variable y propiedad va, cómo se rota cada uno, la cuenta de servicio y el
  escalado. FA-49 completa el resto del archivo (Neon, `gcloud`, despliegue y reversión).
- Verificación de los criterios que dependen de los valores (longitud de `JWT_SECRET`, que prod y
  local no compartan credenciales, que ninguno esté en el historial de git). La corre el usuario:
  los valores no pasan por la sesión.
- Prueba con la imagen de `deployment/Dockerfile` en local de que sin `JWT_SECRET` la app no
  arranca.
- El servicio queda con máximo 1 instancia (servicio y revisión), y `AGENTS.md` y el comentario
  del pool en `application.yaml` pasan de 3 × 10 a 1 × 10 conexiones.

## Capabilities

### New Capabilities

Ninguna (`skip_specs: true`): es documentación y verificación de infraestructura, no comportamiento
de la app.

### Modified Capabilities

Ninguna.

## Fuera de alcance

- Separar la cuenta de servicio de ejecución de la de despliegue. El usuario decidió mantener una
  sola con todos los permisos (04-10-2026); el criterio "lee esos secretos y nada más" queda
  relajado por esa decisión y se dice en la tarea.
- CORS con orígenes explícitos: tarea nueva en Backlog (FA-65).
- Que el servicio construya y despliegue la app: el disparador de `main` que daba por hecho
  `AGENTS.md` no ha producido ninguna revisión con la imagen. Decisión del usuario: se reabre FA-46
  (`cloudbuild.yaml` versionado). Este change solo corrige `AGENTS.md` para que no afirme que
  producción ya corre en Cloud Run.
- Cargar el esquema en Neon y el resto de `docs/despliegue.md`: FA-49.
- Entorno `prod` de Bruno: FA-50.

## Impact

- Nuevo `docs/despliegue.md`.
- Servicio `financeapp-bk-git`: máximo de 1 instancia (aplicado por el usuario).
- `AGENTS.md` y un comentario de `application.yaml`. Sin cambios de código, `bruno/` ni la base.
