# Tasks

Skills: ninguna de la tabla del paso 5 (no toca código Java ni `bruno/`).

## 1. Pipeline

- [x] 1.1 `deployment/cloudbuild.yaml`: `docker build -f deployment/Dockerfile` con las etiquetas
  `$SHORT_SHA` y `latest`, `docker push --all-tags` y `gcloud run deploy` por `$SHORT_SHA` con
  `--update-labels=commit-sha=$SHORT_SHA`. Sustituciones `_REGION`, `_REPOSITORY` y `_SERVICE` con
  default, y `options.logging: CLOUD_LOGGING_ONLY`. Verificar que el YAML parsea, que cada
  sustitución usada está declarada, y que ningún valor sensible aparece en el archivo.
- [x] 1.2 Política de limpieza del repositorio `cloud-run-source-deploy` (`europe-west1`): conservar
  la versión más reciente y borrar el resto. Verificar con `gcloud artifacts repositories describe`.

## 2. Documentación

- [x] 2.1 `docs/despliegue.md`, sección *Pipeline*: pasos de `deployment/cloudbuild.yaml`, cómo crear el
  disparador en la consola (repositorio de GitHub, rama `^main$`, archivo `deployment/cloudbuild.yaml`,
  cuenta `financeapp@`), los roles que necesita la cuenta, cómo desplegar a mano y cómo revertir
  (revertir el commit en `main`, decisión 4).
- [x] 2.2 `AGENTS.md`, *Despliegue*: el pipeline existe y un merge a `main` lo dispara; la línea del
  placeholder se mantiene, con la nota de que la primera corrida es la del merge de `dev` a `main`.

## 3. Preparación en GCP (usuario)

- [x] 3.1 El usuario otorga a `financeapp@` los roles `artifactregistry.writer` y
  `logging.logWriter` y crea el disparador según `docs/despliegue.md`. Verificar con
  `gcloud projects get-iam-policy` (roles) y `gcloud builds triggers list --region europe-west1`.
  Resultado: `financeapp@` con `artifactregistry.writer` y `logging.logWriter`; disparador
  `financeapp-bk` en `europe-west1`, rama `^main$`, archivo `deployment/cloudbuild.yaml`, cuenta
  `financeapp@`, activo.

## 4. Verificación

- [x] 4.1 `openspec validate fa-46-cloudbuild --strict` en verde.
- [x] 4.2 `gradlew build` y `verificar-bruno.ps1 -RecargarDatos` en verde con los conteos reales,
  aunque el change no toque `src/`. `gradlew build`: 398 tests, 0 fallos, 0 omitidos, cobertura
  de línea 97,75 % (870/890), con las tareas al día por no haber cambios en `src/`. Bruno:
  127/127 requests, 133/133 tests, 268/268 aserciones.

La primera corrida del disparador se verifica después de archivar (decisión 6) y se registra en
FA-46: revisión con la imagen del SHA y la etiqueta `commit-sha`, `GET /api/status` con 401 sin
credencial, y 200 con `UP` usando la Basic desde la app de Bruno.
