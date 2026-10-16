# Tasks

## 1. El origen `EMAIL` en el dominio y en la base

Skills: `java-architect`.

- [x] 1.1 RED: en `TransactionControllerTest`, la lista de pendientes con un pendiente de origen `EMAIL` devuelve `"origin": "EMAIL"`, y un lote con `"origin": "EMAIL"` en el cuerpo sale `WEB`. Falla porque el enum no tiene el valor
- [x] 1.2 RED: en `ApprovePendingTransactionUseCaseTest` y `ApprovePendingGroupUseCaseTest`, aprobar uno a uno y por grupo un pendiente `EMAIL` devuelve `CONFIRMED` con origen `EMAIL` y el mismo grupo (el grupo se aprueba en `ApprovePendingGroupUseCase`; `AprobacionDePendientes` solo valida y no devuelve el movimiento)
- [x] 1.3 GREEN: `EMAIL` en `TransactionOrigin`, con el comentario de que lo reserva la ingesta desde correo (FIN-12); los tests de 1.1 y 1.2 pasan
- [x] 1.4 `ck_transactions_origin` admite `EMAIL` en `docs/database/schema.sql`, y `docs/database/update/20261015_02_origen_email.sql` hace lo mismo con su reversión. El encabezado dice que no reinicia los datos locales y que en Neon la demo se recarga después del update y del despliegue. Se verifica aplicándolo en local y con la comparación de esquemas de `docs/database/modelo-datos.md`
- [x] 1.5 `docs/api/transacciones.md`: la tabla de `origin` agrega `EMAIL`, reservado para la ingesta desde correo (FIN-12), que hoy solo traen los datos de demo y de prueba. Se verifica leyendo la tabla junto al enum

## 2. El pendiente de correo de `pendientes@` en Bruno

Skills: `bruno-cli`.

- [x] 2.1 RED: `bruno/pending/`. `login.yml` guarda `idPendienteCorreo`; `listar-pendientes` espera 4, con el de correo último y `"origin": "EMAIL"` (los demás `TELEGRAM`); `pendientes-con-desactivadas` espera 3; `pendientes-tras-rechazar` espera solo el de correo; `aprobar-correo` (seq 39) lo aprueba y comprueba `CONFIRMED`, `"origin": "EMAIL"` y su `groupId`; `pendientes-tras-aprobar-correo` (seq 40) espera la lista vacía. La documentación de `folder.yml` agrega la fila. Falla con `bru run pending -r --env local` sobre la base sin el pendiente
- [x] 2.2 GREEN: `test-data.sql` siembra para `pendientes@` un gasto `EMAIL` en `Cuenta pendientes`, en su propio grupo, con descripción de notificación bancaria y el desfase más antiguo de los pendientes. La carpeta pasa con `verificar-bruno.ps1 -RecargarDatos -Objetivo pending` salvo `aprobar-ajeno` y `rechazar-ajeno`, que necesitan el token de `auth/` (lo dice `folder.yml`); la corrida completa de 4.2 los cubre
- [x] 2.3 `bruno-personal/`: ningún endpoint cambió y los requests nuevos dependen de los ids sembrados de `pendientes@`, que en Neon no existe. Confirmar que no hay nada que replicar y decirlo en el cierre

## 3. Los pendientes de cada origen en la demo

- [x] 3.1 `demo-data.sql`: pendientes `EMAIL` en grupos, en el mes en curso y el anterior, con descripciones de notificación bancaria (compras con tarjeta, débitos automáticos). Del mes en curso solo entra lo que ya ocurrió
- [x] 3.2 `demo-data.sql`: un pendiente `WEB` en el mes en curso, un gasto que llegó en USD a la Mastercard en COP, con `original_amount` y el monto convertido con la tasa de la base, como el confirmado de FIN-96
- [x] 3.3 `demo-data.sql`: un grupo `IMPORT` de varios movimientos pendientes en el mes anterior
- [x] 3.4 El encabezado de `demo-data.sql` describe los pendientes de cada origen, y el resumen final agrupa los pendientes por grupo y origen. Se verifica con `psql -U postgres -d financeapp -f docs/database/cargar-datos-local.sql`: carga sin error para `demo@` y `dev@`, el resumen muestra los grupos `EMAIL`, `WEB`, `IMPORT` y `TELEGRAM`, y `GET /api/transactions/pending` de `demo@` los devuelve con su origen

## 4. Verificación

- [x] 4.1 `.\gradlew.bat build` en verde, con el conteo real de tests y la cobertura sobre el umbral
- [x] 4.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde (la colección entera y `check-saldos.sql`), con los conteos reales

## Workflow follow-up

- Archivar el change en la rama antes del PR.
- En Neon: aplicar el update, desplegar y después recargar la demo de `demo@`. Anotar el update en el
  registro de `docs/despliegue.md` cuando se aplique.
