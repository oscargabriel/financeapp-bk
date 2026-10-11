## Why

FA-128. El 10-10-2026 el usuario pidió desplegar: `dev` va 68 commits por delante de `main`
(`b163a65`, PR #29, 05-10-2026) y nada de lo hecho desde entonces está en producción. Con FA-100
cerrada, el servicio ya tiene las variables que `dev` exige sin default; lo que falta es poner Neon
al día, mergear y comprobar que la revisión nueva funciona.

El merge a `main` y los cambios en GCP y Neon los hace el usuario. La sesión prepara los comandos,
abre el PR `dev` → `main`, verifica lo que se puede verificar sin credenciales de producción y deja
el registro escrito.

## What Changes

### Antes del merge (usuario, con los comandos que da la sesión)

- **Variables del servicio**: `gcloud run services describe` contra la lista de propiedades sin
  default de `application.yaml` de `dev`: `SPRING_PROFILES_ACTIVE`, `DB_USERNAME`, `DB_PASSWORD`,
  `JWT_SECRET`, `BASIC_USERNAME`, `BASIC_PASSWORD`, `CORS_ALLOWED_ORIGINS`, `GEMINI_API_KEY` y
  `GEMINI_MODEL`. Ningún `@Value` del código pide una propiedad sin default fuera de esas
  (revisado al proponer). `DB_HOST`, `DB_PORT` y `DB_NAME` tienen default, pero apuntarían a
  `localhost`: también se comprueban.
- **Neon recreado desde cero en vez de aplicar los ocho updates.** Decisión del usuario
  (10-10-2026), ya ejecutada al aprobar esta propuesta: borró todas las tablas y cargó
  `schema.sql` y `seed.sql` de `dev`, para no arrastrar datos viejos. Los datos que había en
  producción se perdieron a sabiendas. Es el procedimiento *Crear la base desde cero* de
  `docs/despliegue.md`, sin crear un proyecto nuevo, así que los secretos `db-*` no cambian.
  - El `schema.sql` del día ya incluye los ocho updates: no se aplica ninguno. Desaparece el
    problema del orden de `20261010_01` (después del despliegue, con la tasa USD→COP previa) y
    `20261010_02` (antes): con la base vacía no hay movimientos que recalcular.
  - El registro de updates se reinicia, como pide ese procedimiento, con el commit del `schema.sql`
    cargado (`de1f996`, FA-51, último cambio en `dev`) y `seed.sql` de `00c3b63`.
  - La comparación de esquemas confirma que lo cargado es eso: línea base más todos los updates,
    `$hasta = '20261010_02'`, tiene que dar `sin diferencias`. Y la consulta de usuarios de prueba,
    `0`.
- **Correos admitidos**: el usuario insertó en `registration_allowlist` el correo con el que va a
  probar. Su cuenta de antes ya no existe: se registra de nuevo contra el servicio desplegado, y
  eso prueba de paso la lista.

El criterio 2 de la tarea («los 8 updates aplicados en orden») queda **sustituido** por la recreación,
no cumplido: lo que pedía, que Neon tenga el esquema de `dev`, lo comprueba la comparación.

### Merge y despliegue

- La sesión abre el PR `dev` → `main` con `gh pr create --base main --head dev`, con la lista de
  tareas que entran y el aviso de los updates. **No lo mergea**: `merge-pr-dev.ps1` rechaza los PR
  contra `main` y el merge es del usuario.
- Cloud Build en verde y la revisión nueva con la etiqueta `commit-sha` del merge
  (`gcloud run revisions list`).

### Pruebas contra producción (usuario, con los comandos que da la sesión)

La sesión no lee secretos de producción: los comandos toman la Basic de Secret Manager en la
terminal del usuario y guardan el token en una variable, sin imprimirlos.

- `/api/status` con la Basic: 200 y `UP`.
- Registro con el correo de la lista y login. Un correo fuera de la lista da 403.
- Alta de una cuenta USD y de un movimiento en ella. La base está vacía, así que no hay ninguna
  tasa: el alta en COP o en otra moneda la pide a ExchangeRate-API en el momento (FA-120); un 502
  ahí es el proveedor, no la base. Lo que se cree en la prueba se borra al terminar, salvo que el
  usuario quiera conservarlo.
- `GET /api/reports/transactions` sin 500.
- Un `POST /api/assistant/messages` real: `intent` en la respuesta, sin 502. Si el asistente deja
  un movimiento pendiente, se rechaza con `POST /api/transactions/{id}/reject`.

### Repositorio

- `docs/despliegue.md`:
  - el resultado de la comparación de variables;
  - el registro de updates reiniciado: la línea base pasa a `schema.sql` de `de1f996` y `seed.sql`
    de `00c3b63`, cargados el 10-10-2026 sobre las tablas borradas, sin updates aplicados encima;
  - `$hasta` del script de comparación en `20261010_02`. La línea base del script sigue en
    `00c3b63` más los updates: es lo que prueba que el `schema.sql` cargado coincide con ellos;
  - «Estado al …» con la revisión y el commit desplegados;
  - en *Correos admitidos*, quitar el bloque «Al promover FA-103 a `main`», que deja de ser una
    tarea futura.
- Este PR va a `dev` **al final**, cuando todo lo anterior esté hecho: lo que registra no existe
  hasta entonces. Llega a `main` en la siguiente promoción.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna. Es despliegue, base de producción y documentación. El change lleva `skip_specs: true`.

## Cobertura de los criterios

| Criterio | Cómo se cumple |
|---|---|
| 1. Variables del servicio comparadas con `application.yaml`, resultado en `docs/despliegue.md` | `describe` del usuario contra la lista de arriba; la sesión la anota |
| 2. Los 8 updates aplicados en orden, con snapshot, `ON_ERROR_STOP` y `sin diferencias`, respetando el encabezado | **Sustituido** por decisión del usuario: Neon recreado con `schema.sql` y `seed.sql` de `dev`. La comparación con `$hasta = '20261010_02'` da `sin diferencias` |
| 3. Registro de updates al día | La sesión lo reinicia con el commit cargado |
| 4. PR `dev` → `main` mezclado por el usuario; Cloud Build en verde y revisión con el `commit-sha` del merge | La sesión abre el PR; el usuario lo mergea y corre `revisions list` |
| 5. `/api/status` 200, login, alta en cuenta USD y reporte sin 500 | El usuario, con los comandos de la sesión, más el registro con el correo de la lista |
| 6. `POST /api/assistant/messages` real con `intent`, sin 502 | Igual que el 5 |
| 7. «Estado al …» con la revisión y el commit | La sesión lo escribe |

## Impact

- Producción: Neon ya recreado vacío por el usuario. Hasta el merge, la app de `b163a65` corre
  contra el esquema nuevo; los encabezados de los updates dicen que la app vieja sigue funcionando
  con cada uno, salvo `20261010_01`, cuyo riesgo (sumar dólares como pesos) no existe sin
  movimientos. El servicio pasa de `b163a65` al merge de `dev`.
- Repo: solo `docs/despliegue.md`. Sin código, ni esquema, ni Bruno.
- FA-92 (front) queda desbloqueada cuando esta cierre.

## Fuera de alcance

- Qué correos se invitan al registro.
- Cambiar `CORS_ALLOWED_ORIGINS` por la URL del front: FA-65.
- Mover Neon de región (FA-70) o separar el rol de la app (FA-69).
