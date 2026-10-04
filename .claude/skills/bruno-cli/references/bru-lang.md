# Referencia del lenguaje Bru (.bru)

Archivos de texto plano que definen requests, colecciones y ambientes. Bloques `keyword { ... }`.
Interpolación `{{var}}` resuelta desde el environment activo, `vars`, o runtime (`bru.setVar`).

> **Formato legado.** Desde Bruno v3.1 el default es OpenCollection YAML. `.bru` sigue soportado
> con paridad de features y no está deprecado: usarlo en las colecciones que ya lo usan (el hub
> ALEA, por ejemplo). Para colecciones nuevas, ver `opencollection-yml.md`, que además trae la
> tabla de equivalencias bloque-a-clave para reescribir de un formato al otro.

## bruno.json (raíz de colección)

```json
{
  "version": "1",
  "name": "Emision Flotillas",
  "type": "collection",
  "ignore": ["node_modules", ".git"]
}
```

## collection.bru / folder.bru

Settings heredados por los requests de la colección/carpeta.

```
meta {
  name: Issuance Service Dev
  seq: 1
}

auth {
  mode: none
}

headers {
  X-App: alea
}

vars:pre-request {
  baseUrl: https://api.dev.example.com
}
```

- `collection.bru` aplica a toda la colección; `folder.bru` a su carpeta.
- `seq` ordena las carpetas/requests en la app y en la ejecución.

## meta

```
meta {
  name: crear-recibo 201
  type: http
  seq: 1
}
```

`type` casi siempre `http`. `seq` define el orden dentro de la carpeta.

## Bloque de método

Uno por request: `get` / `post` / `put` / `patch` / `delete` / `head` / `options`.

```
post {
  url: {{baseUrl}}/recibos
  body: json
  auth: basic
}
```

- `url` — soporta `{{var}}`.
- `body` — declara el tipo del bloque body (`json`, `text`, `xml`, `form-urlencoded`, `multipart-form`, `none`).
- `auth` — declara el modo (`none`, `basic`, `bearer`, `inherit`, …); las credenciales van en su propio bloque.

## headers / query / params

```
headers {
  Content-Type: application/json
  Authorization: Bearer {{token}}
  ~X-Debug: 1
}

query {
  page: 1
  limit: 10
}
```

Prefijo `~` deshabilita una línea (header/param/var comentado sin borrarlo).

## Bodies

```
body:json {
  {
    "folio": "PD-20260622000123",
    "monto": 99000.50   // JSONC: los comentarios // son tolerados
  }
}

body:text {
  texto plano
}

body:xml {
  <root><a>1</a></root>
}

body:form-urlencoded {
  grant_type: client_credentials
  client_id: {{clientId}}
}

body:multipart-form {
  archivo: @file(./datos.csv)
  campo: valor
}
```

## Auth

```
auth:basic {
  username: {{user}}
  password: {{pass}}
}

auth:bearer {
  token: {{token}}
}
```

Otros modos soportados por Bruno: `auth:oauth2`, `auth:apikey`, `auth:digest`, `auth:awsv4`.
El modo se declara en el bloque de método (`auth: basic`) y el detalle en `auth:<modo> { }`.

## Variables

```
vars:pre-request {
  timestamp: {{$isoTimestamp}}
  correlationId: abc-123
}

vars:post-response {
  reciboId: res.body.id
}
```

- `vars:pre-request` — se resuelven antes de enviar.
- `vars:post-response` — capturan datos de la respuesta de forma declarativa (alternativa simple a un script).

## Scripts (JS)

```
script:pre-request {
  const auth = Buffer.from(`${bru.getVar("user")}:${bru.getVar("pass")}`).toString("base64");
  req.setHeader("Authorization", `Basic ${auth}`);
}

script:post-response {
  bru.setVar("reciboId", res.body.id);
  if (res.status !== 201) bru.setVar("ultimoError", res.body);
}
```

API disponible:
- `bru.getVar(name)` / `bru.setVar(name, value)` — variables de runtime.
- `bru.getEnvVar(name)` / `bru.setEnvVar(name, value)` — variables del environment.
- `req` — request saliente: `req.setHeader`, `req.getBody`, `req.setBody`, `req.getUrl`.
- `res` — respuesta (solo post-response): `res.status`, `res.body`, `res.headers`, `res.getResponseTime()`.
- Modo `--sandbox developer` habilita `require` de paquetes npm y acceso a `fs` en scripts.

## Assert

Aserciones declarativas `expresión: operador [valor]`. Se evalúan tras la respuesta.

```
assert {
  res.status: eq 201
  res.body.estado: eq PENDIENTE
  res.body.id: isDefined
  res.body.items: isArray
  res.responseTime: lt 2000
}
```

Operadores comunes: `eq`, `neq`, `gt`, `gte`, `lt`, `lte`, `in`, `notIn`, `contains`, `notContains`,
`length`, `matches`, `isDefined`, `isUndefined`, `isNull`, `isTrue`, `isFalse`, `isEmpty`,
`isNumber`, `isString`, `isBoolean`, `isArray`. Prefijar el operador con `not` niega (ej. `not eq`).

## Tests

Aserciones programáticas (Chai `expect`). Cuentan para `--tests-only` y los reportes.

```
tests {
  test("status is 201", function() {
    expect(res.getStatus()).to.equal(201);
  });
  test("tiene id", function() {
    expect(res.getBody().id).to.be.a("string");
  });
}
```

## Environment

Un solo bloque `vars`. El nombre del archivo (sin `.bru`) es el valor de `--env`.

```
vars {
  baseUrl: http://localhost:8080
  user: user
  pass: password
  ~apiKey: secreto-deshabilitado
}
```

## docs

```
docs {
  # Crear recibo
  Crea un recibo en estado PENDIENTE. Requiere basic auth.
}
```

## Variables dinámicas útiles

Disponibles como `{{$...}}` (basadas en faker/utilidades de Bruno): `{{$isoTimestamp}}`,
`{{$timestamp}}`, `{{$randomUUID}}`, `{{$guid}}`, `{{$randomInt}}`, `{{$randomFullName}}`,
`{{$randomEmail}}`, `{{process.env.MI_VAR}}`.
