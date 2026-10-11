# Tasks

## 1. Esquema, escenario y Bruno en RED

Skills: `bruno-cli`.

- [x] 1.1 `docs/database/schema.sql`:
  - Tabla `finance.installment_purchases` con `user_id`, `account_id`, `category_id`, `amount`,
    `currency_code`, `description`, `purchase_date`, `installment_count` (1–48),
    `monthly_interest_rate` (no nula, 0–10), `status` y las fechas de auditoría. Sus `CHECK`, su
    trigger de `updated_at`, sus comentarios y un índice por usuario de las activas.
  - `finance.transactions` gana `installment_purchase_id` (FK diferida), `installment_number` e
    `installment_principal`, con un `CHECK` de los tres o ninguno, número ≥ 1 y capital > 0, y un
    índice parcial por compra y fecha.
  - Función `finance.committed_credit(p_account_id)`: el capital de las cuotas confirmadas de la cuenta
    con `occurred_at > now()`.

  Emitirlo en `docs/database/update/20261009_05_compras_en_cuotas.sql`, con encabezado (va ANTES de
  desplegar la app; en local hay que recargar los datos) y la reversión comentada. Documentar en
  `docs/database/modelo-datos.md` las compras en cuotas, el capital de cada cuota y el cupo
  comprometido. Aplicarlo a la base local y correr la comparación de esquemas: tiene que salir limpia.
- [x] 1.2 `docs/database/test-data.sql`: usuario nuevo `cuotas@financeapp.local` (lo borra y lo recrea
  el script) con `Visa` (`CREDIT`, cupo 5000000, corte 20, pago 5, tasa 2), `Visa 31` (`CREDIT`, cupo
  2000000, corte 31, pago 31, sin tasa), `Visa sin corte` (`CREDIT`, cupo 1000000, sin corte, pago 5) y
  `Ahorros` (`SAVINGS`), todas en COP con saldo 0. `docs/database/demo-data.sql`: una compra a 6 cuotas
  hecha hace dos meses en la tarjeta de los usuarios de demo, con sus cuotas. Recargar con
  `cargar-datos-local.sql`.
- [x] 1.3 `installment` en las listas exactas de claves de `transactions/lote-de-uno.yml`,
  `pending/listar-pendientes.yml` y `reports/reporte-mes.yml`, con `null` en los movimientos del
  escenario. `transactions/`: un elemento del lote con `installment` en el cuerpo sale con `null`.
- [x] 1.4 Carpeta nueva `bruno/installments/` con bearer de carpeta y el usuario `cuotas@`. Las fechas
  las calculan los scripts, en la zona de Bogotá. Requests, cada uno con sus aserciones:
  - Login, ids de las cuatro cuentas y de una categoría de gasto, otra de gasto y una de ingreso; id de
    una tarjeta de `prueba@` para el caso ajeno.
  - Simulación: 1200000 a 3 en `Visa` → 200 con 424000, 416000, 408000, `totalInterest` 48000 y las
    fechas; 1000000 a 3 → capitales 333333, 333333, 333334 e intereses 21500, 14333, 7167; compras del
    9, el 20 y el 21 del mes pasado → primera cuota el 5 del mes en curso, el 5 del mes en curso y el 5
    del siguiente; `GET /api/accounts` después → `Visa` sin cambios.
  - Alta: 1200000 a 3 en `Visa` hoy → 201 con los campos, `installments` y `transactionId`;
    `GET /api/accounts` → `currentBalance` 0 y `availableCredit` 3800000; `reports/balance` → el mismo
    `availableCredit`; `reports/transactions` → las 3 cuotas programadas con su `installment`.
  - A una cuota → interés 0. En `Visa 31` (sin tasa) a 4 desde el 15 de diciembre del año pasado →
    31-ene, 28/29-feb, 31-mar, 30-abr, `monthlyInterestRate` 0, todas pagadas. En `Visa` desde hace
    cuatro meses a 6 → las vencidas bajan el saldo y `paidCount` las cuenta.
  - Errores (400 con su campo): `Ahorros`, `Visa sin corte`, tarjeta ajena (`La cuenta no existe`),
    categoría de ingreso, `installmentCount` 0 y 49, monto 10 a 12, `purchaseDate` de mañana, sin
    `amount` con `installmentCount` 0 juntos; la simulación con `installmentCount` 0.
  - Listado: la compra de hoy y la de hace cuatro meses, ordenadas por la próxima cuota, con
    `paidCount`, `remainingPrincipal`, `remainingAmount` y `nextInstallment`; no trae la de `Visa 31`
    ni las de otro usuario.
  - A mano: `PATCH /api/transactions/{id}` del monto de la cuota 2 → mismo `installment`; el listado
    refleja el monto nuevo en `remainingAmount`. Borrar la última cuota de la compra de hace cuatro
    meses → `paidCount` sube y el cupo sube su capital.
  - Edición: `FUTURE` con descripción (las futuras cambian, las pasadas no); `ALL` con categoría (todas
    cambian); saldos sin cambios; sin `scope`, solo `scope`, categoría de ingreso → 400 con su campo;
    compra ajena → 404; id mal formado → 400.
  - Cancelación: 204; las pasadas siguen en el reporte con su `installment`, las futuras no; el cupo
    sube el capital pendiente; fuera del listado; otra vez → 404.
  - 401: alta y edición sin credencial; simulación, listado y cancelación con el Basic, con
    `UNAUTHENTICATED` en `authorization`.

  Verificar que los requests nuevos fallan hoy con `verificar-bruno.ps1 -RecargarDatos`, y que el resto
  de la colección sigue en verde.
