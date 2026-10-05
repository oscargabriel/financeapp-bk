# Tasks

## 1. Bruno en RED

Skills: `bruno-cli`.

- [x] 1.1 Crear `bruno/reports/` (`folder.yml` con `auth: inherit`, `seq` después de
  `monthly-spending/`) y los requests contra el usuario del escenario (`{{accessToken}}`), con las
  fechas calculadas en `before-request` porque `test-data.sql` es relativo al mes en curso:
  - `reporte-mes`: primer y último día del mes → 200, 15 movimientos ordenados por `occurredAt`
    descendente, `currencyCode` COP, `totalsByType` EXPENSE 1905500 (11), INCOME 5300000 (2),
    TRANSFER 710000 (2), y la suma de `totalsByCategory` igual a gastos más ingresos.
  - `reporte-filtro-categoria`: Mercado (id tomado de `GET /categories` en un request previo) → solo
    Mercado, sin transferencias.
  - `reporte-filtro-varias-categorias`: Mercado y Restaurantes, repitiendo el parámetro → dos entradas
    en `totalsByCategory`.
  - `reporte-filtro-tipo`: `type=income` → solo los 2 ingresos y una sola entrada en `totalsByType`.
  - `reporte-filtros-combinados`: Mercado y Salario separados por coma con `type=EXPENSE` → solo los
    gastos de Mercado.
  - `reporte-categoria-ajena`: un UUID bien formado que no es del usuario → 200 y lista vacía.
  - `reporte-rango-vacio`: `2000-01-01` a `2000-01-31` → 200, lista vacía, tres tipos en cero.
  - `reporte-borde-ultimo-dia`: `from` = `to` = último día del mes → incluye "Cena de fin de mes".
  - `reporte-borde-dia-siguiente`: `from` = `to` = primer día del mes siguiente → no la incluye.
  - `reporte-sin-from`, `reporte-fecha-mal-formada` (`to=2026-10-1`), `reporte-fecha-inexistente`
    (`from=2026-02-30`), `reporte-rango-invertido`, `reporte-categoria-mal-formada`,
    `reporte-tipo-invalido` → 400 `VALIDATION_ERROR` en su campo.
  - `reporte-sin-credenciales` y `reporte-con-basic` → 401 `UNAUTHENTICATED`.
- [x] 1.2 Escenario de dos usuarios en la misma carpeta: alta y login de un usuario nuevo
  (`reportes-<timestamp>@bruno.local`), lectura de su cuenta y su categoría, alta de un gasto con
  una descripción única en el mes, `reporte-otro-usuario` con su token → exactamente ese movimiento,
  y `reporte-sin-movimientos-ajenos` con `{{accessToken}}` → ninguno con esa descripción. Agregar
  el correo al comentario de limpieza de `docs/database/test-data.sql`.
- [x] 1.3 Verificar que todo `reports/` falla hoy (`bru run reports -r --env local` desde `bruno/`,
  después de `auth/` para tener `accessToken`), con 404 en las rutas del reporte.

## 2. Dominio

Skills: `java-architect`.

- [x] 2.1 `TransactionReportTest` en RED (JUnit puro): totales por tipo con los tres tipos y en
  cero sin movimientos; con filtro de tipo, solo los tipos pedidos; totales por categoría sin
  transferencias, ordenados por total descendente; suma por `amountBase`, no por `amount`.
- [x] 2.2 Crear los records `ReportedTransaction`, `TransactionReportFilter` (rango y filtros),
  `TransactionReport` con el cálculo de los totales, y los puertos `GetTransactionReportPort` y
  `TransactionReportQueryPort`. Verificar con `TransactionReportTest` en verde.

## 3. Caso de uso

Skills: `java-architect`, `java-exceptions`.

- [x] 3.1 `GetTransactionReportUseCaseTest` en RED con `StepVerifier` y el puerto de salida
  mockeado: arma el reporte con la moneda y los movimientos del puerto; rango vacío da totales en
  cero; `from` posterior a `to` da 400 `VALIDATION_ERROR` en `from` sin llamar al puerto.
- [x] 3.2 Implementar `GetTransactionReportUseCase`. Verificar con la clase en verde.

## 4. Web

Skills: `java-architect`, `java-exceptions`, `java-security`.

- [x] 4.1 `TransactionReportControllerTest` en RED: parámetros válidos llegan al puerto ya
  convertidos (UUID, enum en mayúsculas, repetidos y con coma, blancos ignorados); falta de
  `from` o `to`, formato inválido, fecha inexistente, `categoryId` mal formado y `type` inválido dan
  400 en su campo sin llamar al puerto; la respuesta lleva los campos de la spec y `occurredAt` en
  UTC.
- [x] 4.2 Implementar `TransactionReportController` (`/reports/transactions`) y sus DTOs de
  respuesta. Verificar con la clase en verde.
- [x] 4.3 Test de integración `TransactionReportIT`: sin credencial y con el Basic compartido dan 401
  `UNAUTHENTICATED` con `WWW-Authenticate: Bearer`. Verificar con la clase en verde.

## 5. Persistencia

- [x] 5.1 Implementar `TransactionReportR2dbcAdapter`: la moneda base del usuario y la consulta con
  `JOIN` a `users` y `LEFT JOIN` a `categories`, filtrada por `user_id`, los límites del día por
  `users.timezone` (design.md) y los fragmentos fijos de filtro con bind de arreglos. Sin test de
  suite (adapter excluido de cobertura): lo verifican los requests de los grupos 1.1 y 1.2 en verde.

## 6. Contrato y colección personal

Skills: `bruno-cli`.

- [x] 6.1 `docs/api/contrato-api.md`: el endpoint con parámetros, ejemplo de respuesta y errores, y
  la entrada del índice. Verificar leyendo la sección.
- [x] 6.2 `bruno-personal/reportes/movimientos.yml`: el reporte con **todos** los parámetros
  habilitados y valores de ejemplo reales (rango, varias categorías, varios tipos) y un `docs` con
  la tabla de qué devuelve cada combinación, como `gasto-mensual.yml`. Comparar ruta, parámetros y
  autenticación con `bruno/reports/`. No se ejecuta.

## 7. Verificación

- [x] 7.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea reales.
- [x] 7.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  requests, tests y aserciones reales.
