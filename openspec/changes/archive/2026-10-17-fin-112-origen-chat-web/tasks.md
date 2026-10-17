# Tasks

## 1. El origen `WEB_CHAT` en el core y en la base

- [x] 1.1 RED: en `TransactionControllerTest`, `unPendienteQueLlegoPorCorreoSaleConSuOrigen` y `unLoteQueDeclaraOrigenCorreoSeGuardaComoWeb` pasan a `@ParameterizedTest` sobre `EMAIL` y `WEB_CHAT` (el pendiente sale con su origen; el lote que lo declara sale `WEB`). En `TransactionUseCaseTest`, `aprobarUnPendienteQueLlegoPorCorreoConservaSuOrigenYSuGrupo` y `aprobarUnGrupoQueLlegoPorCorreoConservaElOrigenYElGrupo` se parametrizan igual, con `TransactionMother.conOrigen`. No compila porque el enum no tiene el valor
- [x] 1.2 GREEN: `WEB_CHAT` en `core/domain/model/TransactionOrigin.java`, con el comentario de que lo pone el asistente cuando la petición llega por `POST /api/assistant/messages` (FIN-112); el comentario de `TELEGRAM` dice que lo pone el bot (FIN-61). Los tests de 1.1 pasan
- [x] 1.3 `ck_transactions_origin` admite `WEB_CHAT` en `docs/database/schema.sql` (línea 528), y `docs/database/update/20261017_01_origen_chat_web.sql` hace lo mismo, con el formato de `20261015_02_origen_email.sql`: encabezado que dice que no reinicia los datos locales y que en Neon va **antes** del despliegue (la app nueva inserta `WEB_CHAT`) y la demo se recarga después; reversión comentada que borra antes las filas `WEB_CHAT`. Se verifica aplicándolo en local con psql y con la comparación de esquemas de `docs/database/modelo-datos.md`
- [x] 1.4 `docs/api/transacciones.md`: en la tabla de `origin` (línea ~142), `TELEGRAM` pasa a «El bot de Telegram (FIN-61). Hoy ningún endpoint lo produce» y se agrega `WEB_CHAT` → el [asistente](asistente.md#post-apiassistantmessages); en *Movimientos pendientes* (línea ~270), lo que registra el asistente entra con `"origin": "WEB_CHAT"`. Se verifica leyendo la tabla junto al enum

## 2. El canal en el asistente

Skills: `java-architect`, `bruno-cli`.

- [x] 2.1 RED en Bruno: `bruno/assistant/` espera `WEB_CHAT` donde hoy espera `TELEGRAM`: `aprobar-gasto.yml` (assert de `res.body.origin`), `confirmar-grupo.yml` (`t.origin`) y `pendientes-tras-el-gasto.yml` (`origin` del primero). Request nuevo `pendientes-tras-archivo.yml` con `seq: 68`, después de `archivo-con-texto` (67): `GET {{baseUrl}}/transactions/pending` con el Bearer de la carpeta, busca los de `groupId` `{{grupoArchivo}}` y comprueba que hay uno con `"origin": "WEB_CHAT"`; `archivo-descartar` y los siguientes (68–78) suben un `seq`. `folder.yml` (línea 18) dice que el alta pendiente entra con `origin` `WEB_CHAT`. Falla con `verificar-bruno.ps1 -RecargarDatos -Objetivo assistant` por el origen `TELEGRAM`
- [x] 2.2 RED: `ia/domain/model/AssistantChannel.java` (enum `WEB_CHAT`, `TELEGRAM`, con el comentario de por dónde entró la petición). `AssistantUseCaseTest`: las llamadas a `atender` pasan el canal, y un test nuevo comprueba que `pendientes.register` recibe el canal con el que se llamó a `atender`, con `TELEGRAM` y con `WEB_CHAT`. `AssistantControllerTest`: `responde(...)` y los `verify` esperan `AssistantChannel.WEB_CHAT`, en JSON (`milCaracteresSeAceptanYPasanTalCualConElUsuarioDelToken`) y en multipart (`elArchivoYElTextoLleganAlAsistenteConElUsuarioDelToken`). `PendienteDelAsistenteAdapterTest`: `registraElLotePendienteConElOrigenDelAsistente…` se parametriza: `WEB_CHAT` → `TransactionOrigin.WEB_CHAT`, `TELEGRAM` → `TransactionOrigin.TELEGRAM`; los otros tres pasan `WEB_CHAT`
- [x] 2.3 GREEN: `AssistantPort.atender(UUID userId, AssistantChannel canal, AssistantMessage mensaje)`, con el javadoc de que el canal, como el usuario, lo fija quien llama. `AssistantUseCase.atender` lo pasa a `crear(...)` y de ahí a `pendientes.register(userId, canal, …)`. `PendingMovementPort.register(UUID userId, AssistantChannel canal, List<NewMovement> movements)`, y su javadoc deja de decir «con el origen del asistente». `AssistantController.message` y `messageConArchivo` pasan `AssistantChannel.WEB_CHAT`. `puente/PendienteDelAsistenteAdapter.register` traduce con un `switch` sin `default` (`design.md`). Los tests de 2.2 pasan
- [x] 2.4 `docs/api/asistente.md` (línea ~121): lo registrado entra con `"origin": "WEB_CHAT"`, y una línea dice qué origen pone cada canal: el endpoint `WEB_CHAT`, el bot de Telegram `TELEGRAM` (FIN-61). `contrato-api.md` no cambia. Se verifica leyendo el párrafo junto a la tabla de 1.4
- [x] 2.5 `bruno-personal/`: no cambia la ruta, el cuerpo ni la autenticación de ningún request, y el request nuevo depende de `{{grupoArchivo}}` de la colección de prueba. Confirmar que no hay nada que replicar y decirlo en el cierre
- [x] 2.6 La carpeta pasa con `verificar-bruno.ps1 -RecargarDatos -Objetivo assistant`, con el update de 1.3 aplicado en local

## 3. Pendientes del chat web en la demo

- [x] 3.1 `docs/database/demo-data.sql`: `\set grupo_chat` después de `grupo_import` (línea ~149), con el sufijo `…-000000000008` (el siguiente libre de los `e000000…`) y una línea de comentario «Pendientes del chat web (FIN-112).». El bloque *Pendientes del asistente* (línea ~765) agrega la columna `origin` al `VALUES`: `grupo_a` y `grupo_b` siguen `TELEGRAM`, y un grupo `grupo_chat` `WEB_CHAT` de dos gastos del mismo mensaje (un domicilio en la tarjeta y un parqueadero en efectivo, hace 5 horas). Su comentario y el encabezado del archivo (líneas 25–32) describen el grupo del chat web
- [x] 3.2 Se verifica con `psql -U postgres -d financeapp -f docs/database/cargar-datos-local.sql`: carga sin error para `demo@` y `dev@`, y el resumen final muestra el grupo `WEB_CHAT` junto a los de `TELEGRAM`, `EMAIL`, `WEB` e `IMPORT`

## 4. Verificación

- [x] 4.1 `.\gradlew.bat build` en verde, con el conteo real de tests y la cobertura sobre el umbral
- [x] 4.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde (la colección entera y `check-saldos.sql`), con los conteos reales

## Workflow follow-up

- Archivar el change en la rama antes del PR.
- El PR dice que el update solo amplía el check: no reinicia los datos locales del front.
- En Neon: aplicar el update **antes** de desplegar, desplegar y después recargar la demo de `demo@`.
  Anotar el update en *Registro de updates aplicados* de `docs/despliegue.md` cuando se aplique.
