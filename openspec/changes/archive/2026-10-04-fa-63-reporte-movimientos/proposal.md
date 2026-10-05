# Proposal

Origen: FA-63. El encargo llegó por chat el 04-10-2026: *"el reporte dado un rango de año mes día,
con un filtro opcional de categorías y otro filtro opcional de tipo de movimiento, con el ID del
usuario obligatorio para no mezclar resultados"*, con la nota de crear en `bruno-personal/` el
request con todos los parámetros y valores de ejemplo. Al registrarla se aclaró que el reporte
devuelve la lista de movimientos **y** los totales.

## Why

El único reporte que existe es el gasto mensual: agrupa por mes y solo mira egresos. No hay forma de
ver, para un rango de días cualquiera, qué movimientos hubo ni cuánto sumaron por tipo o por
categoría. Tampoco hay un endpoint que liste movimientos: después de FA-62, para modificar o borrar
uno hay que conocer su id, y hoy solo se obtiene en la respuesta del alta.

## What Changes

- `GET /api/reports/transactions?from=YYYY-MM-DD&to=YYYY-MM-DD`, con los filtros opcionales
  `categoryId` y `type`, cada uno con uno o varios valores.
- `from` y `to` son obligatorios e inclusivos. El día se corta en la zona horaria del usuario
  (`users.timezone`), igual que el gasto mensual corta el mes (ver design.md).
- La respuesta trae la moneda base del usuario, la lista de movimientos ordenada por `occurredAt`
  descendente, los totales por tipo y los totales por categoría, sumando `amount_base`.
- Un rango sin movimientos responde 200 con la lista vacía y los totales por tipo en cero.
- El usuario sale del JWT, como en el resto del API: ni la URL ni el query llevan `userId`. Un
  `categoryId` de otro usuario no da error: simplemente no encuentra movimientos.
- Parámetros inválidos → 400 `VALIDATION_ERROR` sobre el parámetro que corresponde.

No es un cambio incompatible: ningún endpoint existente cambia.

## Capabilities

### New Capabilities

- `reportes`: el reporte de movimientos por rango de días. El gasto mensual no tiene spec y este
  change no la escribe (las specs crecen con los cambios).

### Modified Capabilities

Ninguna.

## Fuera de alcance

- Paginación y tope del rango: un rango de varios años devuelve todo. Uso personal, volumen bajo.
- Totales por cuenta, saldo neto (ingresos menos egresos) o agrupaciones por día o semana.
- Exportar a CSV o PDF.
- Filtro por cuenta o por texto de la descripción.
- Escribir la spec del gasto mensual existente.

## Impact

- Dominio: un puerto de entrada, un puerto de salida, los records del reporte y el cálculo de los
  totales.
- Aplicación: el caso de uso que valida el rango y arma el reporte.
- Web: un controlador nuevo bajo `/reports` con los parámetros parseados a mano y sus DTOs de
  respuesta.
- Persistencia: un adapter R2DBC con la consulta filtrada por `user_id`, con bind variables.
- `bruno/reports/`: carpeta nueva con aserciones; `bruno-personal/reportes/movimientos.yml` con todos
  los parámetros y valores de ejemplo.
- `docs/api/contrato-api.md`: el endpoint y el índice.
- Sin cambios en la base de datos: `ix_transactions_user_date` ya cubre la consulta.
