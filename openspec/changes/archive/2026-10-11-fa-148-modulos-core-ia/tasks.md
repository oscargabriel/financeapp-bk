# Tasks

## 1. Esqueleto multi-proyecto

Skills: `java-architect`.

- [x] 1.1 Crear `settings.gradle` con `compartido`, `core`, `ia` y `app`; mover lo común del
  `build.gradle` raíz a `subprojects {}` (toolchain 25, Lombok, JUnit, BOM de Boot); crear el
  `build.gradle` de cada subproyecto con sus dependencias (`ia` → solo `compartido`; `core` →
  `compartido`; `app` → los tres, único con el plugin de Spring Boot). Verifica:
  `.\gradlew.bat projects` lista los cuatro.

## 2. Mover el código sin cambiar comportamiento

Skills: `java-architect`, `java-exceptions`, `java-security`, `java-logging`. El asistente se queda
en `core` en este grupo, todavía con los puertos del core. Sale a `ia` en el grupo 4.

- [x] 2.1 Mover a `compartido` lo de la decisión 2 del design con su paquete nuevo, y sus tests
  (`JwtConfigTest`, `LoggingFilterTest`). Publicar como test fixtures
  el `application.yaml` de test, `TokenMother`, `BasicMother`, `RelojFijo` y `Violaciones`.
  Verifica: `.\gradlew.bat :compartido:test` en verde.
- [x] 2.2 Mover el resto de `src/` a `core` bajo `com.oscargabriel.financeapp.core…`, con sus tests
  unitarios y de slice. Agregar la `@SpringBootConfiguration` de test para los `@WebFluxTest`.
  Verifica: `.\gradlew.bat :core:test` en verde, con los mismos tests que antes en esas clases.
- [x] 2.3 Llevar a `app` el `main`, `R2dbcSearchPathConfig`, `R2dbcCredentialsCheck`,
  `application.yaml`, los `*IT`, `FinanceappBkApplicationTests` y `CloudRunConfigTest`, este con la
  ruta nueva del yaml. Mover en local `application-local.yaml` y `application-prod.yaml` a
  `app/src/main/resources/`. Verifica: `.\gradlew.bat :app:test` en verde.
- [x] 2.4 Cobertura agregada en `app` con `jacoco-report-aggregation`, umbral 85 % de línea y las
  mismas exclusiones (`main`, `**/*R2dbcAdapter`), colgada de `check`. Verifica:
  `.\gradlew.bat build` en verde, y el reporte aparece en
  `app/build/reports/jacoco/testCodeCoverageReport/`. El total de tests debe ser el mismo de `dev`.
- [x] 2.5 Ajustar `deployment/Dockerfile` (copiar los cuatro subproyectos, `gradlew build`, extraer
  el jar de `app/build/libs`) y la lista de lo permitido de `.dockerignore`. Verifica:
  `docker build -f deployment/Dockerfile -t financeapp-bk .` termina y produce la imagen.
- [x] 2.6 `verificar-bruno.ps1` levanta la app con `:app:bootRun`. Verifica: el script con
  `-RecargarDatos` en verde con la colección sin tocar.
- [x] 2.7 Actualizar en `AGENTS.md`:
  - *Comandos*, *Arquitectura* (módulos, qué puede depender de qué, dónde van los puentes, cómo se
    agrega un módulo);
  - *Configuración y arranque* (rutas de los yaml);
  - *Tests*: test fixtures, la configuración de slice y el aviso de que el yaml de test reemplaza al
    de `main`, con su ruta nueva;
  - *Cobertura* (reporte agregado) y *Despliegue* (lista de `.dockerignore`).

  Actualizar también las rutas de `.claude/skills/verificar/SKILL.md` y de
  `docs/verificaciones-manuales.md`. Verifica: ningún archivo versionado fuera de
  `openspec/changes/archive/` nombra `src/main/resources` ni `build/reports/jacoco/test/` en la raíz.
- [x] 2.8 Commit del movimiento solo. Verifica: `git show --stat -M` muestra los archivos como
  renombres y no como borrados y altas.

## 3. Puerto de series en el core

Skills: `java-architect`, `java-testing`.

- [x] 3.1 `CatchUpSeriesPort` en `core/domain/port/in` y su caso de uso, que delega en
  `SeriesAlDia`. Verifica: `CatchUpSeriesUseCaseTest` (llama a `ponerAlDia` con el usuario y
  propaga el error).

## 4. Módulo `ia`

Skills: `java-architect`, `java-exceptions`, `java-testing`, `bruno-cli`.

- [x] 4.1 Antes del código, reescribir en `bruno/assistant/` las aserciones a la forma liviana, y
  verlas fallar contra la app del grupo 2:
  - `gasto-pendiente` y `transferencia`: nombres en vez de ids, sin `origin`;
  - `consultar-movimientos` y `reporte-del-mes`: totales iguales, mismos `id` en el mismo orden que
    los no programados de `/reports`;
  - `consultar-saldo` y `saldo-del-mes`: `period`, `allTime.net` y las cuentas;
  - un test por respuesta de que no vienen los campos excluidos.

  Verifica: `bru run assistant -r --env local` falla solo en esas aserciones.
- [x] 4.2 Dominio de `ia`:
  - los modelos `Assistant*`;
  - los modelos livianos de movimiento, reporte y saldo;
  - las cuentas y categorías para resolver nombres;
  - `AssistantPort`, `AssistantModelPort` y los puertos de salida de lectura, de registrar el
    pendiente y de series.

  Mover `AssistantUseCase` y `ResolutorDeNombres` a `ia/application` sobre esos puertos. Verifica:
  `AssistantUseCaseTest` y `ResolutorDeNombresTest` en `ia`, más casos nuevos: pone al día las
  series antes de leer el reporte y el saldo, y no las toca en una aclaración.
- [x] 4.3 Adaptadores R2DBC de lectura de `ia` (decisión 5 del design). Quedan fuera de la
  cobertura por su nombre y los verifica `bruno/assistant/` (4.1).
- [x] 4.4 Mover `AssistantController`, sus DTO livianos, Gemini, el stub y `resources/asistente/` a
  `ia`. Verifica: `AssistantControllerTest`, `GeminiAssistantAdapterTest` y
  `StubAssistantAdapterTest` en `ia`.
- [x] 4.5 Puentes en `app`: registrar el pendiente con `CreateTransactionsPort` y origen `TELEGRAM`,
  y poner al día las series con `CatchUpSeriesPort`. Verifica: un test por puente con los puertos
  del core simulados (comando armado, origen, propagación del 400).
- [x] 4.6 Quitar del core todo lo del asistente. Verifica: `.\gradlew.bat
  :ia:dependencies --configuration compileClasspath` no lista `:core`, y `.\gradlew.bat build` en
  verde.
- [x] 4.7 `bruno-personal/asistente/`: la ruta, el cuerpo y la autenticación no cambian. Verificar
  leyéndolo que no hay nada que replicar, sin ejecutarlo.
- [x] 4.8 En `AGENTS.md`, anotar que las lecturas de la IA copian reglas del core (estado
  confirmado, corte por zona, conversión de `MontoDeLaPersona`) y que la colección es la que avisa
  si se separan. Verifica: el párrafo existe en *Arquitectura*.

## 5. Verificación

- [x] 5.1 `.\gradlew.bat build` en verde, con el conteo real de tests y el porcentaje de línea
  agregado.
- [x] 5.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  el conteo real de requests y aserciones, y `check-saldos.sql` sin cuentas descuadradas.
- [x] 5.3 `docker build -f deployment/Dockerfile -t financeapp-bk .` en verde.
