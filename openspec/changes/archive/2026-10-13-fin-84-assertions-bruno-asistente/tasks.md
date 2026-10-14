## 1. Los dos requests de `bruno/assistant/`

Skills: `bruno-cli`. Si alguno falla al ejecutarse y la causa no es evidente, `java-debugging`.

- [x] 1.1 Antes de tocar nada, `bru run assistant -r --env local` contra la app de la rama muestra los dos archivos como omitidos por `YAMLParseError` (RED). Se verifica con la salida de esa corrida — `verificar-bruno.ps1 -Objetivo assistant -RecargarDatos`: «Skipping invalid file» en los dos, `66 (64 Passed, 2 Skipped)`
- [x] 1.2 `confirmar-categoria-borrada.yml`: `value: "[0].categoryId"`, con 409, un error, `INVALID_STATE` y `field` `[0].categoryId`. Se verifica con el request en `bru run assistant`, sin omitidos — 409 Conflict, `✓ res.body.errors[0].field: eq [0].categoryId`
- [x] 1.3 `grupo-sin-saldo-confirmar.yml`: `value: "[1].amount"`, con 409, un error, `INSUFFICIENT_FUNDS` y `field` `[1].amount`. Se verifica con el request en `bru run assistant`, sin omitidos. Si alguno de los dos falla porque el API no cumple su escenario de `transacciones/spec.md`, se para y se decide con el usuario antes de tocar código — 409 Conflict, `✓ res.body.errors[0].field: eq [1].amount`; la carpeta queda en `66 (66 Passed)`, 42/42 tests, 146/146 assertions, las 35 cuentas cuadran
- [x] 1.4 Escaneo de toda `bruno/` con el parser `yaml` de `@usebruno/cli`: cero archivos con error y ninguna assertion cuyo `value` salga como lista u objeto. Se verifica con la salida del escaneo — `archivos=904 assertions=1724 problemas=0` (antes: 2 archivos con error, los de 1.2 y 1.3)
- [x] 1.5 `bruno-personal/`: confirmar que ruta, cuerpo y autenticación de los dos requests no cambiaron, así que no hay nada que replicar. Se verifica con `git diff` de los dos archivos: solo cambian las líneas `value:` — una línea por archivo

## 2. Descubrimientos

- [x] 2.1 Issue nuevo en `pendiente`, epic FIN-74 si sigue vigente para mantenimiento del repo, para que `verificar-bruno.ps1` falle cuando `bru` omite archivos inválidos. Se verifica con `getJiraIssue` del issue creado — FIN-85, `Backend`, epic FIN-74, `is blocked by` FIN-84, igual que su `## Depende de`
- [x] 2.2 FIN-83 es un duplicado de FIN-84 (mismos archivos, misma causa, criterios incluidos en los de FIN-84): se descarta con el paso 11 de `tareas-jira`. Se verifica con la respuesta de Jira: etiquetas `cobertura` y `descartada`, estado `Listo` y el comentario `## Descartada` (10042)

## 3. Verificación

- [x] 3.1 `.\gradlew.bat build` en verde, con el conteo real de tests — con `--rerun-tasks`: 1431 tests, 0 fallos, 0 errores, 0 omitidos; cobertura de línea 98,5 %
- [x] 3.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con los requests, tests y assertions reales, sin ningún «Skipped», y las cuentas cuadradas — 882 requests (882 Passed, ninguno omitido), 629/629 tests, 1724/1724 assertions; las 68 cuentas cuadran
