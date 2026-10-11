# Tasks

## 1. Bruno en RED

Skills: `bruno-cli`.

- [x] 1.1 Escribir en `bruno/categories/`, a partir de seq 10 (después de "Categorias de un usuario
  nuevo", que exige las 22 de la semilla), todos con `token: {{tokenCategorias}}` salvo donde se
  indica:
  - `crear-categoria`: `{"name": "  Plantas  ", "appliesTo": "expense", "icon": "sprout", "color": "#7cb342"}`
    → 201, `id` v7, nombre recortado, `EXPENSE`, color en mayúsculas, `isSystem` false, exactamente
    las seis claves del listado. Guarda `idPlantas`.
  - `crear-categoria-sin-icono-ni-color`: `{"name": "Bonos", "appliesTo": "INCOME", "icon": " ", "color": ""}`
    → 201 con `icon` y `color` en null.
  - `categorias-tras-el-alta` (GET `/categories`): 24 elementos; los dos últimos son `Plantas` y
    `Bonos`, en ese orden, con los datos de las respuestas del alta.
  - `crear-categoria-nombre-repetido`: `" PLANTAS "` → 409 `DUPLICATE_RESOURCE` en `name`.
  - `crear-categoria-repite-semilla`: `"mercado"` → 409 `DUPLICATE_RESOURCE` en `name`.
  - `crear-categoria-nombre-de-otro-usuario`: `"Gimnasio"` (propia del usuario del escenario) → 201.
  - `crear-categoria-applies-to-invalido`: `"GASTO"` → 400 `VALIDATION_ERROR` en `appliesTo`.
  - `crear-categoria-campos-invalidos`: `{"appliesTo": "GASTO", "color": "rojo"}` → 400 con
    errores en `name`, `appliesTo` y `color`, comparados por contenido.
  - `crear-categoria-sin-credenciales` (sin `auth`) y `crear-categoria-con-basic` → 401
    `UNAUTHENTICATED` en `authorization` y `WWW-Authenticate: Bearer`.

  Verificar que hoy fallan con `bru run categories -r --env local` desde `bruno/`, contra la app
  de `dev` levantada en local.

## 2. Dominio y puertos

Skills: `java-architect`.

- [x] 2.1 Crear `CreateCategoryCommand` (campos como texto, igual que `CreateAccountCommand`),
  `NewCategory`, `CreateCategoryPort` y `CategoryRepositoryPort` con `Mono<Category> create(NewCategory)`.
  Verificar con `.\gradlew.bat compileJava`.

## 3. Caso de uso

Skills: `java-architect`, `java-exceptions`.

- [x] 3.1 `CreateCategoryUseCaseTest` en RED, con `StepVerifier`, `Clock` fijo y el puerto
  mockeado: el `NewCategory` lleva un UUID v7 del instante del reloj, el usuario recibido, el
  nombre recortado, el `CategoryScope` desde minúsculas, el color recortado en mayúsculas, e `icon`
  y `color` en blanco como `null`; devuelve lo que devuelve el puerto; el 409 del puerto se
  propaga sin cambios.
- [x] 3.2 Implementar `CreateCategoryUseCase`. Verificar con `CreateCategoryUseCaseTest` en verde.

## 4. Web

Skills: `java-architect`, `java-exceptions`, `java-security`.

- [x] 4.1 `CreateCategoryRequestTest` en RED: cuerpo válido sin violaciones; `name` ausente, en
  blanco y de 61 caracteres; `appliesTo` ausente y `GASTO`; `icon` de 41 caracteres; `color`
  `rojo` y `#12345`; `icon` y `color` vacíos sin violación. Cada violación sobre su campo.
  Implementar `CreateCategoryRequest` y `Formatos.COLOR_HEX`. Verificar con la clase en verde.
- [x] 4.2 `CategoryControllerTest` en RED: POST válido llega al puerto con el usuario del token y
  responde 201 con el contrato del listado; cuerpo inválido da 400 con los tres campos sin llamar
  al puerto; el 409 del puerto sale como 409 `DUPLICATE_RESOURCE` en `name`; cuerpo que no es JSON
  da 400 `JSON_PARSING_ERROR`. Implementar el `@PostMapping` en `CategoryController`. Verificar con
  la clase en verde.
- [x] 4.3 `CategoriesIT`: POST sin credencial y con el Basic compartido dan 401 con
  `WWW-Authenticate: Bearer`. Verificar con la clase en verde.

## 5. Persistencia

- [x] 5.1 Implementar `CategoryRepositoryPort` en `CategoryR2dbcAdapter`: el `INSERT ... SELECT`
  con el `sort_order` calculado de design.md, `bindNull` para `icon` y `color`, y
  `DuplicateKeyException` traducida a 409 `DUPLICATE_RESOURCE` en `name`. Sin test de suite
  (adapter excluido de cobertura): lo verifican los requests del grupo 1 en verde.

## 6. Contrato, colección personal y tablero

Skills: `bruno-cli`.

- [x] 6.1 `docs/api/contrato-api.md`: `POST /api/categories` en el índice y su sección con cuerpo,
  respuesta, normalización, posición en la lista y errores (400, 409, 401). Verificar leyendo la
  sección.
- [x] 6.2 `bruno-personal/categorias/crear.yml`: el POST con todos los campos y valores de
  ejemplo, `docs` con los campos opcionales y el 409. Comparar ruta, cuerpo y autenticación con
  `bruno/categories/crear-categoria.yml`. No se ejecuta.
- [x] 6.3 Agregar a los criterios de FA-21 en Notion: un request de `bruno/categories/` que borre
  una categoría del usuario nuevo y vuelva a crear otra con el mismo nombre, esperando 201 (el
  escenario "El nombre lo usa una categoría borrada" de esta spec). Verificar leyendo la página.

## 7. Verificación

- [x] 7.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea reales.
- [x] 7.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  requests, tests y aserciones reales.
