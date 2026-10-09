# Design

## Context

Los campos de crédito (`creditLimit`, `statementDay`, `paymentDueDay`) siguen un patrón que ya está
asentado. Son opcionales en una `CREDIT` y están prohibidos en las demás cuentas. En el alta los valida
`CamposDeCredito`, una constraint de clase, porque el tipo viene en el cuerpo. En el parche, el
formato va en anotaciones de `UpdateAccountRequest`, y la regla "solo en `CREDIT`" la aplica
`UpdateAccountUseCase` contra el tipo guardado. En la base, `ck_accounts_credit_fields` los obliga a
ir en `null` fuera de una `CREDIT`. La tasa entra por el mismo camino. Las decisiones de abajo son
las del contrato, que la tarea pedía tomar.

## Goals / Non-Goals

**Goals:**
- Un contrato de la tasa que FA-108 pueda usar sin convertir unidades y que el front pueda mostrar tal
  como llega.
- La tasa se valida igual que los otros campos de crédito, sin un camino nuevo.

**Non-Goals:**
- Calcular interés: es FA-108.
- Tasa anual, conversiones o varias tasas por tarjeta.

## Decisions

### Nombre: `monthlyInterestRate`

El nombre dice la periodicidad. Con `interestRate` a secas, quien lo lea tendría que adivinar si la
tasa es anual o mensual, y en Colombia los bancos publican las dos. Se descartó `interestRate`, por
ambiguo, y `monthlyRate`, porque no dice qué es lo que tiene tasa.

### Unidad: porcentaje mensual, no fracción

`2.15` es el 2,15 % mensual, como aparece en el extracto y como lo escribe el usuario. Se descartó la
fracción (`0.0215`): obliga al front a multiplicar al mostrar y a dividir al guardar, y un 2.15
enviado por error sería un 215 %. Quien calcule interés (FA-108) divide entre 100 en un solo lugar.

### Rango: de 0 a 10, ambos incluidos

- **0 se admite**: es una tarjeta sin interés. Rechazarlo obligaría a dejar la tasa en `null`, que
  significa otra cosa: "no la he cargado".
- **10 como tope**: la tasa de usura en Colombia ha estado en los últimos años entre el 25 y el 40 %
  efectivo anual, es decir, entre 2 y 3 % mensual, así que 10 % mensual deja margen de sobra a cualquier
  tasa real. El tope sirve para atrapar
  el error más probable: escribir la tasa anual del extracto (28,5) en un campo mensual. Con un tope
  de 100, ese error pasaría en silencio y las cuotas saldrían con un interés diez veces mayor.
- Se descartó no poner tope (deja pasar ese error) y un tope de 5: si alguna vez se registra una
  tarjeta de otro país con tasa alta, ese margen se quedaría corto antes de tiempo.

### Precisión: hasta 4 decimales, columna `NUMERIC(6,4)`

Cuatro decimales de porcentaje bastan para cualquier tasa publicada (los extractos traen 2 o 4). Igual
que en los montos, los ceros a la derecha no cuentan: `1.50000` se admite. `NUMERIC(6,4)` llega hasta
99,9999, lo que cubre el rango; el `CHECK` de la base lo acota a 0–10 por si alguien escribe con psql.

### Opcional y sin vaciar por el parche

Como el cupo: una tarjeta puede no tenerla, y las tarjetas que ya existen quedan en `null` sin
migración de datos. En el parche, `null` sigue significando "no cambia", así que no hay forma de
volver a dejarla sin tasa. Se aceptó por coherencia con los otros campos; si FA-108 lo necesitara,
sería una tarea aparte para los cuatro campos.

### Dónde se valida

- **Alta**: en `CamposDeCredito`, con la tasa sumada a `enRango` y a `ausentes`. Así una `DEBIT` con
  una tasa de 50 da un solo error en `monthlyInterestRate` ("solo una cuenta CREDIT"), no dos.
- **Parche**: una constraint de campo nueva, `@TasaMensual`, en `UpdateAccountRequest`. La regla de
  rango y decimales vive en una clase `Tasas` (junto a `Montos`, en `dto/validation/`), que usan las
  dos constraints. Se descartó `@DecimalMin`/`@DecimalMax` más `@Digits`, porque `@Digits` cuenta los
  ceros a la derecha y rechazaría `1.50000`; es la misma razón por la que existe `MontoNumeric`.
- **Tipo en el parche**: `UpdateAccountUseCase.camposDeCreditoSegunElTipo` suma el cuarto campo.

## Risks / Trade-offs

- **El tope de 10 rechaza una tasa real si alguna vez existe** → la constante está en un solo lugar
  (`Tasas`) y en el `CHECK`; subirla son dos líneas y un update.
- **La app nueva lee una columna que no existe en la base vieja** → el update se aplica en Neon antes
  de desplegar, y su encabezado lo dice. La app vieja con la base nueva sigue funcionando: la columna
  admite `null` y la app vieja no la nombra.
- **Recargar los datos locales** → el update no borra filas, pero `test-data.sql` y `demo-data.sql`
  cambian. Hay que recargar con `cargar-datos-local.sql`, y el PR lo avisa para el front.
