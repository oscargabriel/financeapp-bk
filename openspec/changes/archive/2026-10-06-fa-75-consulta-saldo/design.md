# Design

## Contexto

`GET /api/reports/transactions` (FA-63) ya calcula `totalsByType` en el dominio
(`TransactionReport.of`), sobre la misma lista que devuelve. Las cuentas con `currentBalance`,
`creditLimit` y `availableCredit` ya salen de `AccountQueryPort.findByUser` y de
`Account.availableCredit()`. Este change reutiliza los dos y solo agrega el agregado de ingresos y
egresos.

## Decisiones

### 1. La ruta es `GET /api/reports/balance`

Es una lectura agregada de los movimientos, como el reporte, y queda junto a él en
`TransactionReportController` (mismo `@RequestMapping("/reports")`, mismo parseo de fechas).

Descartadas:
- `GET /api/balance`: una raíz nueva para un solo endpoint, sin ganar nada a cambio.
- Agregar los netos a `GET /api/accounts`: mezcla el listado de un recurso con un cálculo por
  rango, y obligaría a ese endpoint a aceptar `from`/`to`.

### 2. Los netos salen de un agregado en SQL, no de la lista del reporte

Un puerto de salida nuevo devuelve ingresos y egresos del rango y del histórico en una sola
consulta, con `SUM(amount_base) FILTER (WHERE ...)`. El rango usa el mismo corte del reporte: los
límites se calculan con `u.timezone`, sin aplicar la zona a la columna.

Descartado: reutilizar `GetTransactionReportPort` y restar sus totales. Para el rango valdría, pero
el histórico no tiene rango, y traer todos los movimientos del usuario a memoria para sumarlos crece
sin límite con el uso.

### 3. El `net` del reporte se calcula en `TransactionReport`

Se deriva de `totalsByType`, que ya está sobre la lista devuelta, así que cuadra con ella por
construcción. Con un filtro de tipo que deja fuera `INCOME` o `EXPENSE`, el ausente vale cero.

Descartado: calcularlo en el DTO. Es una regla del reporte, no del formato de salida, y el asistente
de FA-77 va a consumir el modelo de dominio, no la respuesta HTTP.

### 4. Sin parámetros, el mes en curso según el `Clock` de la aplicación

`YearMonth.now(clock)`, como `GetMonthlySpendingUseCase`, en la zona `app.timezone`. El corte de
cada movimiento sí usa la zona del usuario, igual que el reporte. Hoy las dos son `America/Bogota`.
Si algún día difieren, el mes por defecto puede no coincidir con el mes local del usuario en las
horas del cambio de mes. Es aceptable: basta con mandar `from`/`to` explícitos.

Descartado: calcular el mes por defecto con la zona del usuario. Obliga a leer el usuario antes de
resolver el rango, y AGENTS.md pide el `Clock` para lo que dependa de «hoy».

### 5. `from` y `to` van los dos o ninguno

Con uno solo, la respuesta es 400 en el que falta.

Descartado: completar el faltante, como hace `MonthRange.resolve`. Aquí no hay un ancho natural
(en `monthly-spending` son doce meses), y adivinarlo daría netos que el usuario no pidió sin que se
note.

### 6. La comprobación del cupo es de solo lectura y contra la base local

```sql
SELECT a.name, a.credit_limit
  FROM finance.accounts a
 WHERE a.type = 'CREDIT' AND a.deleted_at IS NULL;
```

Se corre con psql contra la base local, con la conexión de `application-local.yaml`. Las tarjetas
sin cupo se reportan al usuario. Al implementar, el usuario descartó Neon: sus datos son de pruebas
suyas, no un uso real que validar.

## Riesgos

- **Los pendientes de FA-76.** Cuando exista el estado de aprobación, el agregado de la decisión 2 y
  la consulta del reporte tienen que excluir los pendientes. Queda dicho en FA-76, que es donde nace
  el estado.
