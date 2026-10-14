# Design

## Context

El porqué está en `proposal.md`. Lo que ya existe y condiciona el diseño:

- **`expense_shares` (FIN-16)** une a dos usuarios (`payer_id`, `debtor_id`), no a una afiliación.
  Guarda el monto y la moneda del gasto, una copia de la descripción y la fecha, el estado y
  `resolved_at`. Tiene índices por `(payer_id, status)` y `(debtor_id, status)`, pensados para esta
  suma (su `design.md`, decisión 4).
- **El API no expone ids de usuario (FIN-65, FIN-66).** La otra persona se nombra con el `id` de una
  afiliación, y se muestra como `{email, firstName, lastName}`.
- **Entre dos personas puede haber varias afiliaciones a lo largo del tiempo**: cada solicitud es
  una fila, y tras desvincularse pueden afiliarse otra vez. Las partes no dicen con qué afiliación
  se crearon.
- **Las partes solo nacen con una afiliación `ACCEPTED`** (FIN-66), así que toda pareja con partes
  tiene al menos una afiliación `ACCEPTED` o `UNLINKED`. Las series y las compras en cuotas no se
  comparten, así que esta lectura no tiene que poner al día las series sin fin, como sí hacen las
  lecturas de saldos de cuentas.

## Goals / Non-Goals

**Goals:**

- Que el saldo de todas las personas salga de una sola consulta, sin una por persona.
- Que el mismo dato salga igual visto por los dos: lo que A ve como `owedToMe` con B es el `iOwe` de
  B con A.

**Non-Goals:**

- Cambiar `GET /api/affiliations` o la vista de una afiliación de FIN-65.
- Cambios de esquema o índices nuevos.

## Decisions

### 1. Rutas propias bajo `/api/affiliations`

`GET /api/affiliations/balances` para el saldo y `GET /api/affiliations/{id}/shares` para el detalle.

- **Descartado: agregar `balances` a cada elemento de `GET /api/affiliations`.** Ese listado trae las
  relaciones abiertas, incluidas las solicitudes, y no trae las desvinculadas. La persona desvinculada
  con deuda tendría que salir ahí con otras reglas, y una solicitud enviada sin aceptar no puede
  mostrar el nombre de la otra persona (FIN-65): mezclar los dos listados obliga a cada elemento a
  decir cuál de las dos reglas aplica.
- **Descartado: un recurso aparte (`/api/shares/balances`).** El front lo muestra en la pantalla de
  afiliados (decisión del epic), y la persona se nombra por su afiliación: la ruta va donde vive ese
  id.

`balances` no choca con `/{id}`: no hay `GET /api/affiliations/{id}`, y las acciones son `POST`.

### 2. La persona se nombra por una afiliación que la representa

Cada entrada del saldo trae el `affiliationId` de la afiliación `ACCEPTED` con esa persona, o, si no
hay, el de la `UNLINKED` más reciente (por `updated_at`, que es cuando se desvinculó). El detalle
acepta cualquier afiliación `ACCEPTED` o `UNLINKED` del usuario, y lista **todas** las partes entre los
dos usuarios, sin importar con qué afiliación se crearon.

- **Descartado: el id de usuario.** Rompe la regla de FIN-65 y FIN-66 de no exponerlo.
- **Descartado: el correo en la ruta.** Puede cambiar, hay que normalizarlo, y viaja en la URL y en
  los logs de acceso.
- **Descartado: limitar el detalle a las partes creadas mientras duró esa afiliación.** Las partes no
  guardan la afiliación, y para el usuario la deuda es con la persona, no con una de sus solicitudes.

Una afiliación `REQUESTED`, `REJECTED` o `CANCELLED` no sirve para el detalle: 409 `INVALID_STATE` en
`status`, como cualquier acción de afiliaciones que no corresponde al estado. Una `REQUESTED` enviada
a un correo sin cuenta no tiene a nadie del otro lado, y responder distinto según el correo tenga
cuenta revelaría cuáles la tienen.

### 3. Saldo por moneda, con `owedToMe`, `iOwe` y `net`

Por moneda, y no un número total: el epic define la deuda como la suma de las partes, y cada parte
está en la moneda en que se registró el gasto. Convertir exigiría elegir una tasa y una fecha, y el
número cambiaría solo con la tasa. Los tres montos, y no solo `net`, porque el front muestra «te
debe» y «le debes», y con deudas en los dos sentidos `net` sola esconde las dos.

Una persona `ACCEPTED` sin partes pendientes sale con `balances: []`: es «a paz y salvo», que FIN-72
muestra. Una `UNLINKED` sin partes pendientes no sale: ya no hay nada que hacer con ella.

### 4. `direction` con el vocabulario del saldo

Cada parte del detalle trae `direction`: `OWED_TO_ME` si el usuario es quien pagó, `I_OWE` si es quien
debe. Es el mismo par de palabras que `owedToMe` / `iOwe` del saldo, para que el front no traduzca
entre dos vocabularios. Se mantiene aunque la parte ya no sea deuda: una `PAID` con `OWED_TO_ME` es
«me pagó».

- **Descartado: `person` en cada parte.** Es siempre la misma persona, la de la afiliación.
- **Descartado: el resumen del saldo junto con el detalle.** El front ya lo tiene del listado; el
  detalle queda como lista, igual que los demás listados del API.

### 5. Una consulta para el saldo, en el adapter

El adapter (`AffiliateBalanceR2dbcAdapter`, sin colisión con los nombres de `ia`) resuelve el saldo en
una sola sentencia:

1. Las afiliaciones `ACCEPTED` o `UNLINKED` del usuario, con el id de la otra persona, y una por
   persona con `DISTINCT ON`: primero la `ACCEPTED`, después la de `updated_at` más reciente.
2. Las partes `PENDING` en las que el usuario es `payer_id` o `debtor_id`, agrupadas por la otra
   persona y la moneda, con `SUM ... FILTER` para cada sentido.
3. El cruce de las dos, quedándose con las `ACCEPTED` y con las `UNLINKED` que tengan alguna suma.

Sale una fila por persona y moneda, y el adapter las agrupa en orden. El detalle son dos lecturas: la
afiliación (`findForParticipant`, que ya existe, para el 404 y el 409 del caso de uso) y las partes
entre los dos usuarios.

La regla de qué cuenta como deuda vive en el SQL, como los demás saldos del proyecto, y por eso la
verifica `bruno/`, no la suite. El caso de uso del detalle sí decide algo: el 404, el 409 y quién es
la otra persona. Eso tiene tests unitarios.

- **Descartado: traer las partes y sumar en Java.** Recorre todas las partes del usuario en cada
  pedido del saldo, cuando el índice por `(payer_id, status)` deja sumar solo las pendientes.

## Risks / Trade-offs

- **Que se pierda la regla de FIN-65 sobre el nombre.** El saldo muestra el nombre actual de la otra
  persona, también en una `UNLINKED` que el usuario había enviado. Es correcto: fue aceptada, y la
  regla solo esconde el nombre de las enviadas que nunca se aceptaron. → La consulta lee el nombre de
  `users`, no la vista de FIN-65, que lo escondería para una enviada en cualquier estado que no sea
  `ACCEPTED`.
- **Una persona desactivada sigue saliendo**, con su nombre, si hay afiliación o deuda. → Es deuda
  real entre los dos; desactivar no la cancela.
- **Sin paginación en el detalle.** Entre dos personas el volumen es bajo. → Si crece, una tarea
  nueva.