- [x] 1.5 `bruno-personal/`: carpeta `cuotas/` con simulación, alta, listado, edición y cancelación
  (ruta, cuerpo y bearer iguales a `bruno/installments/`). En `movimientos/registrar.yml`, la nota de
  que `installment` se ignora. No se ejecutan.

## 2. Dominio

Skills: `java-architect`.

- [x] 2.1 `InstallmentPlanTest` en RED: fechas antes, en y después del corte; pago posterior al corte en
  el mismo mes; días 31 en meses cortos con el día anclado; medianoche en la zona dada; capital que
  suma el total con el resto en la última; interés sobre el pendiente con redondeo mitad hacia arriba;
  sin interés con 1 cuota, tasa 0 o tasa nula; cuántas vencieron hasta hoy. `InstallmentPlan` y
  `InstallmentPlan.Cuota`. Verificar con la clase en verde.
- [x] 2.2 `RecurrenceScope` pasa a `GroupScope`. Verificar con la suite en verde.
- [x] 2.3 `Transaction` y `ReportedTransaction` ganan `InstallmentRef installment` (`purchaseId`,
  `number`, `count`, `principal`). `Account` gana el cupo comprometido y `availableCredit` lo resta
  (`AccountTest`). Ajustar los Object Mother y los usos. Verificar con la suite en verde.

## 3. Aplicación

Skills: `java-architect`, `java-exceptions`.

- [x] 3.1 `PreviewInstallmentPurchaseUseCaseTest` y `CreateInstallmentPurchaseUseCaseTest` en RED:
  valida cuenta y categoría con `ReferenciasDelUsuario`; la cuenta tiene que ser `CREDIT` con corte y
  pago (error en `accountId`); `purchaseDate` futura en la zona del perfil (error en `purchaseDate`); el
  alta guarda la compra con la tasa copiada y sus cuotas con el `Clock` y la zona, y responde con el
  plan; la simulación no guarda. Puertos `PreviewInstallmentPurchasePort`,
  `CreateInstallmentPurchasePort` e `InstallmentPurchaseRepositoryPort`. Verificar con las dos clases
  en verde.
- [x] 3.2 `ListInstallmentPurchasesUseCaseTest`, `UpdateInstallmentPurchaseUseCaseTest` y
  `CancelInstallmentPurchaseUseCaseTest` en RED: el listado devuelve lo que da el puerto; la edición
  valida la categoría, aplica el alcance y responde 404 si no hay compra activa; la cancelación
  responde 404 si no había compra que cancelar. Sus puertos de entrada. Verificar con las tres clases
  en verde.
- [x] 3.3 `UpdateTransactionUseCase`, `ApprovePendingTransactionUseCase` y `TransactionBatchValidator`
  pasan `installment`: el lote siempre lo deja en `null` y la edición conserva el guardado. Verificar con
  sus tests en verde.

## 4. Web

Skills: `java-architect`, `java-security`, `java-exceptions`.

- [x] 4.1 `CreateInstallmentPurchaseRequestTest` y `UpdateInstallmentPurchaseRequestTest` en RED: cada
  regla de formato de la tabla de `design.md` con su campo (monto menor que las cuotas incluido) y
  `sinCambios()`. Los records y, si hace falta, una constraint de clase en `dto/validation/`.
  Verificar con las dos clases en verde.
- [x] 4.2 `InstallmentPurchaseControllerTest` en RED: 200 de la simulación, 201 del alta con
  `installments`, 200 del listado y de la edición sin `installments`, 204; id mal formado → 400 en
  `id`; parche sin campos → 400 en `body`; sin `scope` → 400 en `scope`. `InstallmentPurchaseController`
  y sus respuestas. Verificar con la clase en verde.
- [x] 4.3 `TransactionControllerTest` y `TransactionReportControllerTest` en RED: `installment` en la
  respuesta (null y con valor, sin `principal`). `TransactionResponse` y el ítem de
  `TransactionReportResponse` lo devuelven. Verificar con las dos clases en verde.
- [x] 4.4 `InstallmentPurchasesIT`: las cinco rutas sin credencial y con el Basic dan 401 con
  `UNAUTHENTICATED` en `authorization` y `WWW-Authenticate: Bearer`. Verificar con la clase en verde.

## 5. Persistencia

- [x] 5.1 `InstallmentPurchaseR2dbcAdapter`: guardar la compra con sus cuotas, listar las activas con su
  resumen, buscar una no cancelada del usuario, editar con alcance y cancelar, cada escritura en una
  transacción. `AccountR2dbcAdapter` lee `finance.committed_credit`. `TransactionR2dbcAdapter` y
  `TransactionReportR2dbcAdapter` leen la cuota con `LEFT JOIN` a la compra, y el `INSERT` la escribe;
  el `UPDATE` de un movimiento no la toca. Todo con parámetros enlazados. Sin test de suite: lo
  verifican en verde los requests del grupo 1.

## 6. Contrato

- [x] 6.1 `docs/api/contrato-api.md`: sección de compras en cuotas con las cinco rutas, los cuerpos, las
  respuestas, la regla de fechas, el capital y el interés, el cupo, el alcance y los errores;
  `installment` en la respuesta de un movimiento y en el ítem de `reports/transactions`; el
  `availableCredit` con cuotas. Verificar leyendo las secciones.

## 7. Verificación

- [x] 7.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea reales.
- [x] 7.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  los conteos reales de requests, tests y aserciones.
