# Tasks

## 1. Esquema, escenario y Bruno en RED

Skills: `bruno-cli`.

- [x] 1.1 `docs/database/schema.sql`:
  - Tabla `finance.recurrences` con la plantilla (`user_id`, `account_id`, `category_id`, `type`,
    `amount`, `currency_code`, `description`), la regla (`frequency`, `interval_count`,
    `day_of_week`, `day_of_month`, `start_date`), el fin (`end_date`, `occurrence_limit`), los
    contadores (`generated_count`, `prior_count`), `status` y las fechas de auditoría. Sus `CHECK` de
    forma (día según la frecuencia, fin exclusivo, rangos), su trigger de `updated_at`, sus comentarios
    y un índice por usuario de las activas.
  - `finance.transactions.recurrence_id`, con FK diferida e índice parcial.

  Emitirlo en `docs/database/update/20261009_04_series_recurrentes.sql`, con encabezado (va ANTES de
  desplegar la app; en local hay que recargar los datos) y la reversión comentada. Documentar en
  `docs/database/modelo-datos.md` las series, sus contadores y la regla de que toda lectura de saldos o
  movimientos pone al día las series sin fin. Aplicarlo a la base local y correr la comparación de
  esquemas: tiene que salir limpia.
- [x] 1.2 `docs/database/test-data.sql`: usuario nuevo `series@financeapp.local` (lo borra y lo recrea
  el script, como a los demás del back) con tres cuentas `CASH` en COP de saldo conocido y una serie
  `WEEKLY` sin fin atrasada: empezó hace cuatro semanas y tiene `generated_count` 2, pero solo existe
  la primera ocurrencia (la segunda la «borró a mano»). `docs/database/demo-data.sql`: una suscripción
  mensual sin fin para los usuarios de demo, con su ocurrencia programada. Recargar con
  `cargar-datos-local.sql`.
- [x] 1.3 `recurrenceId` en las listas exactas de claves de `transactions/lote-de-uno.yml`,
  `pending/listar-pendientes.yml` y `reports/reporte-mes.yml`, con `null` en los movimientos del
  escenario. `transactions/`: un elemento del lote con `recurrenceId` en el cuerpo sale con `null`.
- [x] 1.4 Carpeta nueva `bruno/recurrences/` con bearer de carpeta y el usuario `series@`. Las fechas
  las calculan los scripts, en la zona de Bogotá. Requests, cada uno con sus aserciones:
  - Login, ids de las tres cuentas y de las categorías de gasto e ingreso; ids de una cuenta y una
    serie de `prueba@` para los casos ajenos (los fijos de `test-data.sql`).
  - Puesta al día: `GET /api/accounts` → la cuenta de la serie atrasada descuenta la primera y las
    ocurrencias desde la tercera hasta hoy, no la segunda; `reports/transactions` de las últimas cinco
    semanas → esas ocurrencias con su `recurrenceId` y una programada; `GET /api/recurrences` → la serie
    con `nextOccurrenceAt`.
  - Altas: mensual con `occurrences` 3 (campos de la respuesta, 3 ocurrencias programadas a las
    `05:00:00Z` del 15); día 31 desde el 31 de enero del año siguiente con 4 (31-ene, 28/29-feb,
    31-mar, 30-abr); semanal cada dos semanas desde un miércoles; semanal con `endDate` (4
    ocurrencias); mensual desde hace dos meses (el saldo baja dos cuotas); semanal sin fin desde hoy
    (2 ocurrencias); sin fin desde dentro de diez días (1); sin `interval` → 1.
  - Errores del alta (400 con su campo): `dayOfMonth` 32, `interval` 0, `endDate` antes del inicio,
    `dayOfWeek` en mensual, semanal sin `dayOfWeek`, `endDate` con `occurrences`, `TRANSFER`, cuenta
    ajena, diez años semanales, y sin `amount` con `interval` 0 juntos.
  - Listado: la serie con fin ya pasada no aparece; la de `prueba@` tampoco.
  - Edición: `FUTURE` con monto (saldo igual, futuras cambiadas, pasada igual); `ALL` con monto (saldo
    baja la diferencia); `ALL` con cuenta (saldos de las dos cuentas); de mensual a semanal con
    `occurrences` (pasadas conservadas, futuras rehechas en viernes); a `MONTHLY` sin `dayOfMonth`,
    sin `scope`, solo `scope`, `interval` 0 → 400 con su campo; serie ajena → 404.
  - A mano: `PATCH /api/transactions/{id}` de una ocurrencia → mismo `recurrenceId`.
  - Cancelación: 204; la pasada sigue en el reporte, las futuras no; fuera del listado; otra vez →
    404; id mal formado → 400.
  - 401: alta sin credencial, listado con el Basic, edición sin credencial y cancelación con el Basic,
    con `UNAUTHENTICATED` en `authorization`.

  Verificar que los requests nuevos fallan hoy con `verificar-bruno.ps1 -RecargarDatos`, y que el resto
  de la colección sigue en verde.
- [x] 1.5 `bruno-personal/`: carpeta `series/` con alta, listado, edición y cancelación (ruta, cuerpo y
  bearer iguales a `bruno/recurrences/`). En `movimientos/registrar.yml`, una nota de que
  `recurrenceId` se ignora. No se ejecutan.

