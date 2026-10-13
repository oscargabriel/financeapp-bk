# Tasks

## 1. Un solo proyecto

Skills: `java-architect`.

- [x] 1.1 Mover `compartido/src`, `core/src`, `ia/src` y `app/src` a `src/` con `git mv`, sin
  cambiar los paquetes de los módulos. Llevar los test fixtures a `src/test/java`, y el yaml de test
  a `src/test/resources`. Mover en local los dos yaml sin versionar a `src/main/resources/`.
  Verifica: `compartido/`, `core/`, `ia/` y `app/` quedan sin `src/`.
- [x] 1.2 Renombrar los paquetes de lo que era `app` según la decisión 2 del design: conexión a
  `compartido.infrastructure.config`, puentes a `puente`, cada `*IT` al paquete web de su módulo y
  `CloudRunConfigTest` junto a la conexión. Verifica: ningún archivo declara
  `com.oscargabriel.financeapp.app`.
- [x] 1.3 Un solo `build.gradle` (el plugin de Boot, las dependencias de los cinco, cobertura con
  `**/*R2dbcAdapter` y el `main` excluidos) y `settings.gradle` sin `include`. Borrar los
  `build.gradle` de los módulos, las dos `ConfiguracionDeSlices`, la copia del yaml de test y el
  `-parameters` manual. Verifica: `.\gradlew.bat build` en verde, con 1213 tests y la cobertura en
  98,66 % de línea, como en `dev`.
- [x] 1.4 `deployment/Dockerfile` y `.dockerignore` con las rutas de un solo proyecto. Verifica:
  `docker build -f deployment/Dockerfile -t financeapp-bk .` produce la imagen.
- [x] 1.5 `verificar-bruno.ps1` con `bootRun` y el yaml local en `src/main/resources`; la skill
  `verificar` y `docs/verificaciones-manuales.md` con las rutas de un solo proyecto;
  `bruno-personal/opencollection.yml` con `bootRun`. Verifica: ningún archivo versionado fuera de
  `openspec/changes/archive/` nombra `app/src`, `:app:` ni `testCodeCoverageReport`.
- [x] 1.6 `AGENTS.md`:
  - *Comandos* sin prefijo de módulo;
  - *Arquitectura* con los módulos como paquetes, la convención de que un módulo no importa otro y
    `ia` no importa `core` (sin verificación automática), el paquete `puente/` y cómo se agrega un
    módulo;
  - *Configuración*, *Tests*, *Cobertura* y *Despliegue* con las rutas de un solo proyecto.

  Verifica: ninguna sección menciona subproyectos, test fixtures ni `processTestResources`.
- [x] 1.7 Commit del movimiento. Verifica: `git show --stat -M` muestra los archivos como renombres.

## 2. Verificación

- [x] 2.1 `.\gradlew.bat clean build` en verde, con el conteo real de tests y el porcentaje de línea.
- [x] 2.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con el
  conteo real de requests y aserciones, y `check-saldos.sql` sin cuentas descuadradas.
