# Tasks

## 1. Flujo: réplica en bruno-personal y excepción de AGENTS.md

Sin skills de dominio.

- [x] 1.1 `openspec/config.yaml`, `rules.tasks`: todo change que agregue o cambie un request de
  `bruno/` lleva una tarea que lo replica en `bruno-personal/` (ruta, cuerpo, autenticación), sin
  ejecutarla. Verificar con `openspec validate fa-59-catalogos --strict` en verde y
  `openspec instructions tasks --change fa-59-catalogos --json` mostrando la regla.
- [x] 1.2 `.claude/skills/tareas-notion/SKILL.md`, paso 8: antes del commit, confirmar que la réplica
  de `tasks.md` está hecha leyendo los archivos de `bruno-personal/`, porque el PR no la muestra.
  Verificar leyendo el paso.
- [x] 1.3 `AGENTS.md`, sección *Arquitectura*: un endpoint que solo expone los valores de un enum del
  dominio no lleva puerto ni caso de uso. Verificar leyendo la sección.

## 2. Moneda inactiva en el escenario

- [x] 2.1 `docs/database/test-data.sql`: insertar `XTS` (`Moneda de prueba`, símbolo `XTS`) inactiva
  con `ON CONFLICT (code) DO UPDATE SET is_active = FALSE`. Verificar recargando el escenario dos
  veces con `verificar-bruno.ps1 -RecargarDatos` sin error; el filtro lo prueba 3.3.

## 3. Bruno en RED

Skills: `bruno-cli`.

- [x] 3.1 `bruno/catalogs/folder.yml` (seq 7, después de `transactions/`) con `auth: inherit`.
- [x] 3.2 `tipos-de-cuenta.yml` y `tipos-de-movimiento.yml`: 200, los códigos en el orden de la spec,
  las etiquetas de `proposal.md` y exactamente las claves `code` y `description`.
- [x] 3.3 `monedas.yml`: 200, incluye COP con nombre y símbolo de la semilla, códigos en orden
  alfabético, exactamente `code`, `name` y `symbol`, y sin `XTS`.
- [x] 3.4 `categorias.yml`: 200 con los mismos ids y el mismo orden que `idsCategoriasEscenario`
  (guardado por `categories/Listar categorias`), exactamente `id` y `name`.
  `categorias-gasto.yml`: con `appliesTo=EXPENSE` trae `Mercado` y `Ajustes` (BOTH) y ninguna de solo
  ingreso. `categorias-filtro-invalido.yml`: `appliesTo=GASTO` da 400 `VALIDATION_ERROR` en
  `appliesTo`.
- [x] 3.5 `sin-credenciales-*.yml`, uno por ruta, sin clave `auth`: 401 `UNAUTHENTICATED` en
  `authorization` con `WWW-Authenticate: Bearer`. `monedas-con-basic.yml`: el Basic compartido da 401
  `UNAUTHENTICATED`.
- [x] 3.6 Ver la carpeta fallar con 404 en los caminos felices antes de escribir el controlador
  (`verificar-bruno.ps1 -RecargarDatos -Objetivo catalogs`). Los 401 pasan desde el inicio: la cadena
  JWT ya cubre toda ruta.

## 4. Monedas activas

Skills: `java-architect`.

- [x] 4.1 `ListCurrenciesUseCaseTest`: devuelve en orden lo que entrega
  `CurrencyQueryPort.findActive()`. Verlo fallar.
- [x] 4.2 `Currency(code, name, symbol)` en `domain/model`, `findActive()` en `CurrencyQueryPort`,
  `ListCurrenciesPort` y `ListCurrenciesUseCase`. Verificar con `ListCurrenciesUseCaseTest` en verde.
- [x] 4.3 `CurrencyR2dbcAdapter.findActive()`: `SELECT code, name, symbol ... WHERE is_active ORDER BY
  code`. Lo verifica `monedas.yml` en 3.3 (los adapters R2DBC no tienen test en la suite).

## 5. Controlador de catálogos

Skills: `java-architect`, `java-exceptions`, `java-security`.

- [x] 5.1 `CatalogControllerTest` (`@WebFluxTest`): las cuatro rutas responden con su forma, las
  etiquetas de cada valor de los dos enums, `appliesTo` parseado y pasado al caso de uso, 400 en
  `appliesTo` con `GASTO` y con `expense`, 401 sin credencial y con un subject que no es UUID. Verlo
  fallar.
- [x] 5.2 `CatalogController` (`@RequestMapping("/catalogs")`) y sus DTOs en `dto/`:
  `CatalogItemResponse` con `switch` exhaustivo por enum, `CurrencyResponse` y
  `CatalogCategoryResponse`. Verificar con `CatalogControllerTest` en verde y `CategoryControllerTest`
  sin cambios y en verde.
- [x] 5.3 `CatalogsIT`: 401 con `WWW-Authenticate: Bearer` en la ruta con base-path y 404 sin el
  prefijo. Verificar con la clase en verde.

## 6. Contrato y colección personal

- [x] 6.1 `docs/api/contrato-api.md`: las cuatro rutas en el índice y su sección, con la tabla de
  etiquetas. Verificar leyendo el documento.
- [x] 6.2 Replicar en `bruno-personal/catalogos/` un request por catálogo, con `auth: inherit` y
  `appliesTo=EXPENSE` en el de categorías. No se ejecuta: apunta a Neon. Verificar leyendo los
  archivos contra los de `bruno/catalogs/`.

## 7. Verificación

- [x] 7.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea reales.
- [x] 7.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  requests, tests y aserciones reales. Incluye `bruno/catalogs/` completo.
