## 1. Contrato

Skills: ninguna de dominio (no toca código ni `bruno/`).

- [x] 1.1 Borrar «Series y compras en cuotas en monedas distintas de COP.» de «Lo que el API todavía no tiene» en `docs/api/contrato-api.md`. Se verifica con `Select-String -Pattern 'distintas de COP' docs/api/contrato-api.md` sin resultados — 0 coincidencias; `git diff --stat`: 1 línea borrada
- [x] 1.2 Revisar las menciones de `COP` y de moneda del contrato: ninguna afirma que series o cuotas sean solo en COP. Se verifica con `Select-String -Pattern 'COP' docs/api/contrato-api.md` y la lectura de las secciones de `recurrences` e `installment-purchases` — las demás son ejemplos de JSON o la unidad mínima por moneda (líneas 1650 y 1694); las secciones de series y cuotas dicen «en una moneda activa»

## 2. Verificación

- [x] 2.1 `.\gradlew.bat build` en verde, con el conteo real de tests — con `--rerun-tasks`: 1431 tests, 0 fallos, 0 errores, 0 omitidos
- [x] 2.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con los requests, tests y assertions reales, sin omitidos, y las cuentas cuadradas — con `-Puerto 8081` (el 8080 lo ocupaba otra app): 882 requests (882 Passed), 629/629 tests, 1724/1724 assertions; las 68 cuentas cuadran

## 3. Cierre

- [x] 3.1 Enlace `Blocks` de FIN-79 hacia FIN-80 (copia del contrato en el front), y avisar en chat que el `## Depende de` de FIN-80 queda sin la línea. Se verifica con `getJiraIssue` de FIN-80 (`issuelinks`) — enlace 10045, FIN-80 `is blocked by` FIN-79
