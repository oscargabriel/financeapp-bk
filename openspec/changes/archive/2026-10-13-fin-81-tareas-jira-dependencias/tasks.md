## 1. `tareas-jira/SKILL.md`

Sin skills de dominio: no toca código de la app.

- [x] 1.1 *Crear un issue*: subsección *Dependencias y orden* con el cruce (tareas abiertas del epic, JQL por palabra clave en otros epics), los casos que son dependencia, los que no, la pregunta cuando no está claro y el enlace hacia un issue existente, también del otro repo, avisando en chat que le falta la línea en `## Depende de`. *Un encargo que llega suelto* y el paso 10 remiten a ella. Se verifica leyendo la sección contra los criterios 1, 3 y 4 de FIN-81
- [x] 1.2 *Dependencias y orden*, el contrato: un cambio de `docs/api/contrato-api.md`, aunque sea solo texto, bloquea a los `Frontend` abiertos que usan esa parte; si el front la consume y no hay ninguno, se crea el `Frontend` que sincroniza su copia, bloqueado por este. Cómo se decide si el front la usa (JQL, `../financeapp-fr` solo leyendo, pregunta). Se verifica contra el criterio 2
- [x] 1.3 *Crear un issue*, pasos finales: verificar con `getJiraIssue` que `## Depende de` y los `is blocked by` coinciden uno a uno (salvo `FA-n`), decir en chat el orden de ejecución y avisar si un `Por hacer` con `sprint-actual` quedó bloqueado por un `pendiente`. Se verifica contra los criterios 5 y 6
- [x] 1.4 *1. Consumir*, *Dependencias entre issues*: si `## Depende de` y los enlaces no coinciden, se dice en chat antes de transicionar y el bloqueo usa la unión. Se verifica contra el criterio 7 y aplicándolo a FIN-81 (coinciden: FIN-76)
- [x] 1.5 *9. Cerrar*: si el PR (`gh pr diff --name-only`) tocó `docs/api/contrato-api.md`, se aplica la regla del contrato antes de la transición. Se verifica contra el criterio 2
- [x] 1.6 *Prohibiciones*: enlazar `Blocks` desde o hacia un issue nuevo es excepción a «nunca modificar issues distintos del que está en curso», y nunca se escribe que un issue «no bloquea» a otro. La tabla de *Dónde está el tablero* dice que la dependencia cruza epics y repos. Se verifica con `rg -n "no bloquea" .claude/skills/tareas-jira/SKILL.md`: solo aparece como prohibición — líneas 441 y 477

## 2. Verificación y cierre

- [x] 2.1 `.\gradlew.bat build` en verde, con el conteo real de tests — con `--rerun-tasks`: 1320 tests, 0 fallos, 0 errores, 0 omitidos; cobertura de línea 98,6 %
- [x] 2.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con los requests, tests, assertions y cuentas cuadradas reales — 691 requests, 542/542 tests, 1341/1341 assertions; las 61 cuentas cuadran
- [x] 2.3 Al cerrar FIN-81, el paso 9 nuevo: el diff no toca `docs/api/contrato-api.md`, así que no aplica la regla del contrato; se deja el comentario `## Notas de implementación`. Se verifica con `getJiraIssue` con `comment` en los campos — comentario 10032 del 13-10-2026
