# Spec Delta

## ADDED Requirements

### Requirement: Una serie no se comparte
`POST /api/recurrences` con `shares` no vacío SHALL responder 400 `VALIDATION_ERROR` en `shares` y
SHALL NOT crear la serie: compartir junto con «Repetir» está fuera de alcance del epic FIN-9.
`shares` ausente, `null` o `[]` SHALL ser una serie normal.

#### Scenario: Serie con partes
- **WHEN** el usuario envía una serie válida con `"shares": [{"affiliationId": "...", "amount": 10000}]`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `shares`, y no se crea la serie ni ninguna ocurrencia

#### Scenario: Serie con la lista vacía
- **WHEN** el usuario envía una serie válida con `"shares": []`
- **THEN** la respuesta es 201 con la serie
