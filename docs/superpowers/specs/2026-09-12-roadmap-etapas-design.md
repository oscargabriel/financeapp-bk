# Roadmap por etapas de financeapp-bk

Fecha: 2026-09-12
Estado: aprobado. El backlog vive en Notion; este documento es el registro de la descomposición.

## Punto de partida

El modelo de datos va muy por delante del código. `docs/database/schema.sql` ya define
`currencies`, `exchange_rates`, `users` (con `telegram_chat_id` y `base_currency_code`), `accounts`,
`categories` + `default_categories`, `transactions` (con `origin`, `exchange_rate`, `amount_base`),
`budgets` y dos vistas de reporte. El API, en cambio, expone **un** endpoint de negocio
(`monthly-spending`) más `/api/status`.

Eso reordena el roadmap: la mayoría de las etapas son *exponer lo que el modelo ya soporta*, no
*diseñar el modelo*. En particular, el requisito de ver un gasto pasado convertido a la tasa **de
ese día** ya está resuelto por diseño — cada transacción congela su `exchange_rate` y guarda
`amount_base`, y los reportes suman esa columna.

Solo dos puntos del roadmap exigen tocar el esquema:

- **Finanzas compartidas** — no hay tablas.
- **Estado de aprobación en `transactions`** — no existe. Aparece por primera vez en la etapa de
  Telegram, no en la de correo: lo que entre por el bot también queda pendiente de aprobar.

## Decisiones

| # | Decisión | Alternativas descartadas |
|---|---|---|
| 1 | **Dos capas de prueba**: JUnit con mocks, y la integración desde Bruno | Tres capas conservando Testcontainers; Testcontainers solo donde hay SQL propio; decidir por etapa |
| 2 | **JWT básico primero, Google después** | JWT completo antes de los CRUD; CRUD primero y seguridad al final; un CRUD piloto primero |
| 3 | **Detalle solo en las etapas 4 a 8**; de la 9 a la 16, notas en la etapa y una tarea de refinamiento | Detalle completo en las 13 etapas; solo etapas sin tareas |
| 4 | **Una tarea, un resultado** — lo que no quepa en sus criterios se convierte en tarea nueva | Dejar que la tarea crezca durante la implementación |

### Consecuencias de la decisión 1

Se borra `MonthlySpendingR2dbcAdapterIT`, salen las dependencias de Testcontainers de
`build.gradle`, y `.\gradlew.bat test` **deja de exigir Docker**. Entra JaCoCo.

El costo, explícito: el SQL de las vistas y el corte de mes por zona horaria pierden cobertura
automática. Solo los verifica `bru run` contra la app y la base levantadas, así que ese paso deja de
ser opcional al cerrar una etapa.

`CLAUDE.md`, el skill `tareas-notion` y la memoria del proyecto afirman hoy que la suite exige
Docker. Siguen siendo ciertos hasta que la etapa 4 los cambie; se actualizan ahí, no antes.

### La regla de granularidad (decisión 4)

Una tarea tiene **un** resultado. Si durante la implementación aparece algo que sus criterios de
aceptación no cubren —parametrizar los mensajes del bot, un filtro de seguridad contra inyección,
cualquier pieza que sea un feature en sí misma— no se agranda la tarea: se crea una nueva en
`Backlog` con el contexto de por qué surgió, y se dice en el chat.

El discriminador: **¿puedo dejar esta tarea en verde y cerrada sin eso?** Si sí, es tarea aparte. Si
no —el código no compila, la suite no pasa, el endpoint queda roto— es parte de esta, y se dice
explícitamente al cerrar.

Efecto esperado: una etapa **crece** mientras se implementa. El número de tareas de una etapa lejana
es un punto de partida, no una estimación.

## Las etapas

| # | Etapa | Detalle hoy |
|---|---|---|
| 4 | Deuda de cobertura y entorno local | Tareas |
| 5 | Identidad y seguridad: registro, login y JWT | Tareas |
| 6 | CRUD de categorías | Tareas |
| 7 | CRUD de cuentas | Tareas |
| 8 | Transacciones: alta en lote y consulta | Tareas |
| 9 | Tasas de cambio automáticas | Notas |
| 10 | Perfil de usuario | Notas |
| 11 | Login con Google | Notas |
| 12 | Finanzas compartidas | Notas |
| 13 | Reinicio de contraseña por correo | Notas |
| 14 | Módulo de Telegram | Notas |
| 15 | Telegram: foto de factura | Notas |
| 16 | Ingesta desde correo con aprobación y deduplicación | Notas |

Notas de orden y alcance que no se deducen de los títulos:

- **El registro de usuario va en la etapa 5**, no en la 10. Un login sin alta de usuarios no sirve:
  hoy los usuarios se crean por SQL. El registro es además donde se copian las
  `default_categories` a `categories`.
- **La etapa 7 (CRUD de cuentas) no estaba en el roadmap dictado.** Se agrega porque sin cuentas no
  se puede crear una transacción desde el API y hoy solo existen por SQL. Es la primera candidata a
  eliminar si se prefiere seguir creándolas a mano.
- **La etapa 8 arranca solo en COP** (`exchange_rate = 1`, `amount_base = amount`), que es lo que el
  propio modelo de datos prescribe mientras no haya tasas cargadas. Por eso puede ir antes de la 9.
- **La etapa 12 está sin definir a propósito.** Se describieron dos modelos distintos: una cuenta
  bancaria que dos personas ven, y un gasto que se reparte generando una deuda entre personas
  (un consumo de 50.000 donde uno pone 40.000 y el otro queda debiendo 10.000). Elegir entre ellos
  —o soportar ambos— es el primer trabajo de esa etapa.
- **El estado de aprobación de `transactions` aparece en la etapa 14**, aunque su motivación
  completa esté en la 16.

## Fuera de alcance

Interfaz de usuario propia, aplicación móvil, despliegue y CI/CD, y cualquier análisis o predicción
sobre los gastos. El roadmap cubre API y módulo de Telegram.
