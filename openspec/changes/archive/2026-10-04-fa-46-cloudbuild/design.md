# Design

## Context

El servicio `financeapp-bk-git` (europe-west1) ya tiene secretos, variables y escalado de 0 a 1
(FA-48). `deployment/Dockerfile` construye una imagen de unos 226 MB comprimida y su etapa de
compilación corre la suite completa (FA-45). En Artifact Registry existe el repositorio
`cloud-run-source-deploy` en `europe-west1`, vacío y sin política de limpieza; el de `us-east1`, con
609 MB de intentos viejos, lo borró el usuario el 04-10-2026. Cloud Run y Artifact Registry van en
plan gratuito: 0,5 GB de almacenamiento.

## Goals / Non-Goals

**Goals:**
- Un merge a `main` construye, prueba, publica y despliega sin pasos manuales.
- Cada revisión se puede asociar a su commit.
- El almacenamiento se queda dentro de la cuota gratuita.

**Non-Goals:**
- Pruebas de integración en el pipeline, entornos de staging o despliegues progresivos.

## Decisions

### 1. `deployment/cloudbuild.yaml` versionado, no un disparador configurado solo en la consola

El disparador de "despliegue continuo" de Cloud Run guarda los pasos en GCP, donde nadie los revisa
en un PR. Fue lo que se creyó tener y no existía. Con el archivo en el repo, los pasos se leen, se
versionan y un cambio pasa por PR. Decisión del usuario al reabrir FA-46.

Va en `deployment/`, junto al `Dockerfile`, y no en la raíz: el usuario lo movió al crear el
disparador (04-10-2026), que apunta a esa ruta. No cambia los pasos: Cloud Build los ejecuta desde
la raíz del repo, así que el contexto del build sigue siendo `.`.

### 2. La suite corre dentro del `docker build`, sin un paso de Gradle aparte

La etapa de compilación del `Dockerfile` corre `./gradlew build`: si un test falla o la cobertura
baja del 85 %, el paso 1 falla, y Cloud Build no ejecuta los pasos siguientes, así que nada se
publica ni se despliega. FA-45 ya comprobó que un test rojo no produce imagen.

Descartado: un paso previo `gradle build` con la imagen de Gradle. Correría la suite dos veces por
build, con el doble de minutos de Cloud Build, y podría usar otro JDK distinto del que empaqueta la
imagen.

### 3. El despliegue solo cambia la imagen

`gcloud run deploy financeapp-bk-git --image <repo>/<servicio>:<SHA>` conserva las variables, los
secretos, la cuenta de servicio y el escalado que ya tiene el servicio. La configuración sigue
viviendo en el servicio, documentada en `docs/despliegue.md`.

Descartado: declarar el servicio completo (`service.yaml` o todos los flags en el paso). Duplicaría
la configuración de FA-48 en dos lugares que se separarían con el tiempo, y un error en el archivo
podría quitarle un secreto al servicio en el siguiente despliegue.

Se despliega por la etiqueta del SHA y no por `latest`: así la revisión apunta a una imagen
concreta. La revisión lleva además la etiqueta `commit-sha=<SHA>`, que se ve en la consola y en
`gcloud run revisions list`.

### 4. Conservar 1 versión en Artifact Registry

Decisión del usuario del 04-10-2026: lo mínimo. La política conserva la versión más reciente y borra
el resto.

Consecuencia que queda escrita en `docs/despliegue.md`: una revisión vieja de Cloud Run apunta al
digest de su imagen, y con escala a 0 toda petición tras un rato sin uso arranca una instancia
nueva, que necesita esa imagen. Así que **revertir no es pasar el tráfico a la revisión anterior**,
sino revertir el commit en `main` y dejar que el pipeline reconstruya. La limpieza corre una vez al
día: durante unas horas tras un despliegue puede quedar la versión anterior, y la reversión por
tráfico puede funcionar, pero no se cuenta con ella.

Descartado: conservar 2 versiones. Permitía revertir sin reconstruir, y la capa del JDK se comparte,
así que costaba poco más; el usuario prefirió el mínimo.

### 5. La cuenta del disparador es `financeapp@`

Sigue la decisión de FA-48: una sola cuenta. Para el pipeline necesita, además de `run.admin` e
`iam.serviceAccountUser`, `roles/artifactregistry.writer` (publicar la imagen) y
`roles/logging.logWriter` (logs del build). Con una cuenta propia en el disparador, Cloud Build
exige `options.logging: CLOUD_LOGGING_ONLY`. Los roles los otorga el usuario.

### 6. La primera corrida es la del disparador, después del merge de `dev` a `main`

Decisión del usuario del 04-10-2026: respetar que `main` es producción. El change se archiva y se
mergea a `dev` con todo lo que se puede hacer antes (archivo, limpieza, documentación, roles y
disparador); el primer despliegue real ocurre cuando el usuario mergee `dev` a `main` (va 32
commits detrás).

La verificación de esa corrida queda **fuera de `tasks.md`**, porque llega después de archivar:
FA-46 se mantiene `En curso` hasta comprobar que la revisión nueva usa la imagen con el SHA y que
`/api/status` responde la app y no el placeholder. Con esa evidencia, un commit corto a `dev`
quita de `AGENTS.md` la línea que dice que el servicio corre el placeholder.

Descartado: una corrida manual con `gcloud builds submit` desde la rama, antes de archivar. Daba la
evidencia dentro del ciclo, pero ponía en producción código de `dev` que no está en `main`.

## Risks / Trade-offs

- [Minutos de Cloud Build] → cada build corre la suite completa dentro de Docker. El plan gratuito
  incluye minutos de build al mes; al ritmo de merges a `main` de este proyecto no debería llegar al
  límite.
- [`latest` en el repositorio] → solo informativo; nada despliega por `latest`.
- [Un despliegue roto] → se revierte pasando el tráfico a la revisión anterior, con su imagen
  todavía disponible (decisión 4).
