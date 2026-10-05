# Tasks

## 1. Documentación

Skills: ninguna de la tabla del paso 5 (no toca código ni `bruno/`).

- [x] 1.1 `docs/despliegue.md` con la sección *Secretos y configuración del servicio*: tabla de los
  ocho secretos (nombre en Secret Manager, variable, propiedad de `application.yaml`, qué es), las
  dos variables en texto plano, y el procedimiento de rotación de cada tipo (`JWT_SECRET` invalida
  los tokens emitidos; la Basic exige actualizar el entorno `prod` de Bruno; la de la base se cambia
  primero en Neon). Verificar leyendo el archivo contra la salida de `gcloud run services describe`.
- [x] 1.2 Misma sección: cuenta de servicio y su precio (decisión 1), y el escalado de 0 a 1 con la
  cuenta de conexiones (decisión 4). Dejar marcado que FA-49 completa el resto del archivo.

## 1b. Escalado del servicio

- [x] 1b.1 `--max-instances=1`, aplicado por el usuario. Verificado con `gcloud run services
  describe`: servicio y revisión `financeapp-bk-git-00005-dj5` con máximo 1, mínimo 0 en la
  revisión, 100 % del tráfico.
- [x] 1b.2 `AGENTS.md` (*Configuración y arranque*) y el comentario del pool en `application.yaml`
  pasan de 3 × 10 a 1 × 10.
- [x] 1b.3 `AGENTS.md`, encabezado y *Despliegue*: el servicio existe pero corre el placeholder y un
  merge a `main` todavía no despliega; el pipeline es FA-46 (reabierta). Verificado con
  `gcloud run revisions list`: las cinco revisiones usan `gcr.io/cloudrun/placeholder`.

## 2. Comprobaciones con los valores

- [x] 2.1 El usuario corre el bloque de comprobación (longitudes y sí/no, sin valores): `JWT_SECRET`
  de 32 bytes o más, distinto del de local; contraseña Basic distinta de la de local; ninguno de los
  dos en `git log --all -p`. Anotar el resultado en *Notas de implementación* de FA-48.
  Resultado: `JWT bytes: 36`, `JWT igual a local: False`, `Basic pass igual a local: False`,
  `En historial git: False`.

## 3. Sin secreto no arranca

- [x] 3.1 Construir la imagen con `docker build -f deployment/Dockerfile -t financeapp-bk:fa48 .`
  (la suite corre dentro y pasó) y correrla sin `JWT_SECRET`, con el resto de variables ficticias y
  `STARTUP_DB_CHECK_ENABLED=false` para que la base no intervenga. Resultado: código de salida 1,
  `PlaceholderResolutionException: Could not resolve placeholder 'JWT_SECRET' in value
  "${JWT_SECRET}" <-- "${spring.security.jwt.secret}"` al crear `jwtSigningKey`.
- [x] 3.2 Control: las mismas variables más un `JWT_SECRET` ficticio de 64 caracteres. Resultado:
  `Started FinanceappBkApplication in 4.6 seconds`, contenedor vivo. La única diferencia entre las
  dos corridas es el secreto, así que el fallo de 3.1 lo produjo su ausencia.

## 4. Verificación

- [x] 4.1 `openspec validate fa-48-secretos-cloud-run --strict` en verde.
- [x] 4.2 `gradlew build` y `verificar-bruno.ps1 -RecargarDatos` en verde con los conteos reales,
  aunque el change no toque código: confirman que la rama sigue sana. `gradlew build`: 398 tests,
  0 fallos, 0 omitidos, cobertura de línea 97,75 % (870/890). Bruno: 127/127 requests, 133/133
  tests, 268/268 aserciones.
