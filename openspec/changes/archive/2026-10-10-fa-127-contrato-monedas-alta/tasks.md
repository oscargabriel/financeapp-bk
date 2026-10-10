# Tasks

## 1. Contrato

- [x] 1.1 `docs/api/contrato-api.md`, sección `GET /api/catalogs/currencies`: el párrafo dice que el
  alta de movimientos admite cuentas en cualquier moneda activa, con la conversión a pendiente, y
  que las series y las compras en cuotas siguen solo en COP. Lleva enlaces a `POST /api/transactions`
  y a las secciones de series y de compras en cuotas, con anclas que existan en el documento.

## 2. Verificación

- [x] 2.1 `gradlew build` en verde, con el conteo de tests.
- [x] 2.2 `verificar-bruno.ps1 -RecargarDatos` en verde, con los conteos y el resultado de
  `check-saldos.sql`.
