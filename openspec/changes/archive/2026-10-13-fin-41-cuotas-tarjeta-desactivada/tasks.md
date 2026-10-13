# Tasks

## 1. Escenario sembrado: una cuota que vence con la tarjeta desactivada

Skills: `bruno-cli`.

- [x] 1.1 En `bruno/installments/`, al final de la carpeta, escribir los requests que leen la tarjeta
  sembrada y verlos fallar con la base actual:
  - `GET /api/accounts?includeInactive=true`: `Visa guardada` con `isActive` en `false`,
    `currentBalance` -100000 y `availableCredit` 700000. Sus ids fijos (`idVisaGuardada`,
    `idCompraGuardada`) los fija `login.yml`, como los demás sembrados;
  - `GET /api/installment-purchases`: la compra `Bicicleta` con `paidCount` 1,
    `remainingPrincipal` 200000 y `remainingAmount` 200000.

  Se verifican con `bru run installments -r --env local` y fallan porque la tarjeta no existe.
- [x] 1.2 En `docs/database/test-data.sql`, bloque de `cuotas@`: sembrar `Visa guardada` (CREDIT,
  COP, cupo 1.000.000, corte 20, pago 5, sin tasa, `is_active` en `false`) y la compra `Bicicleta` de
  300000 a 3 cuotas sin interés. La cuota 1 vence ayer, y las cuotas 2 y 3 dentro de uno y de dos
  meses. Actualizar el comentario del bloque y el `docs` de `bruno/installments/folder.yml`. Se
  verifica recargando con `psql -U postgres -d financeapp -f docs/database/cargar-datos-local.sql`:
  los requests de 1.1 pasan, y `docs/database/check-saldos.sql` no lista ninguna cuenta. La compra
  sembrada es una compra activa más: `listar.yml`, que compara el conjunto exacto, la incluye.

## 2. Desactivar, editar, borrar y cancelar con compras activas

Skills: `bruno-cli`.

- [x] 2.1 Después de los requests de `Visa USD`, en `bruno/installments/`:
  - desactivar `Visa USD` con `{"isActive": false}`: 200, `isActive` en `false`, `currentBalance` 0
    y `availableCredit` 4900;
  - `GET /api/installment-purchases`: sigue la compra `idCompraUsd`;
  - `PATCH /api/installment-purchases/{idCompraUsd}` con `{"scope": "FUTURE", "description":
    "Audífonos viejos"}`: 200 con esa `description`;
  - `DELETE /api/accounts/{idVisaUsd}`: 409 con un solo error `RESOURCE_IN_USE` en `id`;
  - `DELETE /api/installment-purchases/{idCompraUsd}`: 204;
  - `GET /api/accounts?includeInactive=true`: `Visa USD` con `availableCredit` 5000.

  Se verifica con `bru run installments -r --env local`. Todos pasan sin código nuevo: si alguno
  falla, el código no hace lo que dice `design.md`, y se para a corregir el change.

## 3. Documentación

- [x] 3.1 `docs/api/contrato-api.md`:
  - en `PATCH /api/accounts/{id}`, junto al párrafo de FA-116: desactivar una tarjeta con compras
    activas no las toca, y sus cuotas siguen venciendo;
  - en `DELETE /api/installment-purchases/{id}`, o en la sección de la compra: la compra se edita y
    se cancela igual con la tarjeta desactivada.

  Se verifica leyendo que el texto coincida con los escenarios de las dos deltas.
- [x] 3.2 `bruno-personal/cuotas/` y `bruno-personal/cuentas/`: nota en su `docs` con la regla. No
  cambian rutas, cuerpos ni autenticación, así que no hay requests que replicar. No se ejecuta,
  porque apunta a Neon. Se verifica releyendo la nota.

## 4. Verificación

- [x] 4.1 `.\gradlew.bat build` en verde, con el conteo real de tests.
- [x] 4.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  los conteos reales de requests y asserts, y `check-saldos.sql` sin cuentas descuadradas.
