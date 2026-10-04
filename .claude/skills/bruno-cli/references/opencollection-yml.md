# Referencia del formato OpenCollection YAML

Formato por defecto de Bruno desde **v3.1**. Archivos `.yml` de texto plano. Interpolación
`{{var}}` igual que en `.bru`: environment activo, runtime (`bru.setVar`) y `{{process.env.X}}`.

Verificado contra `@usebruno/cli` 4.0.0 (`node_modules/@usebruno/filestore/src/formats/yml/`) y
contra colecciones reales escritas por la app 4.1.

## Archivos

| Archivo | Rol |
|---|---|
| `opencollection.yml` | Raíz de colección: identidad, config, settings heredados, `ignore`. Reemplaza a `bruno.json` + `collection.bru`. |
| `folder.yml` | Metadata de carpeta y settings heredados por su contenido. |
| `<request>.yml` | Un request HTTP. |
| `environments/<env>.yml` | Variables por ambiente. El nombre de archivo (sin `.yml`) es el valor de `--env`. |

**En formato YAML no se escribe `bruno.json`.** Toda la config vive en `opencollection.yml`.

## `opencollection.yml`

Claves de nivel superior, en el orden que emite el serializador:

```yaml
opencollection: 1.0.0          # version del spec, siempre "1.0.0"

info:
  name: mi-coleccion
  version: "1"                 # opcional

config:                        # opcional: proxy, certificados, protobuf
  proxy:
    inherit: true

request:                       # settings heredados por TODA la coleccion
  auth:
    type: basic
    username: "{{user}}"
    password: "{{pass}}"
  headers:
    - name: X-App
      value: alea
  variables:
    - name: token
      value: ""
  scripts:
    - type: before-request
      code: |-
        bru.setVar("ts", Date.now());

docs:                          # a nivel coleccion/carpeta es OBJETO
  content: |
    Markdown libre.
  type: text/markdown
bundled: false
extensions:
  bruno:
    ignore:
      - node_modules
      - .git
```

El auth heredado va en `request.auth`. El prefijo `http` solo existe en requests individuales.

## `folder.yml`

```yaml
info:
  name: System
  type: folder
  seq: 1

request:
  auth: inherit

docs:
  content: Notas de la carpeta.
  type: text/markdown
```

`seq` ordena las carpetas en la app y en la ejecución.

## Request `.yml`

```yaml
info:
  name: crear-recibo 201
  type: http                   # http | graphql | grpc | websocket
  seq: 1
  tags:
    - smoke

http:
  method: POST
  url: "{{baseUrl}}/recibos"
  params:
    - name: page
      value: "1"
      type: query              # query | path
    - name: id
      value: "{{reciboId}}"
      type: path
  headers:
    - name: Content-Type
      value: application/json
      disabled: false
  body:
    type: json                 # json | text | xml | form-urlencoded | multipart-form | graphql
    data: |-
      {
        "folio": "PD-20260622000123",
        "monto": 99000.50
      }
  auth: inherit

runtime:
  assertions:
    - expression: res.status
      operator: eq
      value: "201"
    - expression: res.body.estado
      operator: eq
      value: PENDIENTE
  scripts:
    - type: after-response     # before-request | after-response | tests
      code: |-
        bru.setVar("reciboId", res.body.id);

settings:
  encodeUrl: true
  timeout: 0
  followRedirects: true
  maxRedirects: 5

docs: |                        # a nivel request es STRING PLANO, no objeto
  Crea un recibo en estado PENDIENTE.
```

### `http.auth`

Escalar o objeto según el modo:

| Modo | Cómo se escribe |
|---|---|
| Heredar de la colección/carpeta | `auth: inherit` (string) |
| Sin autenticación | **omitir la clave `auth`** |
| Con credenciales | objeto con `type` + sus campos |

```yaml
  auth:
    type: basic                # basic | bearer | apikey | digest | ntlm | wsse |
    username: admin            # oauth1 | oauth2 | awsv4 | akamai-edgegrid
    password: secret
```

