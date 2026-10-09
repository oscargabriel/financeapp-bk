# asistente Specification

## Purpose
El asistente recibe un mensaje en texto libre, como llegaría de Telegram, y con un modelo de lenguaje
decide si registrar un movimiento, consultar movimientos o consultar el saldo del usuario del token.
Lo que registra queda pendiente de aprobación. Nació en FA-77.

## Requirements

### Requirement: Mensaje al asistente
`POST /api/assistant/messages` SHALL recibir `{"data": "<texto>"}` y responder 200 con `intent`,
`message`, y los campos `transaction`, `report` y `balance`, de los que solo SHALL venir el que
corresponde al `intent` y los demás en `null`. El texto que el modelo escriba SHALL NOT aparecer
en la respuesta: `message` sale de textos fijos del back.

#### Scenario: Respuesta sin función
- **WHEN** el modelo responde solo texto, sin llamar ninguna función
- **THEN** la respuesta es 200 con `"intent": "UNSUPPORTED"`, un `message` que lista lo que el asistente sabe hacer, y `transaction`, `report` y `balance` en `null`

#### Scenario: Función desconocida
- **WHEN** el modelo llama una función que no es `crear_movimiento`, `consultar_movimientos` ni `consultar_saldo`
- **THEN** la respuesta es 200 con `"intent": "UNSUPPORTED"` y no se crea ni se consulta nada

### Requirement: Formato del mensaje
`data` SHALL ser obligatorio, no vacío ni en blanco, y de hasta 1000 caracteres. Si no cumple, la
respuesta SHALL ser 400 y el modelo SHALL NOT llamarse.

#### Scenario: data ausente
- **WHEN** el cuerpo es `{}`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `data`

#### Scenario: data en blanco
- **WHEN** el cuerpo es `{"data": "   "}`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `data`

#### Scenario: data por encima del tope
- **WHEN** `data` tiene 1001 caracteres
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `data`

### Requirement: Asistente autenticado con JWT
El endpoint SHALL exigir el JWT de la API. Sin token, o con la credencial Basic compartida de
`/auth/*` y `/status`, SHALL responder 401 sin llamar al modelo.

#### Scenario: Sin token
- **WHEN** se envía el mensaje sin `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization` y `WWW-Authenticate: Bearer`

#### Scenario: Con la credencial Basic
- **WHEN** se envía el mensaje con la credencial Basic compartida
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

### Requirement: Crear un movimiento desde el asistente
Con `crear_movimiento`, el asistente SHALL registrar un movimiento del usuario del token con
`"status": "PENDING"` y `"origin": "TELEGRAM"`, en la moneda de su cuenta, y responder
`"intent": "CREATE_TRANSACTION"` con el movimiento en `transaction`. La cuenta, la cuenta destino y
la categoría SHALL resolverse por nombre, solo entre las activas del usuario, sin distinguir
mayúsculas ni tildes. El movimiento SHALL NOT mover saldos hasta aprobarse.

#### Scenario: Gasto con cuenta y categoría propias
- **WHEN** el modelo llama `crear_movimiento` con `tipo` `EXPENSE`, `monto` 20000, una cuenta y una categoría de gasto del usuario escritas en minúsculas
- **THEN** la respuesta es 200 con `"intent": "CREATE_TRANSACTION"` y un `transaction` con `"status": "PENDING"`, `"origin": "TELEGRAM"` y `amount` 20000, y el movimiento aparece en `GET /api/transactions/pending`

#### Scenario: El saldo no cambia
- **WHEN** el asistente registra un gasto sobre una cuenta
- **THEN** el `currentBalance` de esa cuenta en `GET /api/accounts` sigue igual

#### Scenario: Transferencia entre cuentas propias
- **WHEN** el modelo llama `crear_movimiento` con `tipo` `TRANSFER`, una cuenta y una `cuenta_destino` del usuario
- **THEN** la respuesta es 200 con un `transaction` pendiente de tipo `TRANSFER` y `categoryId` en `null`

#### Scenario: Sin fecha
- **WHEN** el modelo no envía `fecha`
- **THEN** el movimiento tiene como `occurredAt` el instante en que se atendió la petición

#### Scenario: Fecha de otro día
- **WHEN** el modelo envía `fecha` `2026-10-05` y hoy es otro día
- **THEN** el movimiento tiene como `occurredAt` el 2026-10-05 a las 12:00 en la zona de la aplicación, expresado en UTC

### Requirement: Lo que el asistente no puede resolver
Si un nombre de cuenta o de categoría no coincide con ninguno del usuario, o coincide con más de
uno, o si los argumentos no cumplen el formato del alta, el asistente SHALL responder 200 con
`"intent": "NEEDS_CLARIFICATION"` y un `message` que diga qué falta, y SHALL NOT crear ni consultar
nada.

#### Scenario: Cuenta que no existe
- **WHEN** el modelo llama `crear_movimiento` con una cuenta que el usuario no tiene
- **THEN** la respuesta es 200 con `"intent": "NEEDS_CLARIFICATION"`, el `message` nombra las cuentas del usuario, y `GET /api/transactions/pending` no cambia

