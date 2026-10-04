---
name: bruno-cli
description: >
  Use when creating, editing, or running Bruno API collections — in the YAML format
  (opencollection.yml, folder.yml, request and environment .yml) or in the older .bru format
  (meta, method, headers, query, body, auth, vars, assert, tests, scripts) — or when using the
  `bru` CLI (`bru run` with env, reporters, tags, tests, data-driven runs). Skip for
  Postman/Insomnia collections or other API clients.
metadata:
  origen: skills-catalogo 2026-10-04
---

# Bruno CLI Skill

Editar y ejecutar colecciones de Bruno. Las colecciones son archivos de texto plano en disco;
Claude los lee/edita directamente. El CLI `bru` corre los requests.

## Contexto

- CLI `bru` **4.1.0** (npm global bajo `C:\nvm4w\nodejs`, en PATH) y app de escritorio **4.1.0**.
  Shell = PowerShell 7. La app y el CLI comparten los mismos archivos: editar y refrescar en la app.
- **Hub ALEA**: `C:\Users\ozamb\Desktop\asignacion\ALEA\COLECCIONES\api-collections\` — 39
  colecciones en formato `.bru` (Emisión Flotillas, Pricing, Multirramo, catálogos…). Cada
  subcarpeta con `bruno.json` es una colección propia. **No** usa carpeta `environments/`: separa
  por carpetas anidadas de ambiente/producto.
- **Colecciones en YAML** (referencia del esquema real, escrito por la app):
  `Desktop\importante\final\academic-report-system-back\bruno\` y
  `Desktop\importante\newsletter-generator-una\bruno\`.
- **financeapp-bk**: `Desktop\modo_chill\1-finanzas\financeapp-bk\bruno\` — YAML, con
  `environments/local.yml` y `prod.yml`.

## El MCP oficial (todavía no se usa)

Existe `usebruno/bruno-mcp` en la organización oficial: paquete `@usebruno/mcp` 0.1.0, MIT.
Verificado el 21-09-2026 — **no está publicado en npm**, 0 releases, 2 commits, sin tests. Se
instala clonando y compilando (`npm run build`, apuntar al `dist/index.js`). Empaqueta
`@usebruno/cli` 4.0.0, una minor por detrás del CLI instalado.

Expone cuatro herramientas, todas de lectura o ejecución: `list_collections`, `list_requests`,
`get_request`, `execute_request`. Reconoce `opencollection.yml`, así que el formato YAML lo entiende.

No sustituye a esta skill, por dos razones:

- **No escribe.** Ninguna herramienta crea ni edita requests. La sintaxis y las convenciones de las
  secciones siguientes siguen siendo trabajo de archivo.
- **`execute_request` corre un request por llamada**, cada una en su propio proceso `bru run`. Las
  variables de runtime no sobreviven entre llamadas: una colección que encadena con `bru.setVar`
  —financeapp-bk saca `accessToken` de `auth/login` y lo hereda en la raíz— responde 401 salvo que
  se le pase el token a mano por el parámetro `variables`. Tampoco hay equivalente de `bru run . -r`,
  ni `--bail`, ni reporters, ni tags.

Revisitar cuando publiquen `@usebruno/mcp` en npm o corten el primer tag.

## Dos formatos

Desde Bruno **v3.1** el formato por defecto es **OpenCollection YAML**. El `.bru` sigue soportado
con paridad de features y no está deprecado.

| Rol | YAML (default) | `.bru` (legado) |
|---|---|---|
| Raíz de colección | `opencollection.yml` (lleva también la config) | `bruno.json` + `collection.bru` |
| Carpeta | `folder.yml` | `folder.bru` |
| Request | `<request>.yml` | `<request>.bru` |
| Environment | `environments/<env>.yml` | `environments/<env>.bru` |

**Regla de decisión:** colección existente → seguir el formato que ya tiene, sin mezclar. Colección
nueva → YAML. **No hay comando de migración** `.bru` → `.yml` en el CLI: es reescritura manual
(`bru import` solo trae specs OpenAPI/WSDL).

## Sintaxis YAML (esencial)

**Request** (`system/status.yml`) — `auth` es el escalar `inherit` o `none`; las aserciones van en
`runtime.assertions` como lista, y los valores numéricos entre comillas:
```yaml
info:
  name: Status
  type: http
  seq: 1