**No escribir `auth: none`.** Funciona (se parsea como modo `none`) pero el parser no contempla
ese literal y el CLI imprime `toBrunoAuth failed: Unsupported auth type` en cada corrida
(`filestore/src/formats/yml/common/auth.ts:236-345`, sin rama para `'none'`). Omitir la clave da
exactamente el mismo comportamiento sin el ruido, y es lo que emite el propio serializador. La app
de escritorio sí escribe `auth: none` explícito al guardar — no es un error, solo genera el aviso.

### `runtime.assertions`

Lista de objetos `{expression, operator, value, disabled?, description?}` — nunca el mapa
`expresión: operador valor` del `.bru`.

```yaml
runtime:
  assertions:
    - expression: res.status
      operator: eq
      value: "200"
    - expression: res.body.items
      operator: isArray
    - expression: res.responseTime
      operator: lt
      value: "2000"
```

Operadores: `eq`, `neq`, `gt`, `gte`, `lt`, `lte`, `in`, `notIn`, `contains`, `notContains`,
`length`, `matches`, `isDefined`, `isUndefined`, `isNull`, `isTrue`, `isFalse`, `isEmpty`,
`isNumber`, `isString`, `isBoolean`, `isArray`.

**Los valores numéricos van entre comillas** (`"200"`): el serializador los escribe así para que no
se relean como número al parsear. Un valor de texto plano como `UP` va sin comillas.

### `runtime.scripts`

Lista de `{type, code}` con `type` en `before-request` / `after-response` / `tests`. El bloque
`tests` usa Chai `expect` igual que en `.bru`:

```yaml
runtime:
  scripts:
    - type: tests
      code: |-
        test("status is 201", function() {
          expect(res.getStatus()).to.equal(201);
        });
```

## `environments/<env>.yml`

Las variables son una **lista de objetos**, nunca un mapa `clave: valor`:

```yaml
name: local
variables:
  - name: baseUrl
    value: http://localhost:8080
    enabled: true
    secret: false
    type: text
  - name: apiKey
    value: "{{process.env.API_KEY}}"
    enabled: true
    secret: false
    type: text
```

Una variable marcada `secret: true` se escribe sin `value` (`{secret: true, name, type?}`): el
valor se guarda fuera del archivo. Para secretos en git, preferir `{{process.env.X}}` + un `.env`
en la raíz de la colección (ver la sección **Secretos** de la skill).

Campos mínimos aceptados: `name` y `value`. El CLI omite el resto al serializar; la app los escribe
completos. Ambas formas parsean igual.

## Secretos y `.env`

El `.env` en la raíz de la colección se carga en `bru run` sin importar el formato
(`cli/src/commands/run.js`, sin rama por formato), así que `{{process.env.X}}` funciona igual en
YAML que en `.bru`.

## Migrar `.bru` → `.yml`

**No hay comando de migración.** `bru import` solo trae specs OpenAPI/WSDL; su
`--collection-format` elige el formato de salida de esa importación, no convierte una colección
existente. La conversión es reescritura manual (o abrir y reguardar desde la app de escritorio).

Equivalencias al reescribir:

| `.bru` | YAML |
|---|---|
| `meta { name, type, seq }` | `info: {name, type, seq}` |
| `get { url, auth }` | `http: {method: GET, url, auth}` |
| `headers { }` / `query { }` | `http.headers` / `http.params` como listas de `{name, value}` |
| `body:json { }` | `http.body: {type: json, data}` |
| `auth:basic { }` | `http.auth: {type: basic, username, password}` |
| `assert { }` | `runtime.assertions` (lista) |
| `script:post-response { }` | `runtime.scripts: [{type: after-response, code}]` |
| `tests { }` | `runtime.scripts: [{type: tests, code}]` |
| `docs { }` | `docs:` (string en request, objeto `{content, type}` en colección/carpeta) |
| `vars { }` del environment | `variables:` (lista de `{name, value}`) |
