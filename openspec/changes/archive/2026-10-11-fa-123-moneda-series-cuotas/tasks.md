## 1. Series en la moneda de su cuenta

Skills: `java-architect`, `java-exceptions`, `bruno-cli`.

- [x] 1.1 Escribir en `bruno/recurrences/`, al final de la carpeta, los requests de la delta, fallando
  contra la app de hoy: crear dos cuentas `SAVINGS` en USD del usuario `series@` (`Series USD` y
  `Series USD otra`); alta de una serie `MONTHLY` de 12.5 a 2 repeticiones en `Series USD` (201,
  `currencyCode` `USD`) y sus ocurrencias en `reports/transactions` (2, en `USD`); PATCH `ALL` a la
  cuenta `Series` en COP (400 en `accountId`, y la serie sigue en `Series USD`); PATCH `ALL` a
  `Series USD otra` (200, ocurrencias en la cuenta nueva). Actualizar el `docs` de `folder.yml`.
  Verificación: `bru run recurrences -r --env local` contra la app de `dev` muestra los nuevos en rojo.
- [x] 1.2 `ReferenciasDelUsuario`: quitar `cuentaEnCop` y `MONEDA_UNICA`; agregar la regla de una cuenta
  propia en una moneda dada, con «La cuenta tiene que estar en X, la moneda de la serie». Verificación:
  los tests de 1.3 y 1.4.
- [x] 1.3 `CreateRecurrenceUseCase`: la serie toma la moneda de la cuenta; leer `findActive()` y
  rechazar con 400 en `accountId` una cuenta cuya moneda no está activa (decisión 2). Borrar
  `SOLO_EN_COP`. Verificación: `CreateRecurrenceUseCaseTest` con una serie en USD cuyas ocurrencias
  quedan en USD, y una cuenta en una moneda inactiva; el test de hoy de «solo COP» se reemplaza.
- [x] 1.4 `UpdateRecurrenceUseCase`: una cuenta nueva tiene que estar en la moneda de la serie.
  Verificación: `UpdateRecurrenceUseCaseTest` con una serie en USD que recibe una cuenta en COP (400 en
  `accountId`, sin escribir) y otra en USD (se aplica).
- [x] 1.5 Replicar en `bruno-personal/recurrences/` los requests nuevos de 1.1 (ruta, cuerpo y
  autenticación), sin ejecutarlos.

## 2. Cuotas a los decimales de la moneda de la tarjeta

Skills: `java-architect`, `java-exceptions`, `bruno-cli`.

- [x] 2.1 Escribir en `bruno/installments/`, al final de la carpeta, los requests de la delta, fallando
  contra la app de hoy: crear `Visa USD` (`CREDIT`, USD, corte 20, pago 5, tasa 2) del usuario
  `cuotas@` y `Visa USD sin tasa` (sin tasa); simular 100 a 3 cuotas (capitales 33.33, 33.33 y 33.34,
  intereses 2.00, 1.33 y 0.67, `totalAmount` 104); simular 0.10 a 12 cuotas en `Visa USD` (400 en
  `amount`); simular 0.12 a 12 cuotas en `Visa USD sin tasa` (200, doce cuotas de 0.01); registrar 100
  a 3 cuotas en `Visa USD` (201, `currencyCode` `USD`) y leer sus cuotas en `reports/transactions`.
  `alta-monto-menor.yml` (10 COP a 12 cuotas) sigue en 400. Actualizar el `docs` de `folder.yml`.
  Verificación: `bru run installments -r --env local` contra la app de `dev` muestra los nuevos en rojo.
- [x] 2.2 `InstallmentPlan` recibe `decimals` y redondea capital e interés a esa escala (decisión 3).
  Verificación: `InstallmentPlanTest` con 100 a 3 cuotas en escala 2 y tasa 2, y los casos de COP de
  hoy sin cambios.
- [x] 2.3 `PlanDeCompra`: leer `findActive()`, rechazar una tarjeta en una moneda inactiva, pasar los
  `decimalPlaces` al plan y validar el mínimo de capital por cuota con la unidad mínima de la moneda
  (decisión 1). `tarjetaParaCuotas` deja de exigir COP. Verificación: `PreviewInstallmentPurchaseUseCaseTest`
  y `CreateInstallmentPurchaseUseCaseTest` con una tarjeta en USD (plan en centavos, compra y cuotas en
  USD), 0.10 a 12 cuotas en USD (400 en `amount`), 10 a 12 cuotas en COP (400 en `amount`) y una
  tarjeta en una moneda inactiva (400 en `accountId`).
- [x] 2.4 Borrar `@MontoParaLasCuotas` y su uso en `CreateInstallmentPurchaseRequest`, y mover sus
  casos de `CreateInstallmentPurchaseRequestTest` a los tests de 2.3. Verificación:
  `CreateInstallmentPurchaseRequestTest` e `InstallmentPurchaseControllerTest` en verde.
- [x] 2.5 Replicar en `bruno-personal/installments/` los requests nuevos de 2.1 (ruta, cuerpo y
  autenticación), sin ejecutarlos.

## 3. Documentación

- [x] 3.1 `docs/api/contrato-api.md`: quitar «por ahora solo COP» de series y cuotas (líneas 85, 821,
  1202 y 1346), describir el redondeo a los decimales de la moneda (1303-1306) y el mínimo de capital
  por cuota (1348). Verificación: `rg -n "solo admiten cuentas en COP|tarjetas en COP|al menos 1 peso de capital|en pesos enteros y" docs/api/contrato-api.md` sin resultados.
- [x] 3.2 `docs/database/modelo-datos.md` (línea 324): las series y las cuotas admiten cualquier moneda
  activa. Verificación: lectura del párrafo.

## 4. Verificación

- [x] 4.1 `.\gradlew.bat build` en verde, con el conteo real de tests y la cobertura por encima del 85 %.
- [x] 4.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con el
  conteo real de requests y `check-saldos.sql` sin cuentas descuadradas.