http:
  method: GET
  url: "{{baseUrl}}/status"
  auth: inherit

runtime:
  assertions:
    - expression: res.status
      operator: eq
      value: "200"

docs: |
  Markdown libre. A nivel request es string plano.
```

**Raíz** (`opencollection.yml`) — el auth heredado va en `request.auth`, no en `http.auth`:
```yaml
opencollection: 1.0.0

info:
  name: mi-coleccion

request:
  auth:
    type: basic
    username: "{{user}}"
    password: "{{pass}}"

extensions:
  bruno:
    ignore:
      - node_modules
      - .git
```

**Environment** — las variables son una **lista**, nunca un mapa `clave: valor`:
```yaml
name: local
variables:
  - name: baseUrl
    value: http://localhost:8080
    enabled: true
    secret: false
    type: text
```

`folder.yml` lleva `info: {name, type: folder, seq}` y opcionalmente `request: {auth: inherit}`.

Todas las claves, operadores de `assertions`, `runtime.scripts`, `http.body` y tipos de `auth`:
`references/opencollection-yml.md`.

## Sintaxis `.bru` (esencial)

Bloques `keyword { ... }`. Interpolación `{{var}}` desde el environment o runtime (`bru.setVar`).

| Bloque | Uso |
|---|---|
| `meta { name, type, seq }` | Nombre, tipo (`http`), orden. |
| `get/post/put/delete { url, body, auth }` | Método + URL; declara tipo de body y modo de auth. |
| `headers { }` / `query { }` | Headers y query params, `clave: valor` por línea. |
| `body:json { }` / `body:form-urlencoded { }` / `body:text { }` | Cuerpo del request. |
| `auth:basic { username / password }` / `auth:bearer { token }` | Credenciales del request. |
| `vars:pre-request { }` / `vars:post-response { }` | Variables declarativas. |
| `script:pre-request { }` / `script:post-response { }` | JS con la API `bru`, `req`, `res`. |
| `assert { }` | Aserciones: `res.status: eq 201`, `res.body.id: isDefined`. |
| `tests { }` | Tests JS estilo `test("…", () => expect(res.getStatus()).to.equal(200))`. |
| `docs { }` | Documentación markdown. |

```
meta {
  name: crear-recibo 201
  type: http
  seq: 1
}

post {
  url: {{baseUrl}}/recibos
  body: json
  auth: basic
}

auth:basic {
  username: {{user}}
  password: {{pass}}
}

body:json {
  {
    "folio": "PD-20260622000123"
  }
}

