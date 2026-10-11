## 1. Antes del merge

- [x] 1.1 Comandos al usuario: `describe` del servicio. Comparar con las propiedades sin default de `application.yaml` de `dev` y anotar el resultado — 10-10-2026: las 9 sin default presentes (`SPRING_PROFILES_ACTIVE=prod`, `CORS_ALLOWED_ORIGINS=*`, `GEMINI_MODEL` en texto plano; `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `BASIC_USERNAME`, `BASIC_PASSWORD`, `GEMINI_API_KEY` desde Secret Manager `latest`), más `DB_HOST`, `DB_PORT` y `DB_NAME`. No falta ninguna
- [x] 1.2 Comandos al usuario: comparación de esquemas con `$hasta = '20261010_02'` contra el Neon recreado (`sin diferencias`) y consulta de usuarios de prueba (`0`) — 10-10-2026: el usuario recreó Neon desde el editor SQL de Neon con `drop.sql` (sin versionar), `schema.sql` y `seed.sql`, así que en vez del volcado con Docker se comparó por nombre en el mismo editor: tablas, vistas, funciones y triggers de `schema.sql`, sin faltantes ni sobrantes; 9 monedas, 22 categorías semilla, 0 usuarios, 2 correos admitidos. Usuarios de prueba: 0

## 2. Merge y despliegue

- [x] 2.1 `gh pr create --base main --head dev` con las tareas que entran y el aviso de la base recreada — PR #64
- [x] 2.2 El usuario mergea; Cloud Build en verde y `revisions list` con el `commit-sha` del merge — merge `4894df2` (11-10-2026 03:09 UTC), build `1eafe368` `SUCCESS`, revisión `financeapp-bk-git-00010-dj8` lista con `commit-sha` `4894df2`

## 3. Pruebas contra producción

- [x] 3.1 `/api/status` 200 con la Basic; registro con el correo de la lista y login — 11-10-2026: el usuario lo probó contra la revisión `financeapp-bk-git-00010-dj8` y confirma que funciona
- [x] 3.2 Alta de cuenta USD y de un movimiento en ella; `GET /api/reports/transactions` sin 500 — 11-10-2026: confirmado por el usuario
- [x] 3.3 `POST /api/assistant/messages` real con `intent`, sin 502; rechazar el pendiente si lo deja — 11-10-2026: confirmado por el usuario
- [x] 3.4 Borrar los datos de prueba que el usuario no quiera conservar — el usuario conserva lo creado: es su cuenta real, no se borra nada

## 4. Documentación y verificación

- [x] 4.1 `docs/despliegue.md`: comparación de variables, registro reiniciado, `$hasta`, «Estado al …» y quitar «Al promover FA-103 a `main`»
- [x] 4.2 `gradlew build` y `verificar-bruno.ps1 -RecargarDatos` en verde — 1111 tests, 0 fallos, 0 omitidos; Bruno 533 requests, 427/427 tests, 1064/1064 assertions, 48 cuentas cuadradas