## 2. Dominio

Skills: `java-architect`.

- [x] 2.1 `RecurrenceRuleTest` en RED: primera ocurrencia igual o posterior al inicio en el día pedido;
  semanal cada X semanas; mensual cada X meses con el día anclado (31 → 28/29 → 31 → 30); medianoche en
  la zona dada, también en un cambio de horario. `Frequency` y `RecurrenceRule`. Verificar con la clase
  en verde.
- [x] 2.2 `RecurrenceTest` en RED: con fin por fecha y por número crea todas; sin fin crea hasta hoy
  más una; la puesta al día parte del contador y no recrea las borradas; el tope de 500; el cambio de
  regla descuenta las ocurridas, empieza mañana y respeta el total, el fin o la siguiente. `Recurrence`,
  `RecurrenceStatus` y `RecurrenceScope`. Verificar con la clase en verde.
- [x] 2.3 `Transaction` y `ReportedTransaction` ganan `recurrenceId`. Ajustar los Object Mother y los
  usos. Verificar con la suite en verde.

## 3. Aplicación

Skills: `java-architect`, `java-exceptions`.

- [x] 3.1 `CreateRecurrenceUseCaseTest` en RED: valida cuenta y categoría con `ReferenciasDelUsuario`
  (errores en `accountId` y `categoryId`); el tope sobre `endDate` o `startDate`; guarda la serie con sus
  ocurrencias usando la zona del perfil y el `Clock`. Puertos `CreateRecurrencePort` y
  `RecurrenceRepositoryPort`. Verificar con la clase en verde.
- [x] 3.2 `SeriesAlDiaTest` en RED: sin series sin fin no lee el perfil ni escribe; con una atrasada
  agrega las que faltan con el contador esperado; si el contador ya cambió, sigue sin error. Llamarla
  desde `ListAccountsUseCase`, `GetBalanceUseCase`, `GetMonthlySpendingUseCase` y
  `GetTransactionReportUseCase` antes de leer; un test por caso de uso que lo compruebe. Verificar con
  esas clases en verde.
- [x] 3.3 `ListRecurrencesUseCaseTest`, `UpdateRecurrenceUseCaseTest` y `CancelRecurrenceUseCaseTest`
  en RED: el listado pone al día antes de leer; la edición aplica el parche sobre la serie guardada,
  valida la resultante, rehace las futuras si cambió la regla y responde 404 si no existe; la
  cancelación responde 404 si no había serie activa que cancelar. Sus puertos de entrada. Verificar con
  las tres clases en verde.

## 4. Web

Skills: `java-architect`, `java-security`, `java-exceptions`.

- [x] 4.1 `CreateRecurrenceRequestTest` y `UpdateRecurrenceRequestTest` en RED: cada regla de formato y
  de forma de la tabla de `design.md`, con su campo, y `sinCambios()`. Los records y una constraint de
  clase para las reglas que cruzan campos, en `dto/validation/`. Verificar con las dos clases en verde.
- [x] 4.2 `RecurrenceControllerTest` en RED: 201, 200, 200 y 204 con la forma de `RecurrenceResponse`;
  id mal formado → 400 en `id`; parche sin campos → 400 en `body`; `scope` ausente → 400 en `scope`.
  `RecurrenceController` y `RecurrenceResponse`. Verificar con la clase en verde.
- [x] 4.3 `TransactionControllerTest` y `TransactionReportControllerTest` en RED: `recurrenceId` en la
  respuesta (null y con valor). `TransactionResponse` y el ítem de `TransactionReportResponse` lo
  devuelven. Verificar con las dos clases en verde.
- [x] 4.4 `RecurrencesIT`: las cuatro rutas sin credencial y con el Basic dan 401 con `UNAUTHENTICATED`
  en `authorization` y `WWW-Authenticate: Bearer`. Verificar con la clase en verde.

## 5. Persistencia

- [x] 5.1 `RecurrenceR2dbcAdapter`: guardar la serie con sus ocurrencias, listar las activas con
  `nextOccurrenceAt`, buscar una activa del usuario, leer las sin fin activas, agregar ocurrencias con
  el `UPDATE` condicionado al contador, editar con alcance (y rehacer las futuras) y cancelar, cada
  escritura en una transacción. `TransactionR2dbcAdapter` y `TransactionReportR2dbcAdapter` leen
  `recurrence_id`, y el `INSERT` lo escribe. Todo con parámetros enlazados. Sin test de suite: lo
  verifican en verde los requests del grupo 1. La puesta al día concurrente no se puede probar desde
  Bruno, que corre en serie: queda cubierta por `SeriesAlDiaTest` y por el `WHERE` del contador.

## 6. Contrato

- [x] 6.1 `docs/api/contrato-api.md`: sección de series con las cuatro rutas, los cuerpos, la
  respuesta, el alcance, la regla de fechas, la puesta al día y los errores; `recurrenceId` en la
  respuesta de un movimiento y en el ítem de `reports/transactions`. Verificar leyendo las secciones.

## 7. Verificación

- [x] 7.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea reales.
- [x] 7.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  los conteos reales de requests, tests y aserciones.
