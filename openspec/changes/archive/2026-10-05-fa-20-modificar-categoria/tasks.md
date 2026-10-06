# Tasks

## 1. Escenario y Bruno en RED

Skills: `bruno-cli`.

- [x] 1.1 `docs/database/test-data.sql`: dar a "Cigarrillos" el id fijo
  `30000000-0000-7000-8000-000000000170` con un `\set`, como las cuentas. En
  `bruno/categories/listar-categorias.yml`, guardar el id de `Gimnasio` en `idGimnasioEscenario`; en
  `categorias-tras-el-alta.yml`, el de `Mercado` del usuario nuevo en `idMercadoUsuarioNuevo`.
  Verificar que la colección sigue en verde contra la app de `dev`.
- [x] 1.2 Escribir en `bruno/categories/`, a partir de seq 20, con `token: {{tokenCategorias}}` salvo
  donde se indica:
  - `modificar-categoria`: `idPlantas` con `{"name": " Huerta ", "icon": "leaf", "color": "#558b2f"}`
    → 200, `Huerta`, `leaf`, `#558B2F`, mismo `id` y `appliesTo` `EXPENSE`.
  - `modificar-categoria-campo-en-null`: `{"color": "#33691E", "icon": null}` → 200, `icon` sigue `leaf`.
  - `modificar-categoria-semilla`: `idMercadoUsuarioNuevo` con `{"color": "#1B5E20"}` → 200,
    `isSystem` `true`.
  - `categorias-tras-modificar` (GET): 25 elementos (el `Gimnasio` del alta cuenta); `Huerta` en la posición 22 con los datos del
    PATCH, `Mercado` primera con el color nuevo.
  - `modificar-categoria-nombre-repetido`: `{"name": "BONOS"}` → 409 `DUPLICATE_RESOURCE` en `name`.
  - `modificar-categoria-mismo-nombre`: `{"name": "HUERTA"}` → 200 con `HUERTA`.
  - `modificar-categoria-parche-vacio`: `{}` → 400 en `body`.
  - `modificar-categoria-campos-invalidos`: `{"name": " ", "appliesTo": "GASTO", "icon": "", "color": "rojo"}`
    → 400 con `name`, `appliesTo`, `icon` y `color`, comparados por contenido.
  - `modificar-categoria-id-mal-formado`: `PATCH /categories/abc` → 400 en `id`.
  - `modificar-categoria-inexistente`: un UUID v7 que no existe → 404 `NOT_FOUND` en `id`.
  - `modificar-categoria-borrada`: con `{{accessToken}}` (el usuario del escenario), la de
    Cigarrillos → 404 en `id`.
  - `modificar-categoria-ajena`: `idGimnasioEscenario` con `{"name": "Robada"}` → 404 en `id`.
  - `categoria-ajena-intacta` (GET con `{{accessToken}}`): sigue `Gimnasio`, no hay `Robada`.
  - `cuenta-para-categorias` (POST `/accounts`): una cuenta CASH en COP; guarda `idCuentaCategorias`.
  - `gasto-en-huerta` (POST `/transactions`): un gasto en esa cuenta con categoría `idPlantas` → 201.
  - `modificar-alcance-incompatible`: `{"appliesTo": "INCOME", "color": "#000000"}` → 409
    `RESOURCE_IN_USE` en `appliesTo`.
  - `modificar-alcance-a-ambos`: `{"appliesTo": "BOTH"}` → 200 `BOTH` con `color` todavía `#33691E`.
  - `modificar-alcance-sin-movimientos`: `idBonos` con `{"appliesTo": "EXPENSE"}` → 200 `EXPENSE`.
  - `modificar-categoria-sin-credenciales` (sin `auth`) y `modificar-categoria-con-basic` → 401
    `UNAUTHENTICATED` en `authorization` y `WWW-Authenticate: Bearer`.

  Verificar que hoy fallan con `verificar-bruno.ps1 -RecargarDatos`.

