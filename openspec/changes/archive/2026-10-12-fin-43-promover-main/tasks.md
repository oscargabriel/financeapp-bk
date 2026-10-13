## 1. La migración a Jira entra en `dev`

Sin skills de dominio: no toca código.

- [x] 1.1 `.gitignore`: `docs/database/drop.sql`, `docs/database/reset-data.sql` y `bruno/environments/local-oz.yml`. Se verifica con `git status --short`: los tres dejan de aparecer — hecho
- [x] 1.2 Commit con la migración (`.claude/skills/tareas-jira/`, `.claude/skills/tareas-notion/` borrada, `AGENTS.md`, `.claude/rules/claude-code.md`, `.claude/settings.json`, `openspec/config.yaml`, `openspec/changes/archive/.gitkeep` borrado, `.mcp.json`, `.gitattributes`), `docs/superpowers/` y `.gitignore`. Sin este change: va en el PR del grupo 6 — commit `202cf02`
- [x] 1.3 PR a `dev` y `merge-pr-dev.ps1`. Se verifica con `git log origin/dev -1` — PR #75, merge `3e3e57f`

## 2. Verificación sobre el commit que se promueve

- [x] 2.1 `gradlew build` sobre el `origin/dev` del 1.3, con el conteo real de tests — `3e3e57f`, con `--rerun-tasks`: 1213 tests, 0 fallos, 0 errores, 0 omitidos; cobertura de línea 98,7 %
- [x] 2.2 `verificar-bruno.ps1 -RecargarDatos` sobre el mismo commit, con requests, tests, assertions y cuentas cuadradas — `3e3e57f`: 608 requests, 491/491 tests, 1159/1159 assertions; 62 cuentas cuadradas

## 3. Neon recreado con la demo, antes del merge (usuario)

- [x] 3.1 Ensayo del script de recreación contra una base local desechable (`fin43-ensayo`), con `schema.sql`, `seed.sql`, la tasa y `demo-data.sql` de `3e3e57f` — 12-10-2026: sin la tasa, `demo-data.sql` falla (FIN-45); con ella, `demo@` con 7 cuentas y 120 movimientos, `@front.local` admitido, 9 monedas, 22 categorías semilla, octubre con `total_income` 2 600 000 y las 7 cuentas cuadradas
- [x] 3.2 Snapshot en la consola de Neon (Backup & Restore → Create snapshot) — **no se tomó**: el usuario borró el esquema sin snapshot. Lo acepta (12-10-2026) porque la app todavía no se libera y la base se puede reiniciar; no había datos que recuperar más que su cuenta, que decidió perder
- [x] 3.3 El usuario corre el script contra Neon. Se verifica con los cinco `OK`, la comprobación de datos y `check-saldos.sql` sin cuentas descuadradas — 12-10-2026: el usuario lo hizo desde el editor SQL de Neon, con `fin43-demo-neon.sql` (la demo en SQL plano, con la tasa al principio) porque el editor no acepta los comandos de psql; confirma que quedó creado. La salida no se vio en la sesión
- [x] 3.4 Comparación de esquemas con `$hasta = '20261011_02'`. Se verifica con `sin diferencias` — 13-10-2026: `sin diferencias`, 1007 líneas de cada lado. Con el PostgreSQL 18 local en vez de Docker (no estaba corriendo). La primera corrida dio 14 diferencias falsas en los `COMMENT ON` con tilde: la consola del usuario está en 437 y PowerShell decodificaba `git show` con esa página; se corrigió forzando UTF-8, también en el bloque de `docs/despliegue.md`

## 4. Merge y despliegue

- [x] 4.1 `gh pr create --base main --head dev` con las tareas que entran y Neon ya recreado — PR #76
- [x] 4.2 El usuario mergea; Cloud Build en verde y `gcloud run revisions list` con la revisión activa en el `commit-sha` del merge — merge `c122550` (13-10-2026 00:32 UTC), build `8544bcba` `SUCCESS`, revisión `financeapp-bk-git-00011-tk2` con `commit-sha` `c122550` y el 100 % del tráfico

## 5. Pruebas contra producción (usuario)

- [x] 5.1 `GET /api/status` con la Basic: 200 y `UP` — 13-10-2026, revisión `00011-tk2`: 200 `{"status":"UP","services":{"postgres":"UP"}}`
- [x] 5.2 Login con `demo@` y `GET /api/monthly-spending`: el mes en curso trae `totalIncome` distinto de 0 — `2026-10`: `totalSpent` 3 256 361,90, `totalIncome` 2 600 000, 15 gastos; `2026-09` con `totalIncome` 8 000 000. Las mismas cifras del ensayo local

## 6. Repositorio y entrega

- [x] 6.1 `AGENTS.md`: la regla de datos en Neon y la frase del reinicio de datos
- [x] 6.2 `docs/database/demo-data.sql`: solo el encabezado, remitiendo a `docs/despliegue.md`
- [x] 6.3 `docs/despliegue.md`: «Estado al 12-10-2026», registro reiniciado, `$hasta`, *Crear la base desde cero* con el `DROP SCHEMA` y la demo, consulta de usuarios de prueba sin `demo@`, y los riesgos aceptados; además, UTF-8 forzado en el bloque de comparación
- [x] 6.4 Verificación del PR final: `gradlew build` y `verificar-bruno.ps1 -RecargarDatos` en verde, con los conteos reales — rama `feature/fin-43-registro-despliegue`, con `--rerun-tasks`: 1213 tests, 0 fallos, 0 errores, 0 omitidos; Bruno 608 requests, 491/491 tests, 1159/1159 assertions, 62 cuentas cuadradas
