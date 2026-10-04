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

Desde `bruno/`, con la app levantada (`SPRING_PROFILES_ACTIVE=local`):

```powershell
bru run . -r --env local
```

Si `bru` no está en el PATH o la app no levanta, es un bloqueo que se reporta, no un paso que se
omite.

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
