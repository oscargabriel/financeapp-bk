# Tasks

## 1. Bruno en RED

Skills: `bruno-cli`.

- [x] 1.1 En `bruno/categories/gasto-en-huerta.yml`, guardar el id del gasto creado en
  `idGastoHuerta`. Escribir a partir de seq 42, con `token: {{tokenCategorias}}` salvo donde se
  indica:
  - `borrar-categoria`: `DELETE /categories/{{idPlantas}}` (hoy `HUERTA`, con un gasto) → 204 sin cuerpo.
  - `borrar-categoria-semilla`: `idMercadoUsuarioNuevo` → 204.
  - `categorias-tras-borrar` (GET): 23 elementos, sin `idPlantas` ni `idMercadoUsuarioNuevo`.
  - `borrar-categoria-ya-borrada`: `idPlantas` otra vez → 404 `NOT_FOUND` en `id`.
  - `borrar-categoria-inexistente`: un UUID v7 que no existe → 404 `NOT_FOUND` en `id`.
  - `borrar-categoria-id-mal-formado`: `DELETE /categories/abc` → 400 `VALIDATION_ERROR` en `id`.
  - `borrar-categoria-ajena`: `idGimnasioEscenario` → 404 `NOT_FOUND` en `id`.
  - `categoria-ajena-sigue-viva` (GET con `auth: inherit`, el usuario del escenario): 24 elementos,
    `idGimnasioEscenario` presente.
  - `reporte-con-categoria-borrada` (GET `/reports/transactions` de ayer a mañana, con las fechas
    calculadas en un script previo): el gasto `idGastoHuerta` trae `categoryId` `idPlantas` y
    `categoryName` `HUERTA`, y `totalsByCategory` tiene la entrada de `idPlantas` con 20000.
  - `modificar-gasto-de-categoria-borrada`: `PATCH /transactions/{{idGastoHuerta}}` con
    `{"amount": 25000}` → 200 con `categoryId` `idPlantas`.
  - `gasto-con-categoria-borrada`: `POST /transactions` con un gasto en `idCuentaCategorias` y
    categoría `idPlantas` → 400 `VALIDATION_ERROR` en `[0].categoryId`.
  - `recrear-categoria-borrada`: `POST /categories` con
    `{"name": "huerta", "appliesTo": "EXPENSE", "icon": "leaf", "color": "#558B2F"}` → 201 con un
    `id` distinto de `idPlantas`; guarda `idHuertaNueva`.
  - `categorias-tras-recrear` (GET): 24 elementos, `idHuertaNueva` al final.
  - `borrar-categoria-sin-credenciales` (sin `auth`) y `borrar-categoria-con-basic`: sobre
    `idHuertaNueva` → 401 `UNAUTHENTICATED` en `authorization` y `WWW-Authenticate: Bearer`.

  Verificar que hoy fallan (405 en los DELETE) con `verificar-bruno.ps1 -RecargarDatos`.

## 2. Dominio y puertos

Skills: `java-architect`.

- [x] 2.1 Crear `DeleteCategoryPort` (`Mono<Void> delete(UUID userId, UUID categoryId)`) y sumar
  `Mono<Boolean> softDelete(UUID categoryId, UUID userId)` a `CategoryRepositoryPort`. Verificar con
  `.\gradlew.bat compileJava`.

## 3. Caso de uso

Skills: `java-architect`, `java-exceptions`.

- [x] 3.1 `DeleteCategoryUseCaseTest` en RED, con `StepVerifier` y el puerto mockeado: si
  `softDelete` devuelve `true`, completa vacío; si devuelve `false`, da 404 `NOT_FOUND` en `id`.
- [x] 3.2 Implementar `DeleteCategoryUseCase`, con `UpdateCategoryUseCase.noEncontrada()` visible
  en el paquete. Verificar con `DeleteCategoryUseCaseTest` y `UpdateCategoryUseCaseTest` en verde.

## 4. Web

Skills: `java-architect`, `java-exceptions`, `java-security`.

- [x] 4.1 `CategoryControllerTest` en RED: un DELETE válido llega al puerto con el usuario del token
  y el id, y responde 204 sin cuerpo; id mal formado da 400 en `id` sin llamar al puerto; el 404 del
  puerto se propaga con su código y campo. Implementar el `@DeleteMapping("/{id}")` en
  `CategoryController`. Verificar con la clase en verde.
- [x] 4.2 `CategoriesIT`: el DELETE sin credencial y con el Basic compartido dan 401 con
  `WWW-Authenticate: Bearer`. Verificar con la clase en verde.

## 5. Persistencia

- [x] 5.1 Implementar `softDelete` en `CategoryR2dbcAdapter`: `UPDATE finance.categories SET
  deleted_at = now()` filtrado por `id`, `user_id` y `deleted_at IS NULL`, con bind variables, y
  `rowsUpdated() > 0`. Sin test de suite (adapter excluido de cobertura): lo verifican los requests
  del grupo 1.1 en verde.

## 6. Documentación y colección personal

Skills: `bruno-cli`.

- [x] 6.1 `docs/api/contrato-api.md`: `DELETE /api/categories/{id}` en el índice y su sección (204,
  borrado lógico, qué pasa con los movimientos, nombre liberado, 400 y 404). Quitar "borrar
  categorías" de lo que el API todavía no tiene. Verificar leyendo las secciones.
- [x] 6.2 `docs/database/modelo-datos.md`: bajo `v_monthly_spending_by_category`, la decisión de que
  una categoría borrada sigue apareciendo en los meses con gasto y por qué. Verificar después de la
  corrida de Bruno con una consulta psql contra la base local: la vista sigue trayendo `HUERTA` del
  usuario de la corrida, con `deleted_at` no nulo en `categories`.
- [x] 6.3 `bruno-personal/categorias/borrar.yml`: el DELETE con un id de ejemplo, `docs` con lo que
  pasa con los movimientos y el nombre. Ajustar el `docs` de `crear.yml`, que dice que borrar todavía
  no se puede. Comparar ruta y autenticación con `bruno/categories/borrar-categoria.yml`. No se ejecuta.

## 7. Verificación

- [x] 7.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea reales.
- [x] 7.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  requests, tests y aserciones reales.
