# FA-114 — Poner al día el contrato del API

## Por qué

FA-114. Detectado el 09-10-2026 al revisar el contrato para las tareas de gastos recurrentes y
compras en cuotas: `docs/api/contrato-api.md` va atrasado frente al código en tres puntos, y el front
trabaja con una copia de ese documento.

1. No documenta `POST /api/assistant/messages` (FA-77).
2. Dice que `isActive` no se envía en `PATCH /api/accounts/{id}` y lo lista en «Lo que el API todavía
   no tiene», aunque FA-68 ya lo admite.
3. No incluye `origin` en la respuesta de un movimiento, aunque `TransactionResponse` lo devuelve.

El cuarto criterio pide contrastar el contrato con los controladores y DTOs vigentes. Esa revisión
encontró cuatro diferencias más, que entran en este change porque son del mismo documento y del mismo
tipo de atraso:

4. La sección de pendientes dice que hoy ningún endpoint los crea, y «Lo que el API todavía no tiene»
   dice que los creará el asistente. El asistente ya los crea.
5. En `reports/balance`, `availableCredit` se describe como `creditLimit + currentBalance`. Desde
   FA-108 también descuenta el capital de las cuotas por venir, igual que en `GET /accounts`.
6. *Formato* dice que los campos sin valor nunca se omiten. `installments` de una compra en cuotas se
   omite fuera del alta.
7. La tabla de errores no tiene el 502 `EXTERNAL_SERVICE_ERROR` del asistente.

Las rutas del índice, las formas de las demás respuestas y los límites de los cuerpos coinciden con
el código.

## Qué cambia

Solo `docs/api/contrato-api.md`:

- Una fila en el índice y una sección `POST /api/assistant/messages` con la autenticación, el cuerpo,
  los cinco `intent`, la respuesta de cada uno y los errores, tal como los implementan
  `AssistantController`, `AssistantMessageRequest`, `AssistantMessageResponse` y `AssistantUseCase`.
- `PATCH /api/accounts/{id}` admite `isActive`: desactivar y reactivar con cualquier saldo, sin tocar
  saldo ni movimientos. También se dice qué deja de poder hacerse con una cuenta desactivada, incluido
  el 409 `INVALID_STATE` al aprobar un pendiente con ella (spec `transacciones`), que tampoco estaba
  en el contrato. Sale de «todavía no tiene».
- `origin` en el ejemplo y en el texto de la respuesta de un movimiento, con sus valores: `WEB` para
  lo que entra por el API, también series y cuotas; `TELEGRAM` para lo que registra el asistente; e
  `IMPORT`, que hoy ningún endpoint produce.
- Los puntos 4 a 7 de arriba.

No cambia código, specs ni `bruno/`: `skip_specs: true`.

## Fuera de alcance

- Pasar los cambios a la copia del contrato del front (`financeapp-fr`). Se hace desde ese repo; el
  PR lo menciona para que se sepa.
- Documentar `INVALID_ARGUMENT` e `INVALID_NUMBER_FORMAT`. El handler los traduce, pero ningún endpoint
  actual deja llegar esas excepciones: los controladores parsean a mano. Documentarlos invitaría a
  manejar casos que no ocurren.
- La sección *CORS* sigue diciendo que el origen es `*` mientras no exista frontend. Eso depende de
  la configuración del servicio, no del código, y lo cubren FA-65 y FA-52.
