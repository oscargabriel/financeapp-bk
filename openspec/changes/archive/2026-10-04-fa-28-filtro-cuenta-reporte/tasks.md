# Tasks

## 1. Bruno en RED

Skills: `bruno-cli`.

- [x] 1.1 Agregar a `bruno/reports/` los requests de la cuenta, con `seq` después de
  `reporte-sin-movimientos-ajenos`, contra el usuario del escenario (`{{accessToken}}`) y el mes en
  curso (`{{primerDia}}`–`{{ultimoDia}}`). Las cifras salen de `test-data.sql`.
  - `cuentas-escenario`: `GET /accounts` y `setVar` de los ids de Efectivo, Bancolombia y Ahorros USD
    por nombre, porque cambian en cada recarga.
  - `reporte-filtro-cuenta`: Efectivo → 5 movimientos: 4 gastos que suman 227500 y el retiro de cajero
    de 300000, con `destinationAccountId` igual a Efectivo. `totalsByType`: EXPENSE (4), INCOME en
    cero y TRANSFER (1).
  - `reporte-cuenta-origen-transferencia`: Ahorros USD → solo "Cambio de dólares", con `accountId`
    igual a Ahorros USD.
  - `reporte-filtro-varias-cuentas`: Efectivo y Bancolombia separados por coma → 11 movimientos, sin
    ids repetidos. El retiro de cajero aparece una vez. EXPENSE 1697500 (7), INCOME 5300000 (2) y
    TRANSFER 710000 (2).
  - `reporte-cuenta-y-tipo`: Bancolombia con `type=EXPENSE,TRANSFER` → 5 movimientos: sus 3 gastos y
    las 2 transferencias, la que sale y la que entra. `totalsByType`: EXPENSE 1470000 (3) y TRANSFER
    710000 (2). No sirve `type=TRANSFER` solo: las dos transferencias del mes tocan Bancolombia, así
    que el request pasaría sin el filtro de cuenta.
  - `reporte-cuenta-y-categoria`: Efectivo con Mercado → solo "Fruta", 42500.
  - `reporte-cuenta-ajena`: `{{idCuentaReportes}}`, la cuenta del segundo usuario que ya tiene un
    gasto en el mes, con `{{accessToken}}` → 200, lista vacía.
  - `reporte-cuenta-mal-formada`: `accountId={{idEfectivoEscenario}},abc` → 400 `VALIDATION_ERROR` en el campo
    `accountId`.
- [x] 1.2 Actualizar el `docs` de `bruno/reports/folder.yml` con los movimientos del mes por cuenta.
- [x] 1.3 Verificar que los requests nuevos fallan hoy por la razón correcta. Desde `bruno/`, correr
  `bru run reports -r --env local` después de `auth/`. Hoy el parámetro se ignora: los filtros traen
  los 15 movimientos, la cuenta ajena no da lista vacía y el mal formado da 200, no 400.

## 2. Dominio

Skills: `java-architect`.

- [x] 2.1 `TransactionReportTest` en RED: `TransactionReportFilter` recibe `accountIds` después de
  `categoryIds`, y un `null` queda como conjunto vacío. Agregar `conCuentas(...)` a `ReportMother` y
  actualizar las construcciones existentes del filtro.
- [x] 2.2 Agregar `accountIds` a `TransactionReportFilter`. Verificar con `TransactionReportTest` en
  verde.

## 3. Caso de uso

Skills: `java-architect`, `java-exceptions`.

- [x] 3.1 `GetTransactionReportUseCaseTest` en RED: el filtro que llega al puerto de salida trae los
  `accountIds` recibidos. El rango invertido sigue siendo 400 en `from`, sin llamar al puerto.
- [x] 3.2 Agregar `accountIds` a `GetTransactionReportPort.get` y a `GetTransactionReportUseCase`.
  Verificar con la clase en verde.

## 4. Web

Skills: `java-architect`, `java-exceptions`.

- [x] 4.1 `TransactionReportControllerTest` en RED:
  - `accountId` repetido y separado por coma llega al puerto como conjunto de UUID, con los blancos
    ignorados;
  - sin `accountId` llega un conjunto vacío;
  - `accountId=abc` y `accountId=<uuid>,abc` dan 400 `VALIDATION_ERROR` en `accountId` sin llamar al
    puerto.
  Los 401 los sigue cubriendo `TransactionReportIT`, sin cambios.
- [x] 4.2 Leer `accountId` en `TransactionReportController` con el mismo `parseAll` que `categoryId`.
  Verificar con la clase en verde.

## 5. Persistencia

- [x] 5.1 En `TransactionReportR2dbcAdapter`, agregar el fragmento fijo
  `AND (t.account_id = ANY(:accountIds) OR t.destination_account_id = ANY(:accountIds))`, con el bind
  del arreglo solo cuando viene el filtro (design.md). No tiene test de suite: el adapter está fuera
  de la cobertura. Lo verifican los requests del grupo 1.1 en verde.

## 6. Contrato y colección personal

Skills: `bruno-cli`.

- [x] 6.1 `docs/api/contrato-api.md`: `accountId` en la tabla de parámetros y en los errores, y el
  párrafo que explica tres cosas:
  - entran el origen y el destino;
  - una transferencia sale una sola vez;
  - los totales no llevan signo.
  Verificar leyendo la sección.
- [x] 6.2 `bruno-personal/reportes/movimientos.yml`: agregar `accountId` habilitado con la cuenta real
  que ya usan los requests de `movimientos/`, y su fila en la tabla del `docs`. Comparar ruta,
  parámetros y autenticación con `bruno/reports/`. No se ejecuta.

## 7. Verificación

- [x] 7.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea reales.
- [x] 7.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  los requests, tests y aserciones reales.
