# Spec Delta

## ADDED Requirements

### Requirement: Una compra en cuotas no se comparte
`POST /api/installment-purchases` y `POST /api/installment-purchases/preview` con `shares` no vacío
SHALL responder 400 `VALIDATION_ERROR` en `shares` y SHALL NOT crear ni simular la compra: compartir
junto con una compra en cuotas está fuera de alcance del epic FIN-9. `shares` ausente, `null` o `[]`
SHALL ser una compra normal.

#### Scenario: Compra con partes
- **WHEN** el usuario envía una compra válida con `"shares": [{"affiliationId": "...", "amount": 10000}]`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `shares`, y no se crea la compra ni ninguna cuota

#### Scenario: Simulación con partes
- **WHEN** el usuario envía la misma compra a `POST /api/installment-purchases/preview`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `shares`

#### Scenario: Compra con la lista vacía
- **WHEN** el usuario envía una compra válida con `"shares": []`
- **THEN** la respuesta es 201 con la compra
