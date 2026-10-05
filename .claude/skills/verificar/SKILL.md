---
name: verificar
description: >
  Use when finishing any Java code change in financeapp-bk, when the user asks to verify the
  build ("/verificar", "verifica el build", "corre los tests"), or before declaring a task
  complete: runs the Gradle build (full suite plus the coverage threshold) and the Bruno
  collection, and reports real counts. Skip when the user explicitly asks to skip verification.
metadata:
  origen: skills-catalogo 2026-10-04
  ajustes: Gradle del proyecto, sin checkstyle (el proyecto no tiene linter), con Bruno
---

# Verificar — build verde antes de terminar

Corre las dos capas de verificación del proyecto y reporta el resultado con conteos. Es la
evidencia que respalda dar una tarea por terminada. Las dos van juntas: `AGENTS.md` explica por qué
una sola no alcanza.

## 1. Gradle

```powershell
.\gradlew.bat build
```

`build`, no `test`: el umbral de cobertura lo exige `jacocoTestCoverageVerification`, colgado de
`check`. No usar `-x test` ni banderas que omitan verificación. Este proyecto **no tiene checkstyle
ni linter**: no se reporta un paso de lint que no existe.

## 2. Bruno

Desde la raíz del repo, con el script que valida el entorno, levanta la app, corre la colección y
la apaga:

```powershell
pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos
```

No levantes la app a mano ni corras `bru run` suelto para verificar: si en el puerto hay una app
apuntando a Neon, la colección y la recarga de `test-data.sql` escriben en producción (FA-61). El
script se detiene con código 2 antes de tocar nada si:

- **El puerto de `bruno/environments/local.yml` está ocupado.** Reporta el PID y el proceso al
  usuario y espera a que lo apague él: nunca matar un proceso que no levantaste.
- **La base resuelta no es `localhost/financeapp`.** Mira `application-local.yaml` junto con las
  variables `DB_*` y `SPRING_R2DBC_URL` de la terminal. Dile al usuario cuál variable o línea
  apunta a otra parte; no las limpies ni edites el archivo por tu cuenta.

`-RecargarDatos` carga el escenario de `test-data.sql` antes de correr; omítelo solo si el
escenario ya está recién cargado. `-Objetivo transactions` corre una sola carpeta, y cada argumento
`nombre=valor` llega a `bru` como `--env-var`.

Código 3: la app no arrancó o no se pudo apagar, y el script muestra el final del log. Cualquier
otro código distinto de 0 es el de `bru`. Si `bru` no está en el PATH (FA-39) o la app no levanta,
es un bloqueo que se reporta, no un paso que se omite.

## 3. Reportar

Siempre en este formato, con números tomados de la salida:

```
Tests: X pasados, Y fallidos, Z omitidos (total N)
Cobertura de línea: P % (umbral 85 %)
Bruno: R/R requests, T/T tests, A/A aserciones
Build: SUCCESS | FAILURE
```

Si la salida de Gradle no trae el conteo, leer `build/test-results/test/*.xml` (atributos `tests`,
`failures`, `errors`, `skipped`). La cobertura está en `build/reports/jacoco/test/`.

## 4. Si falla

- Mostrar los tests fallidos con su aserción o stacktrace resumido, y los requests de Bruno que
  fallaron con la aserción.
- Arreglar y volver a correr hasta verde, salvo que el usuario indique lo contrario. Mientras siga
  en rojo, la tarea no está terminada: decirlo con el output, no darla por buena. Si la causa no es
  evidente, `java-debugging`.

## Escaneo de dependencias (opcional — OWASP A06)

Correr SOLO cuando el usuario lo pida o al agregar dependencias nuevas, no en cada verificación (la
primera corrida descarga la base NVD y tarda varios minutos). Requiere el plugin
`org.owasp.dependencycheck` en `build.gradle`:

```
.\gradlew.bat dependencyCheckAnalyze
```

(umbral en el build script: `dependencyCheck { failBuildOnCVSS = 7 }`)

- Para corridas frecuentes, configurar una API key de NVD (`nvd.api.key`); sin ella el rate limit
  hace la actualización muy lenta.
- Reportar: dependencias con CVEs ≥ 7, versión afectada y versión corregida sugerida.
- Los falsos positivos se suprimen con un `suppression.xml` referenciado en la config del plugin.
