## Why

FA-123, surgida al cerrar FA-51 (10-10-2026). Desde FA-51 un movimiento suelto se registra en la
moneda de su cuenta, en cualquier moneda, pero las series (`/api/recurrences`) y las compras en
cuotas (`/api/installment-purchases`) siguen solo en COP: una cuenta en otra moneda da 400 en
`accountId` con «Por ahora las series solo admiten cuentas en COP» o «Por ahora las cuotas solo se
registran en tarjetas en COP». Quedaron fuera de FA-51 por dos motivos: el plan de cuotas redondea a
pesos enteros, y una serie en otra moneda necesita una regla para cambiar de cuenta en su PATCH.

## What Changes

- **Alta de una serie**: admite una cuenta propia en cualquier moneda activa. La serie toma la moneda
  de la cuenta (`currencyCode` de la respuesta) y sus ocurrencias quedan en esa moneda. Una cuenta
  cuya moneda ya no está activa en el catálogo es 400 en `accountId`.
- **PATCH de una serie**: la moneda de la serie no cambia. Una cuenta nueva tiene que estar en la
  moneda de la serie; si no, 400 en `accountId`. Es la misma regla del PATCH de un movimiento.
- **Alta y simulación de una compra en cuotas**: admiten una tarjeta en cualquier moneda activa.
  - El capital y el interés de cada cuota se redondean a los decimales de la moneda de la tarjeta
    (`decimal_places` del catálogo), con las mismas reglas de hoy: el capital hacia abajo con el resto
    en la última, el interés con la mitad hacia arriba. En COP, que tiene 0 decimales, nada cambia.
  - La regla de «al menos 1 peso de capital por cuota» pasa a ser **al menos la unidad mínima de la
    moneda de la tarjeta** por cuota: 1 en COP, 0.01 en USD. Una compra de 0.10 USD a 12 cuotas es
    400 en `amount`; una de 0.12 USD, no.
  - Una tarjeta cuya moneda ya no está activa es 400 en `accountId`: sin sus decimales no hay plan.
- Desaparecen los dos mensajes de «por ahora solo COP».

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `recurrentes`: el alta admite cualquier moneda activa; el PATCH exige la moneda de la serie al
  cambiar de cuenta.
- `cuotas`: el redondeo del capital y del interés, y el mínimo de capital por cuota, siguen los
  decimales de la moneda de la tarjeta; el alta admite cualquier moneda activa.

## Impact

- Aplicación: `ReferenciasDelUsuario` (sale la regla de COP, entra la de la moneda de la serie),
  `CreateRecurrenceUseCase`, `UpdateRecurrenceUseCase`, `PlanDeCompra`, y los casos de uso de alta y
  simulación de cuotas, que pasan a leer las monedas activas.
- Dominio: `InstallmentPlan` recibe la escala de la moneda en vez de fijar 0.
- Infraestructura: sale `@MontoParaLasCuotas` del request de la compra; la regla pasa al caso de uso.
- Sin cambios de esquema: `recurrences` e `installment_purchases` ya guardan `currency_code`.
- Bruno: `bruno/recurrences/` y `bruno/installments/` con cuentas en USD que crea la propia
  colección, replicado en `bruno-personal/`.
- Documentación: `docs/api/contrato-api.md` y `docs/database/modelo-datos.md`, que hoy dicen que las
  series y las cuotas solo admiten COP.
- El front deja de recibir el 400 de «solo COP»; la forma de las respuestas no cambia.

## Fuera de alcance

- Validar que el monto de una serie o de una compra tenga como mucho los decimales de su moneda (un
  `amount` de 100.5 en COP o de 10.123 en USD). Hoy tampoco se valida en los movimientos sueltos de
  FA-51; si se quiere, va a una tarea nueva para los tres a la vez.
- Convertir montos: una serie o una compra se registran en la moneda de su cuenta, sin `currencyCode`
  recibido ni conversión.
- Cambiar la tarjeta de una compra en cuotas: su PATCH solo admite `description` y `categoryId`.
- Los reportes y el resumen mensual: ya totalizan en la moneda de la persona desde FA-122.
