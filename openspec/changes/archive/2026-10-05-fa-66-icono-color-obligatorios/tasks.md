# Tasks

## 1. Base de datos

- [x] 1.1 Escribir `docs/database/update/20261005_01_categorias_icono_color_obligatorios.sql` con
  los `UPDATE` y los `ALTER` de design.md en una transacción, y la reversión comentada al final.
  Reflejar los `NOT NULL` en `schema.sql` (`categories` y `default_categories`).
- [x] 1.2 `test-data.sql`: "Ajustes" con icono `sliders-horizontal` y color `#607D8B`, y el
  comentario que decía que no los tenía a propósito.
- [x] 1.3 Aplicar el update a la base local `localhost/financeapp` con psql y recargar
  `test-data.sql`. Verificar con `\d finance.categories` que las dos columnas son `not null`.
- [x] 1.4 Correr la comparación de esquemas de `docs/database/modelo-datos.md`. Verificar que dice
  `sin diferencias`.

## 2. Bruno en RED

Skills: `bruno-cli`.

- [x] 2.1 En `bruno/categories/`:
  - `crear-categoria-sin-icono-ni-color.yml` → renombrar a `crear-categoria-bonos.yml`: crea
    `Bonos` con `"icon": " gift "` y `"color": "#ad1457"` → 201 con `gift` y `#AD1457`; sigue
    guardando `idBonos`.
  - `crear-categoria-sin-icono-ni-color.yml` nuevo: `{"name": "Sin adornos", "appliesTo": "INCOME"}`
    → 400 con errores en `icon` y `color`.
  - `crear-categoria-icono-color-en-blanco.yml`: `"icon": " "`, `"color": ""` → 400 en los dos.
  - `crear-categoria-campos-invalidos.yml`: ahora 4 errores (`name`, `appliesTo`, `icon`, `color`).
  - `crear-categoria-nombre-repetido`, `-repite-semilla`, `-nombre-de-otro-usuario` y
    `-applies-to-invalido`: agregar `icon` y `color` al cuerpo, para que sigan probando lo suyo.
  - `categorias-tras-el-alta.yml`: `Bonos` con `gift` y `#AD1457`.
  - `listar-categorias.yml`: "Ajustes" con su icono y color; un test nuevo de que ninguna categoría
    trae `icon` ni `color` en `null`.
  - Reordenar los `seq` para que los dos requests nuevos queden junto al alta.

  Verificar con `verificar-bruno.ps1 -RecargarDatos` que fallan los que dependen del cambio de la
  app (el 400 por ausencia y por blanco, y los 4 errores).

## 3. Web y caso de uso

Skills: `java-architect`, `java-exceptions`.

- [x] 3.1 `CreateCategoryRequestTest` en RED: sin `icon` o sin `color`, y cada uno en blanco, dan
  violación en su campo; fuera los tests de "opcionales" y "en blanco no tiene violaciones";
  "todos los errores" incluye `icon`. Agregar `@NotBlank` en `CreateCategoryRequest`. Verificar con
  la clase en verde.
- [x] 3.2 `CreateCategoryUseCaseTest`: fuera los dos tests de `null`; uno nuevo de que recorta el
  icono y pone el color en mayúsculas. Simplificar `CreateCategoryUseCase` sin `opcional()`.
  Verificar con la clase en verde.
- [x] 3.3 `CategoryMother`: `propiaSinIconoNiColor` pasa a `ajustes()` con icono y color.
  `CategoryControllerTest`: el test de "mantiene en null" se reemplaza por uno de que una categoría
  `BOTH` propia sale con su icono y color; el POST de `devuelve400ConLosCamposInvalidos` espera 4
  errores. `CatalogControllerTest` usa `ajustes()`. Verificar con las dos clases en verde.

## 4. Persistencia

- [x] 4.1 `CategoryR2dbcAdapter`: `bind` directo de `icon` y `color` en el alta y en el PATCH, sin
  las ramas de `bindNull`. Sin test de suite: lo verifican los requests de alta y modificación.

## 5. Contrato y colección personal

Skills: `bruno-cli`.

- [x] 5.1 `docs/api/contrato-api.md`: `icon` y `color` obligatorios en el alta y sin `null` en el
  listado. Verificar leyendo las secciones.
- [x] 5.2 `bruno-personal/categorias/crear.yml`: el `docs` deja de decir que son opcionales. El
  cuerpo ya los trae. No se ejecuta.

## 6. Verificación

- [x] 6.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea reales.
- [x] 6.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  requests, tests y aserciones reales.
