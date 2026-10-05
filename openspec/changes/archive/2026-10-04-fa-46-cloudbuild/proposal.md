# Proposal

Origen: FA-46, reabierta el 04-10-2026. Se había descartado ese mismo día porque se creía que un
disparador de Cloud Run ya desplegaba `main`; al verificar FA-48 se encontró que las cinco
revisiones de `financeapp-bk-git` corren `gcr.io/cloudrun/placeholder` y que no hay disparador.

## Why

La app nunca ha corrido en Cloud Run. El servicio tiene los secretos y el escalado listos (FA-48),
pero nada construye la imagen ni la despliega, y no hay en el repo ningún registro de cómo debería
hacerse. Un merge a `main` hoy no tiene efecto, aunque `AGENTS.md` lo trate como el paso que lleva
a producción.

## What Changes

- `deployment/cloudbuild.yaml` con tres pasos en orden:
  1. Construir la imagen con `deployment/Dockerfile`. Su etapa de compilación ya corre
     `./gradlew build`, así que la suite y el umbral de cobertura van dentro de este paso.
  2. Publicarla en Artifact Registry (`europe-west1`, repositorio `cloud-run-source-deploy`) con
     dos etiquetas: el SHA corto del commit y `latest`.
  3. Desplegar en `financeapp-bk-git` la imagen por su SHA, con la etiqueta `commit-sha` en la
     revisión.

  Proyecto, región, repositorio y servicio van como sustituciones. Ningún secreto aparece en el
  archivo: el servicio ya los tiene montados y el despliegue solo cambia la imagen.
- Política de limpieza del repositorio: conservar solo la versión más reciente.
- `docs/despliegue.md`, sección *Pipeline*: qué hace `deployment/cloudbuild.yaml`, cómo crear el disparador en
  la consola, los permisos que necesita la cuenta, cómo desplegar a mano y cómo revertir a la
  revisión anterior.
- `AGENTS.md`, *Despliegue*: el pipeline existe y un merge a `main` lo dispara.
- La primera corrida es la del disparador cuando el usuario mergee `dev` a `main`; se verifica
  después de archivar este change y FA-46 sigue en curso hasta entonces.

## Capabilities

### New Capabilities

Ninguna (`skip_specs: true`): es infraestructura de despliegue, no comportamiento de la app.

### Modified Capabilities

Ninguna.

## Fuera de alcance

- Correr `bru run` en el pipeline: exige el escenario de `test-data.sql`, que no existe en Neon.
  La comprobación contra el servicio desplegado es FA-50.
- Separar la cuenta de despliegue de la de ejecución: decisión de FA-48, una sola cuenta.
- Neon, la carga del esquema y los `update/`: FA-49. FA-49 también pedía "cómo desplegar y cómo
  revertir" en `docs/despliegue.md`; con este change esa parte queda escrita y FA-49 solo la revisa.
- CORS con orígenes explícitos: FA-65.

## Impact

- Nuevos `deployment/cloudbuild.yaml` y la sección *Pipeline* de `docs/despliegue.md`; cambio en `AGENTS.md`.
- GCP: la política de limpieza del repositorio, el disparador (lo crea el usuario en la consola,
  porque la conexión con GitHub se autoriza desde su cuenta) y dos roles más para la cuenta del
  disparador (los otorga el usuario).
- Sin cambios en `src/`, `deployment/Dockerfile`, `bruno/` ni la base.