assert {
  res.status: eq 201
}
```

El environment `.bru` es un único bloque `vars { clave: valor }`.

Detalle completo de cada bloque, operadores de `assert`, `body:*`, `auth:*` y la API de scripts:
`references/bru-lang.md`.

## Secretos

Convención oficial, igual para ambos formatos: un `.env` en la **raíz de la colección**, leído con
`{{process.env.MI_VAR}}` desde los environments o los requests. La carga no depende del formato.

- `.env` → **nunca** se versiona.
- `.env.sample` → plantilla versionada, sin valores reales.
- `.gitignore` **dentro de la colección** (es lo que genera la propia app), no una entrada en el
  `.gitignore` raíz del repo:
  ```
  # Secrets
  .env*
  !.env.sample

  # Dependencies
  node_modules
  ```
  La negación es necesaria: `.env*` a secas también tapa el `.env.sample`.

## Ejecutar con el CLI

`bru run [paths...]` corre un request, varios, o una carpeta. Correr desde la raíz de la colección
(donde vive `opencollection.yml` o `bruno.json`).

| Flag | Uso |
|---|---|
| `--env <name>` | Selecciona `environments/<name>.yml` (o `.bru`). |
| `--env-file <path>` | Archivo de env explícito (`.bru` o `.json`). |
| `--env-var k=v` | Sobrescribe una variable (repetible). |
| `-r` | Recursivo (carpetas anidadas). |
| `--reporter-json/-junit/-html <file>` | Genera reporte. |
| `--tests-only` | Solo requests con `tests` **o assertion activa**. |
| `--bail` | Detiene al primer fallo. |
| `--tags` / `--exclude-tags` | Filtra por tags (coma-separados). |
| `--delay <ms>` / `--iteration-count <n>` | Pausa entre requests / repeticiones. |
| `--csv-file-path` / `--json-file-path` | Ejecución data-driven. |
| `--sandbox safe\|developer` | `safe` por defecto (v3+); `developer` habilita fs/npm en scripts. |
| `--insecure` / `--verbose` | Ignora TLS / salida detallada. |

Ejemplos:
```powershell
bru run system/status.yml --env local
bru run . -r --env local
bru run "Issuance Service Dev" -r --env dev --reporter-html report.html
```

Todos los flags, recetas de reporters/CI y data-driven: `references/bru-cli.md`.

## Flujos de trabajo

1. **Nuevo request** en una colección existente: leer un archivo vecino para copiar la convención
   (formato, auth, `{{baseUrl}}`, `seq`) y crear el nuevo con la misma forma.
2. **Nuevo/editar environment**: `environments/<env>.yml` (o `.bru`); el nombre de archivo es el
   valor de `--env`.
3. **Correr y verificar**: `bru run <target> --env <env>`; para evidencia usar `--reporter-json`.
4. **Encadenar requests**: capturar valores con `bru.setVar(...)` en `runtime.scripts` de tipo
   `after-response` (o `script:post-response` en `.bru`) y consumirlos con `{{var}}` después
   (correr la carpeta con `-r` para preservar el orden).

## Precauciones (Red Flags)

- **Verificar `--env` antes de correr.** `bru run` hace HTTP real; las colecciones de **Emisión
  crean pólizas/recibos reales**. Preferir ambientes Dev/Qa; nunca correr Emisión contra prod sin
  confirmación explícita del usuario.
- `bru run` **no** está auto-permitido en `settings.json` (pide confirmación a propósito). No
  sortear ese prompt.
- No asumir `.bru`: mirar primero si la colección tiene `opencollection.yml`. Escribir un `.bru` en
  una colección YAML (o al revés) produce archivos que la app no muestra.
- `body:json` tolera comentarios `//` (JSONC), como en los payloads grandes de ALEA — no romperlos
  al editar.
- No existe subcomando `environments`: el ambiente se elige con `bru run --env <name>`.

Decir que una colección "pasa" requiere la salida de `bru run` con 0 fallos: un archivo puede verse
correcto y fallar por un `{{var}}` sin resolver en el environment.

## Reference Files

- `references/opencollection-yml.md` — Referencia completa del formato YAML (default desde v3.1):
  claves de `opencollection.yml`, `folder.yml`, requests y environments, `runtime.assertions` y
  `runtime.scripts`, `http.body`, tipos de `auth`, `settings`. Leer antes de crear o editar una
  colección YAML.
- `references/bru-lang.md` — Referencia completa del lenguaje Bru (`.bru`, formato legado):
  todos los `body:*`, `auth:*`, vars, scripts, operadores de `assert` y `tests`.
- `references/bru-cli.md` — Referencia completa de `bru run` y `bru import`: todos los flags,
  recetas de reporters para CI, ejecución data-driven (CSV/JSON), tags y `--sandbox developer`.
