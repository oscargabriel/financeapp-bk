# Tasks

## 1. Formato E.164 en el alta y el perfil

Skills: `java-architect` (DTO de `adapter/in/web/dto`), `bruno-cli`.

- [x] 1.1 RED en Bruno: request nuevo `bruno/users/alta-celular-sin-indicativo.yml` (`"phone": "3001234567"` → 400 `VALIDATION_ERROR` en `phone`) y `bruno/users/modificar-perfil-celular-sin-indicativo.yml` (parche `{"phone": "3109876543"}` → 400 en `phone`, y el `GET` siguiente conserva el celular). Quedan fallando contra la app sin cambiar; se verifica con `verificar-bruno.ps1 -RecargarDatos -Objetivo users`
- [x] 1.2 RED en JUnit: `RegisterUserRequestTest` (`aceptaUnCelularValidoOAusente` con `+573001234567`, `+5730012345`, `" +573001234567 "`, vacío y null; `rechazaUnCelularMalFormado` con `3001234567`, `+57 300 123 4567`, `+57-3001234567`, `+0573001234567`, `+5730012345678901`, `+123456` y el mensaje nuevo) y `UpdateUserProfileRequestTest` (celular sin `+` → error en `phone`; el blanco sigue siendo válido). Fallan por la regla vieja
- [x] 1.3 GREEN: `Formatos.CELULAR` = `^\s*(\+[1-9][0-9]{6,14})?\s*$` y `Formatos.MENSAJE_CELULAR` nuevo ("El celular va en formato internacional: + e indicativo, de 7 a 15 digitos en total, sin espacios ni separadores"), con el javadoc al día. Pasan los tests de 1.2
- [x] 1.4 Ajustar a `+57…` los celulares de `UserControllerTest`, `UserMother` y los demás tests que mandan o esperan un celular sin indicativo; `gradlew test` en verde
- [x] 1.5 Pasar a `+57…` los requests existentes de `bruno/users/` que mandan un celular sin `+` (`modificar-perfil.yml`, `perfil-tras-modificar.yml`); los de 1.1 pasan con `verificar-bruno.ps1 -RecargarDatos -Objetivo users`

## 2. Datos y contrato

- [x] 2.1 `docs/database/test-data.sql` (`prueba@`: `+573001234567`) y `docs/database/demo-data.sql` (`+573109876543`); se cargan sin error con `psql -U postgres -d financeapp -v ON_ERROR_STOP=1 -f docs/database/cargar-datos-local.sql`
- [x] 2.2 `docs/api/auth.md` (regla de `phone` y el mensaje del 400) y `docs/api/usuarios.md` (ejemplos con `+57…`); se verifica releyendo que ningún ejemplo del contrato muestre un celular sin `+` (`grep -n "phone" docs/api/*.md`)
- [x] 2.3 Replicar en `bruno-personal/` sin ejecutarlo: la descripción de `phone` en `auth/registro.yml` y en `perfil/modificar.yml`, y cualquier celular de ejemplo, al formato `+` e indicativo

## 3. Verificación

- [x] 3.1 `.\gradlew.bat build` en verde, con el conteo real de tests y la cobertura por encima del 85 %
- [x] 3.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde (colección completa, sin requests omitidos y saldos cuadrados), con los conteos reales

## Workflow follow-up

- Archivar el change en la misma rama (`/opsx:archive fin-59-phone-e164`) antes del PR.