## 2. Dominio y puertos

Skills: `java-architect`, `java-exceptions`.

- [x] 2.1 Crear `UpdateCategoryCommand` (campos como texto) y `UpdateCategoryPort`; sumar a
  `CategoryRepositoryPort` `findActiveByIdAndUser`, `hasTransactionsOfType` y `update`; agregar
  `RESOURCE_IN_USE` a `ErrorCodes`. Verificar con `.\gradlew.bat compileJava`.

## 3. Caso de uso

Skills: `java-architect`, `java-exceptions`.

- [x] 3.1 `UpdateCategoryUseCaseTest` en RED, con `StepVerifier` y el puerto mockeado: parche de
  nombre, icono y color normalizados conserva el resto; `null` no cambia; categoría de la semilla
  conserva `isSystem`; categoría no encontrada da 404 `NOT_FOUND` en `id` sin llamar a `update`;
  `EXPENSE`→`INCOME` con gastos da 409 `RESOURCE_IN_USE` en `appliesTo` sin `update`;
  `INCOME`→`EXPENSE` consulta ingresos; cambio a `BOTH` o al mismo alcance no consulta movimientos;
  sin movimientos permite el cambio; el 409 de nombre del puerto se propaga; `update` vacío da 404.
- [x] 3.2 Implementar `UpdateCategoryUseCase`. Verificar con `UpdateCategoryUseCaseTest` en verde.

## 4. Web

Skills: `java-architect`, `java-exceptions`, `java-security`.

- [x] 4.1 `UpdateCategoryRequestTest` en RED: todo opcional; cada campo en blanco da violación en su
  campo; nombre de 61, `GASTO`, icono de 41 y `rojo` también; `sinCambios()` con todo `null`.
  Implementar `UpdateCategoryRequest`. Verificar con la clase en verde.
- [x] 4.2 `CategoryControllerTest` en RED: PATCH válido llega al puerto con el usuario del token, el
  id y el cuerpo, y responde 200; parche vacío 400 en `body` sin llamar al puerto; id mal formado
  400 en `id`; el 404 y los dos 409 del puerto se propagan con su código y campo. Implementar el
  `@PatchMapping` en `CategoryController`. Verificar con la clase en verde.
- [x] 4.3 `CategoriesIT`: PATCH sin credencial y con el Basic compartido dan 401 con
  `WWW-Authenticate: Bearer`. Verificar con la clase en verde.

## 5. Persistencia

- [x] 5.1 Implementar en `CategoryR2dbcAdapter` el `SELECT` por id y usuario entre las vivas, el
  `EXISTS` sobre `finance.transactions` por categoría y tipo, y el `UPDATE ... RETURNING` filtrado
  por `id`, `user_id` y `deleted_at IS NULL`, con el choque de nombre traducido a 409. Todo con bind
  variables. Sin test de suite (adapter excluido de cobertura): lo verifican los requests del grupo
  1.2 en verde.

## 6. Contrato y colección personal

Skills: `bruno-cli`.

- [x] 6.1 `docs/api/contrato-api.md`: `PATCH /api/categories/{id}` en el índice y su sección (cuerpo,
  semántica de `null`, campos que no se vacían, 404, los dos 409), `RESOURCE_IN_USE` donde se listan
  los códigos, y quitar "editar categorías" de lo que el API todavía no tiene. Verificar leyendo las
  secciones.
- [x] 6.2 `bruno-personal/categorias/modificar.yml`: el PATCH con todos los campos y valores de
  ejemplo, `docs` con la regla de `null`, los campos que no se vacían y el 409 de alcance. Comparar
  ruta, cuerpo y autenticación con `bruno/categories/modificar-categoria.yml`. No se ejecuta.

## 7. Verificación

- [x] 7.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea reales.
- [x] 7.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  requests, tests y aserciones reales.
