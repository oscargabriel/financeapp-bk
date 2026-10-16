# Design

## Context

`demo-data.sql` se corre una vez por usuario (`demo@` con `u = 0`, `dev@` con `u = 1`), borra al
usuario con todo lo suyo y lo recrea con ids fijos derivados de `u`. Las afiliaciones y las partes
caen en cascada al borrar a cualquiera de sus dos usuarios (`ON DELETE CASCADE` en
`affiliations` y `expense_shares`), y `transactions.shared_by_user_id` queda en `NULL`.

El API deja estas filas al compartir (`TransactionR2dbcAdapter`, `ApprovePendingTransactionUseCase`),
y la demo las copia:

| Momento | Filas |
|---|---|
| A comparte un gasto con B | Gasto `CONFIRMED` de A por el total, sin `group_id`. Una `expense_shares` `PENDING` (`payer_id` A, `debtor_id` B, la descripción y la fecha del gasto). Para B, un `EXPENSE` `PENDING` sin cuenta ni categoría, con la fecha del gasto, `shared_by_user_id` A, `share_id` y un `group_id` propio |
| B paga su parte | La parte `PAID` con `resolved_at`. El pendiente de B pasa a `CONFIRMED` con su cuenta y categoría, conserva su `group_id` y su fecha. Para A, un `INCOME` `PENDING` sin cuenta ni categoría, con la fecha de la aprobación, `shared_by_user_id` B, el mismo `share_id` y un `group_id` propio |
| A aprueba ese ingreso | El ingreso pasa a `CONFIRMED` con cuenta y categoría de ingreso de A |
| B rechaza su parte | La parte `REJECTED` con `resolved_at`; el pendiente de B se borra |
| A hace una transferencia compartida a B | `EXPENSE` `CONFIRMED` de A con categoría de gasto, sin partes. Para B, un `INCOME` `PENDING` sin cuenta ni categoría, `shared_by_user_id` A, sin `share_id`, con `group_id` propio |

Los saldos de las cuentas los mueve el trigger al insertar filas `CONFIRMED`, como en el resto de la
demo.

## Goals / Non-Goals

**Goals:** que `demo@` y `dev@` vean en el front afiliaciones en cada estado abierto, saldo con cada
persona en los dos sentidos, el detalle de las partes y pendientes compartidos que aprobar, con las
filas idénticas a las que dejaría el API.

**Non-Goals:** afiliaciones desvinculadas, partes `VOIDED`, partes en USD (FIN-96) y el volumen de
12 meses (FIN-97).

## Escenario

Fechas en días hacia atrás desde `now()`. L es el usuario de la demo. Todas las partes en COP.

Personas y relaciones:

| Persona | Correo (`demo@`) | Relación con L | Creada hace |
|---|---|---|---|
| Carlos Ruiz | `carlos@demo.financeapp.local` | L le envió la solicitud, `ACCEPTED` | 70 días |
| Valentina Torres | `valentina@demo.financeapp.local` | Ella se la envió a L, `ACCEPTED` | 60 días |
| Andrés Mejía | `andres@demo.financeapp.local` | Él se la envió a L, `REQUESTED` | 3 días |
| (sin cuenta) | `sofia@demo.financeapp.local` | L se la envió, `REQUESTED`, sin destinatario | 20 días |

Cada persona tiene una cuenta `DEBIT` en COP con saldo inicial suficiente para lo que paga, y la
copia de `default_categories`, como el registro.

Compartido:

| # | Hace | Pagó | Gasto | Partes |
|---|---|---|---|---|
| G5 | 50 d | Valentina | Arriendo de la cabaña, 900.000, Viajes | L 300.000 `PAID` hace 47 d: gasto de L confirmado en Nómina, Viajes; ingreso de Valentina confirmado en su cuenta, Reembolsos |
| G1 | 35 d | L | Asado en la finca, 240.000, tarjeta, Restaurantes | Carlos 80.000 `PAID` hace 30 d, y L ya aprobó el ingreso (Nómina, Reembolsos). Valentina 80.000 `PAID` hace 33 d, y el ingreso de L sigue `PENDING` |
| T1 | 18 d | L | Transferencia «Préstamo» de 100.000 a Carlos, Otros gastos | Ingreso de Carlos `PENDING` |
| G2 | 12 d | L | Mercado para el paseo, 180.000, tarjeta, Mercado | Carlos 60.000 `PENDING`. Valentina 60.000 `REJECTED` hace 10 d |
| G6 | 8 d | Valentina | Taxi al aeropuerto, 64.000, Transporte | L 32.000 `REJECTED` hace 7 d |
| G4 | 6 d | Carlos | Boletas del partido, 300.000, Entretenimiento | L 150.000 `PENDING` |
| G3 | 4 d | L | Pizza, 72.000, efectivo, Restaurantes | Valentina 36.000 `PENDING` |
| T2 | 2 d | Valentina | Transferencia «Mi parte del regalo» de 50.000 a L, Regalos | Ingreso de L `PENDING` |

