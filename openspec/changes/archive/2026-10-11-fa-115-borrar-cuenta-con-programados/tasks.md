# Tasks

## 1. Bruno en RED

Skills: `bruno-cli`.

- [x] 1.1 Al final de `bruno/accounts/` (seq 96 en adelante), con `tokenCuentas`, crear estos
  requests en orden:
  - `crear-cuenta-sobre`: `CASH` `Sobre` en COP, sin saldo inicial. Responde 201 y guarda `idSobre`.
  - `programado-en-sobre`: un gasto de 1000 en `Sobre` con `occurredAt` `{{mananaCuentas}}T12:00:00-05:00`.
    Responde 201 y guarda `idProgramadoSobre`.
  - `borrar-cuenta-con-programado`: `DELETE` de `Sobre`. Responde 409 con un solo error,
    `RESOURCE_IN_USE` en `id`, cuya `description` dice que tiene 1 movimiento programado.
  - `quitar-programado-sobre`: `DELETE /api/transactions/{{idProgramadoSobre}}`. Responde 204.
  - `transferencia-programada-hacia-sobre`: una transferencia de 1000 desde `idCuentaEfectivo`
    (`Bolsillo`) hacia `Sobre`, con fecha de mañana. Responde 201 y guarda `idTransferenciaSobre`.
  - `borrar-cuenta-destino-programada`: `DELETE` de `Sobre`. Responde 409 con `RESOURCE_IN_USE` en
    `id`.
  - `quitar-transferencia-sobre`: `DELETE` de la transferencia. Responde 204.
  - `borrar-cuenta-sin-programados`: `DELETE` de `Sobre`. Responde 204.
- [x] 1.2 Al final de `bruno/installments/` (seq 62 y 63):
  - `borrar-tarjeta-con-cuotas`: `DELETE /api/accounts/{{idVisaUnaCuota}}`. Responde 409 con un solo
    error, `RESOURCE_IN_USE` en `id`.
  - `compra-tras-borrado-rechazado`: `GET /api/installment-purchases` sigue trayendo
    `idCompraUnaConInteres`.
- [x] 1.3 Al final de `bruno/recurrences/` (seq 66): `borrar-cuenta-con-serie`.
  `DELETE /api/accounts/{{idCuentaSeries}}` responde 409, y entre sus errores hay uno
  `RESOURCE_IN_USE` en `id`.

  Verificar con `verificar-bruno.ps1 -RecargarDatos` que los requests nuevos fallan hoy. Los
  requests de borrado con saldo, con pendientes, ajeno, inexistente y los 401 siguen como están:
  documentan comportamiento que no cambia.

## 2. Puerto y caso de uso

Skills: `java-architect`, `java-exceptions`.

- [x] 2.1 Agregar a `AccountRepositoryPort` el método `countScheduled(UUID accountId)`, que devuelve
  `Mono<Long>`. Ajustar los mocks que haga falta. Verificar con `.\gradlew.bat test` en verde,
  todavía sin comportamiento nuevo.
- [x] 2.2 `DeleteAccountUseCaseTest` en RED:
  - una cuenta en cero con 1 programado da 409 `RESOURCE_IN_USE` en `id`, con «1» en la
    `description`, sin llamar a `softDelete`;
  - con saldo y programados da los dos errores, en `currentBalance` y en `id`;
  - con saldo y sin programados da solo el de `currentBalance`, como hoy;
  - en cero y sin programados borra, como hoy.

  Implementar en `DeleteAccountUseCase`. Verificar con la clase en verde.

## 3. Persistencia

- [x] 3.1 Implementar `countScheduled` en `AccountR2dbcAdapter`, con bind variables: `count(*)` de
  `finance.transactions` con `status = 'CONFIRMED'`, `occurred_at > now()` y la cuenta como
  `account_id` o `destination_account_id`. No lleva test de suite, porque el adapter está excluido
  de la cobertura: lo verifican en verde los requests del grupo 1.

## 4. Contrato y colección personal

Skills: `bruno-cli`.

- [x] 4.1 En `docs/api/contrato-api.md`, sección `DELETE /api/accounts/{id}`, documentar:
  - la regla de los programados;
  - qué hacer con cada origen: serie, compra en cuotas o movimiento suelto;
  - los dos errores juntos;
  - la fila nueva en la tabla de errores.

  Verificar leyendo la sección.
- [x] 4.2 En `bruno-personal/cuentas/borrar.yml`, agregar a `docs` el 409 por programados y cómo
  resolverlo. La ruta, el cuerpo y la autenticación no cambian: comparar con
  `bruno/accounts/borrar-cuenta.yml`. No se ejecuta.

## 5. Verificación

- [x] 5.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea reales.
- [x] 5.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  los conteos reales de requests, tests y aserciones.
