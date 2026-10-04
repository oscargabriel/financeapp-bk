# Referencia del CLI de Bruno (`bru`)

Versión instalada: **4.0.0** (npm global bajo `C:\nvm4w\nodejs`, en PATH); app de escritorio 4.1.0.
Shell: PowerShell 7.

## Comandos

| Comando | Uso |
|---|---|
| `bru run [paths...]` | Ejecuta un request, varios, o una carpeta. |
| `bru import <type>` | Importa una colección desde un spec **OpenAPI o WSDL**. |
| `bru --version` / `bru --help` | Versión / ayuda. |

Son los dos únicos subcomandos (`src/commands/` solo tiene `run.js` e `import.js`). No hay
subcomando `environments`: el ambiente se elige con `bru run --env <name>`. **Tampoco hay comando
de migración `.bru` → `.yml`**; ver `opencollection-yml.md`.

## `bru run` — todos los flags

Correr desde la raíz de la colección (donde vive `opencollection.yml` o `bruno.json`).

### Selección de qué correr
- `[paths...]` — uno o más archivos de request (`.yml` o `.bru`), o carpetas. Sin argumento corre
  la colección actual. Ojo: `bru run` parsea todo el árbol de la colección aunque se le pase un
  solo archivo, así que un error de sintaxis en otro request se ve igual.
- `-r`, `--recursive` — desciende a subcarpetas.

### Ambiente y variables
- `--env <name>` — usa `environments/<name>.yml` (o `.bru` según el formato de la colección).
- `--env-file <path>` — archivo de env explícito, `.bru` o `.json`, fuera de `environments/`.
- `--env-var <k=v>` — sobrescribe una variable; repetible (`--env-var a=1 --env-var b=2`).
- `--secrets-env-file <path>` — `.env` con la config del proveedor de secretos externos (claves
  `BRUNO_*`).
- `--global-env <name>` — environment global; requiere que la colección esté en un workspace.
- `--workspace-path <path>` — ruta del workspace (se autodetecta si se omite).

Además, un `.env` en la raíz de la colección se carga siempre y alimenta `{{process.env.X}}`,
sin importar el formato de la colección.

### Reportes
- `--reporter-json <file>` — reporte JSON.
- `--reporter-junit <file>` — reporte JUnit XML (para CI).
- `--reporter-html <file>` — reporte HTML.
- Se pueden combinar en una misma corrida. (`--output <file> -f json|junit|html` es la forma antigua.)
- `--reporter-skip-headers <a b>` / `--reporter-skip-all-headers` — omite headers del reporte.
- `--reporter-skip-request-body` / `--reporter-skip-response-body` / `--reporter-skip-body` —
  omite cuerpos del reporte. Útil cuando el payload lleva datos sensibles.

### Control de ejecución
- `--bail` — detiene tras el primer fallo (request/test/assert).
- `--tests-only` — solo corre requests con bloque `tests` **o con una assertion activa**.
- `--delay <ms>` — pausa entre requests.
- `--iteration-count <n>` — repite la corrida N veces.
- `--parallel` — corre las iteraciones de CSV en paralelo en vez de secuencialmente.

### Filtrado por tags
- `--tags <a,b>` — solo requests que tengan TODOS esos tags.
- `--exclude-tags <a,b>` — excluye requests con esos tags.

### Data-driven
- `--csv-file-path <file>` — una iteración por fila del CSV (columnas → variables).
- `--json-file-path <file>` — una iteración por elemento del array JSON.

### Sandbox y red
- `--sandbox safe|developer` — `safe` por defecto desde v3 (bloquea fs/npm en scripts).
  Usar `--sandbox developer` solo si los scripts necesitan `require`/`fs`.
- `--insecure` — ignora validación de certificado TLS.
- `--cacert <file>` / `--client-cert-config <file>` / `--noproxy` — TLS/proxy.
- `--ignore-truststore` — usa solo el `--cacert` dado, ignorando el truststore por defecto.
- `--cache-ssl-session` — reusa sesiones TLS entre requests (handshakes más rápidos).
- `--disable-cookies` — no guarda ni envía cookies automáticamente.
- `--verbose` — salida detallada (útil para depurar).

## Recetas

**Correr una carpeta contra Dev con reporte HTML:**
```powershell
bru run "Issuance Service Dev" -r --env dev --reporter-html report.html
```

**Solo smoke tests, detener al primer fallo:**
```powershell
bru run . -r --tags smoke --bail
```

**CI (JUnit + salir con código de error si falla):**
```powershell
bru run . -r --env qa --reporter-junit results.xml --bail
```
`bru run` sale con código ≠ 0 si algún request/test/assert falla → sirve como gate de pipeline.

**Data-driven desde CSV:**
```powershell
bru run crear-recibo.bru --env qa --csv-file-path datos.csv
```

**Override puntual de un secreto sin tocar el env:**
```powershell
bru run request.bru --env local --env-var token=eyJhbGc...
```

## `bru import`

Solo dos tipos: `openapi` y `wsdl`. **No importa colecciones de Postman** (eso quedó en la app de
escritorio); un `bru import postman ...` falla con error de `choices`.

```powershell
bru import openapi -s api.yml -o ./mi-coleccion -n "Mi API"
bru import openapi -s api.yml -o ./mi-coleccion --collection-format bru
```

| Flag | Uso |
|---|---|
| `-s, --source` | Archivo o URL del spec (requerido). |
| `-o, --output` | Directorio destino de la colección. |
| `-f, --output-file` | Alternativa: escribe un JSON en vez de una colección en disco. |
| `-n, --collection-name` | Nombre de la colección. |
| `--collection-format bru\|opencollection` | Formato de salida. **Default `opencollection`.** |
| `-g, --group-by tags\|path` | Agrupa por tags de OpenAPI (default) o por estructura de la URL. |
| `--insecure` | Salta verificación TLS al traer el spec desde una URL. |

`--collection-format` aplica al destino de esta importación; no convierte una colección existente.

## Notas operativas

- Editar un archivo y refrescar la app de Bruno muestra el cambio (mismos archivos).
- `bru run` hace HTTP real: verificar `--env` antes de correr colecciones de Emisión (crean pólizas/recibos reales).
- En `settings.json` de este usuario, `bru run` NO está auto-permitido (pide confirmación a propósito);
  `bru --version`, `bru --help`, `bru run --help` y `bru import` sí.