Lo que el API debe responder a L con eso (la verificación del grupo 3 de `tasks.md`):

- `GET /api/affiliations/balances`: Valentina primero (afiliación más reciente) con
  `{COP, owedToMe 36000, iOwe 0, net 36000}`, y Carlos con `{COP, owedToMe 60000, iOwe 150000,
  net -90000}`. Andrés no sale.
- `GET /api/affiliations/{id}/shares` con Carlos: G4 `PENDING` `I_OWE`, G2 `PENDING` `OWED_TO_ME`,
  G1 `PAID` `OWED_TO_ME`. Con Valentina: G3 `PENDING`, G6 `REJECTED` `I_OWE`, G2 `REJECTED`
  `OWED_TO_ME`, G1 `PAID` `OWED_TO_ME`, G5 `PAID` `I_OWE`.
- `GET /api/transactions/pending`: tres compartidos (T2, el ingreso de G1 por Valentina, la parte de
  G4).
- `GET /api/affiliations`: Carlos y Valentina aceptadas, la de Andrés recibida y la de Sofía enviada.

## Decisions

### Correos en `<nombre>@<local de la demo>.financeapp.local`

`carlos@demo.financeapp.local` para `demo@` y `carlos@dev.financeapp.local` para `dev@`: el dominio
sale de la parte local del correo del usuario, así que cada carga tiene personas propias.

- `.local` es un dominio reservado: nadie real tiene ese correo, y no entra en
  `registration_allowlist`, así que nadie lo registra por el alta, tampoco en Neon.
- No coincide con `LIKE '%@financeapp.local'`: la comprobación de `docs/despliegue.md` que busca
  datos de `test-data.sql` en Neon sigue dando `0` sin cambiarla.

Descartados:

- `@front.local`: la recarga de la demo ya borra todo `@front.local`, pero en Neon ese dominio está
  admitido en el alta. Cualquiera con la Basic podría registrar antes `carlos@front.local`, y la
  recarga borraría a esa persona o fallaría al insertar.
- `@financeapp.local` con un sufijo (`carlos.demo@financeapp.local`): obliga a cambiar la
  comprobación de Neon, y se mezcla con los usuarios de `test-data.sql`, que usan ese dominio.

### Filas escritas por SQL, como el resto de la demo

Las partes y sus movimientos se insertan con SQL, imitando las filas de la tabla de *Context*. La
alternativa, crearlas llamando al API, exige la app levantada y un cliente HTTP, y psql no hace
HTTP; la demo nunca dependió de la app. El precio es que la demo copia reglas del API: si cambia cómo
el API escribe una parte, `demo-data.sql` se queda viejo sin que nada falle. La verificación del
grupo 3 lo compara una vez con lo que el API responde.

### Ids fijos donde hay enlaces, y fechas por días hacia atrás

Personas `4000000<u>-…-00000000000{2,3,4}`, sus cuentas `5000000<u>-…-00000000001{1,2,3}`,
afiliaciones `b000000<u>-…`, partes `c000000<u>-…` y los movimientos que una parte o un ingreso
enlazan `d000000<u>-…`: prefijos que no usan ni la demo ni `test-data.sql`. Los `group_id` y los
movimientos que nadie enlaza van con `gen_random_uuid()`, como el resto de la demo.

Las fechas van por días hacia atrás desde `now()`, no por mes y desfase como los movimientos fijos:
así todo queda en el pasado y los pendientes salen como recientes, sin depender del día del mes en
que se cargue.

### El borrado incluye a las personas

El `DELETE` del principio borra, por id y por correo, al usuario de la demo y a sus tres personas.
Las afiliaciones, las partes y los movimientos de todos caen en cascada, incluido lo que el front
haya aprobado o rechazado. Un correo de persona solo puede ser de esta carga, así que el borrado no
toca a nadie más, tampoco en Neon.

## Risks / Trade-offs

- **La demo copia las reglas de escritura del API.** Si FIN-66 cambia, la demo se queda vieja en
  silencio. Mitigación: la tabla de *Context* nombra las clases de donde salen, para compararlas.
- **Contraseña pública en Neon para tres usuarios más.** Decisión del epic del 15-10-2026: el mismo
  riesgo aceptado en FIN-43. Se anota en *La demo en Neon*.
- **Cambia el gasto mensual y los saldos de L.** Los gastos compartidos de L entran en sus reportes y
  metas. La demo no tiene montos fijados por ninguna spec, así que no rompe nada.