#### Scenario: Cuenta de otro usuario
- **WHEN** el modelo nombra una cuenta que existe pero es de otro usuario
- **THEN** la respuesta es 200 con `"intent": "NEEDS_CLARIFICATION"` y no se crea nada

#### Scenario: Categoría ambigua
- **WHEN** el usuario tiene dos categorías activas cuyo nombre normalizado coincide con el que envía el modelo
- **THEN** la respuesta es 200 con `"intent": "NEEDS_CLARIFICATION"` y no se crea nada

#### Scenario: Monto inválido
- **WHEN** el modelo envía `monto` 0 o negativo
- **THEN** la respuesta es 200 con `"intent": "NEEDS_CLARIFICATION"` y no se crea nada

#### Scenario: Filtro de consulta que no se resuelve
- **WHEN** el modelo llama `consultar_movimientos` con una categoría que el usuario no tiene
- **THEN** la respuesta es 200 con `"intent": "NEEDS_CLARIFICATION"` y `report` en `null`

### Requirement: Consultar movimientos desde el asistente
Con `consultar_movimientos`, el asistente SHALL responder `"intent": "LIST_TRANSACTIONS"` con el
mismo reporte de `GET /api/reports/transactions` para el usuario del token, el rango y los filtros
dados. Sin rango, SHALL usar el mes en curso.

#### Scenario: Gastos de un rango
- **WHEN** el modelo llama `consultar_movimientos` con `desde`, `hasta` y `tipo` `EXPENSE`
- **THEN** la respuesta es 200 con `"intent": "LIST_TRANSACTIONS"` y un `report` igual al de `GET /api/reports/transactions` con esos parámetros

#### Scenario: Sin rango
- **WHEN** el modelo no envía `desde` ni `hasta`
- **THEN** el `report` cubre del primer al último día del mes en curso

### Requirement: Consultar el saldo desde el asistente
Con `consultar_saldo`, el asistente SHALL responder `"intent": "GET_BALANCE"` con el mismo saldo de
`GET /api/reports/balance` para el usuario del token y el rango dado, o el mes en curso sin rango.

#### Scenario: Saldo del mes
- **WHEN** el modelo llama `consultar_saldo` sin argumentos
- **THEN** la respuesta es 200 con `"intent": "GET_BALANCE"` y un `balance` igual al de `GET /api/reports/balance` sin parámetros

### Requirement: Falla del modelo
Si el modelo no responde dentro del tiempo límite, responde un error o devuelve algo que no se puede
interpretar, la respuesta SHALL ser 502 con el formato común de error y el código
`EXTERNAL_SERVICE_ERROR`, sin crear nada. Ni la respuesta ni el log SHALL incluir la API key, el
prompt, el texto del usuario ni el cuerpo devuelto por el modelo.

#### Scenario: Gemini responde 500
- **WHEN** la API de Gemini responde 500
- **THEN** la respuesta es 502 con un error `EXTERNAL_SERVICE_ERROR` en el campo `server` y no se crea ningún movimiento

#### Scenario: Gemini no responde a tiempo
- **WHEN** la API de Gemini no responde dentro del tiempo límite
- **THEN** la respuesta es 502 con un error `EXTERNAL_SERVICE_ERROR`

### Requirement: Configuración del modelo sin default
La aplicación SHALL leer `GEMINI_API_KEY` y `GEMINI_MODEL` sin valor por defecto y SHALL NOT
arrancar sin ellas, sea cual sea el proveedor. El stub del modelo SHALL NOT existir con el perfil
`prod`.

#### Scenario: Despliegue sin la key
- **WHEN** el servicio arranca sin `GEMINI_API_KEY`
- **THEN** la aplicación no arranca

#### Scenario: Stub pedido en producción
- **WHEN** el servicio arranca con el perfil `prod` y `ASISTENTE_PROVEEDOR=stub`
- **THEN** la aplicación no arranca: el stub no existe en `prod` y el adapter de Gemini solo se activa con el proveedor `gemini`

### Requirement: Categoría que el usuario no nombra
La instrucción que el asistente envía al modelo SHALL pedirle que, si el usuario no nombra la
categoría de un gasto o un ingreso, la deduzca de lo que describe, solo entre las categorías del
usuario que aplican a ese tipo, y la omita si ninguna encaja con claridad o caben varias. SHALL
pedirle también que no deduzca la cuenta. La categoría que envíe el modelo SHALL resolverse igual
que una nombrada por el usuario.

#### Scenario: La instrucción pide deducir la categoría y no la cuenta
- **WHEN** el asistente consulta al modelo
- **THEN** la instrucción de sistema y la descripción de `categoria` en `crear_movimiento` piden deducir la categoría de lo que describe el usuario cuando no la nombra, y la instrucción pide enviar la cuenta vacía cuando el usuario no la nombra

#### Scenario: Gasto sin categoría
- **WHEN** el modelo llama `crear_movimiento` con `tipo` `EXPENSE` y sin `categoria`
- **THEN** la respuesta es 200 con `"intent": "NEEDS_CLARIFICATION"`, un `message` que nombra las categorías de gasto del usuario, `transaction` en `null`, y `GET /api/transactions/pending` no cambia
