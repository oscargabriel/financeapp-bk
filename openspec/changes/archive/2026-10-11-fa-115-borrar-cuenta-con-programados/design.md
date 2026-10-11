# Design

## Context

- `DeleteAccountUseCase` lee la cuenta, exige `currentBalance` en cero y llama a `softDelete`.
- `softDelete` marca la cuenta y borra sus pendientes en una sola sentencia (FA-97).
- El saldo vigente ya descuenta los programados (`finance.scheduled_balance_delta`, FA-106). Por eso
  una cuenta en cero puede tener movimientos futuros.
- Tres orígenes producen programados:
  - los movimientos con fecha futura;
  - las cuotas por venir de una compra (FA-108);
  - la ocurrencia futura de una serie (FA-107): toda serie sin fin activa tiene exactamente una.

## Goals / Non-Goals

**Goals:** que ninguna cuenta borrada quede con movimientos que todavía no ocurren.

**Non-Goals:** desactivar cuentas con programados, y qué hace la puesta al día de las series con una
cuenta borrada. Los dos quedan en FA-116 y FA-117.

## Decisions

### Rechazar el borrado, no llevarse los programados

Decidido con el usuario el 11-10-2026.

**Se rechaza con 409.** El usuario resuelve antes cada programado con la herramienta de su recurso:
- `DELETE /api/recurrences/{id}` para una serie;
- `DELETE /api/installment-purchases/{id}` para una compra;
- `DELETE /api/transactions/{id}` para un movimiento suelto.

Es el mismo patrón de «solo se borra una cuenta en cero»: el sistema no decide por el usuario qué
pasa con su dinero.

**Descartado: borrar los programados con la cuenta, como los pendientes.**
- Un pendiente todavía no es un movimiento, pero un programado sí lo es, ya confirmado.
- Borrar las cuotas futuras de una compra las haría contar como pagadas en `paidCount`.
- Una serie sin fin seguiría creando ocurrencias sobre la cuenta borrada.
- Una transferencia programada desde otra cuenta le cambiaría el saldo futuro a esa otra cuenta sin
  que el usuario lo pida.

**Descartado: dejarlos y documentarlo.** Es el hueco que la tarea pide cerrar.

### Error en `id`, con los dos errores juntos

- El programado no es un campo de la cuenta. El error va en `id`, el recurso que no se puede borrar,
  y su `description` dice cuántos programados hay y cómo resolverlos.
- Si además hay saldo, la respuesta trae los dos errores. Así el usuario no descubre el segundo
  problema después de resolver el primero.
- No hay códigos nuevos: `RESOURCE_IN_USE` ya es el del saldo.

### La comprobación vive en el caso de uso

- `AccountRepositoryPort` gana un método que cuenta los movimientos `CONFIRMED` con
  `occurred_at > now()` que tienen la cuenta como origen o como destino.
- `DeleteAccountUseCase` lo consulta junto con la lectura de la cuenta, y solo llama a `softDelete`
  si el saldo es cero y no hay programados.

**Descartado: un `NOT EXISTS` dentro de la sentencia de borrado.** Con 0 filas afectadas no se
podría distinguir un 404 de un 409 sin otra lectura. Tampoco podría decir cuántos programados hay.

## Risks / Trade-offs

- **[Carrera]** Un programado creado entre la comprobación y el borrado se queda en la cuenta
  borrada. Es la misma ventana que ya tiene la comprobación del saldo. → Se acepta: un solo usuario
  rara vez hace las dos cosas a la vez, y el movimiento sigue visible en los reportes.
- **[Un programado que vence durante el borrado]** Si su fecha pasa entre la comprobación y el
  borrado, deja de ser programado y mueve el saldo. → Misma ventana, se acepta.
