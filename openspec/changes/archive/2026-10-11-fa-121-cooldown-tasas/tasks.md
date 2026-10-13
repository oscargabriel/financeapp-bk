# Tasks

## 1. Registro de consultas al proveedor

Skills: `java-architect`.

- [x] 1.1 Escribir `ConsultasAlProveedorTest` (`application/usecase`) con un `Clock` que el test
  pueda mover, fallando: sin intentos se puede consultar; tras un éxito no se puede el resto del
  día; al día siguiente sí; tras un fallo no se puede dentro del intervalo y sí al cumplirse; el
  cambio de día se mide en la zona del `Clock`
- [x] 1.2 Implementar `ConsultasAlProveedor` (`@Component`, `Clock` y
  `@Value("${tasas.reintento-tras-fallo}") Duration`, estado en `AtomicReference`) y verificar que
  `ConsultasAlProveedorTest` pasa
- [x] 1.3 Agregar `tasas.reintento-tras-fallo: ${TASAS_REINTENTO_TRAS_FALLO:1h}` a
  `src/main/resources/application.yaml`, con su comentario, y `tasas.reintento-tras-fallo: 1h` a
  `src/test/resources/application.yaml`; verificar con `ExchangeRatesIT` que el contexto arranca y
  con un caso en `CloudRunConfigTest` que la clave resuelve a `1h` sin la variable

## 2. El caso de uso respeta el registro

Skills: `java-architect`.

- [x] 2.1 En `ResolveExchangeRateUseCaseTest`, construir el caso de uso con un `ConsultasAlProveedor`
  real sobre el `RELOJ` fijo y agregar, fallando: tras un fallo del proveedor una segunda
  resolución no lo consulta y resuelve con la tasa anterior; tras una respuesta sin la moneda pedida
  una segunda resolución no lo consulta; `refreshToday` tras un fallo no lo consulta; un error al
  guardar las filas no bloquea la siguiente consulta
- [x] 2.2 Inyectar `ConsultasAlProveedor` en `ResolveExchangeRateUseCase`: `guardarDelProveedor` no
  llama al proveedor si el registro no lo permite, anota el éxito tras guardar las filas de la
  respuesta y el fallo cuando el proveedor da error; actualizar el javadoc de la clase. Verificar que
  `ResolveExchangeRateUseCaseTest` pasa completo
- [x] 2.3 Verificar con `ExchangeRatesIT` y `CreateTransactionsUseCaseTest` que el cableado y el alta
  no cambian

## 3. Verificación

- [x] 3.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura reales
- [x] 3.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  el conteo de requests y el chequeo de saldos reales: ningún request cambia, y `bruno/exchange-rates/`
  y las altas en otra moneda tienen que seguir pasando con el registro activo
