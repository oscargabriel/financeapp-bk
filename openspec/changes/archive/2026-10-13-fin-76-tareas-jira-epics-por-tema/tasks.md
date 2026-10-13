## 1. `tareas-jira/SKILL.md`

Sin skills de dominio: no toca código de la app.

- [x] 1.1 *Dónde está el tablero*: dos capas, con el epic por tema, sin componente, mantenido desde la raíz y sin edición desde el repo, y la tarea con componente y `parent` obligatorios. La fila `Etapa` de la tabla pasa a `Tema`. Se verifica leyendo la sección contra los criterios 6 y 3 de FIN-76
- [x] 1.2 *1. Consumir*: la consulta con `issuetype != Epic`, la lectura del epic (`## Decisiones`, `## Fuera de alcance`, `## Flujo`), el choque con una decisión (no se toma, se comenta, conserva su estado, se dice en chat), el epic sin esas secciones o con `por-refinar`, y el epic nombrado por clave (se ofrecen sus tareas `Backend`). Se verifica corriendo la consulta nueva contra Jira: no devuelve epics
- [x] 1.3 *3. Proponer*: el change parte de la tarea y de su epic, y lo que el epic deja fuera de alcance queda fuera del change. *4. Aprobar*: el resumen nombra las decisiones del epic que aplica. Se verifica contra el criterio 1
- [x] 1.4 *9. Cerrar*: antes de la transición se deja el comentario `## Notas de implementación`, con la ruta del change archivado y cada decisión de implementación que cambie algo general del tema, o que no hay ninguna. Se verifica en el grupo 3, cerrando FIN-76 con ese mismo procedimiento
- [x] 1.5 *Un encargo que llega suelto*, *10. Descubrimientos* y *Crear un issue*: `parent` obligatorio; un descubrimiento o un bug va al epic de la tarea en curso; un encargo suelto va al epic de su tema; si ningún epic encaja, se pregunta. La plantilla abre `## Contexto` con «Parte del epic FIN-x: leer sus `## Decisiones` antes de proponer», sin copiar decisiones, y no lleva `## Notas de implementación`. Se verifica contra los criterios 3 y 4
- [x] 1.6 *Prohibiciones*: no crear epics, no editar su descripción y no crear tareas sin `parent`. Además, «etapa» pasa a «tema» o «epic» en la prioridad `Medium`, en el paso 10 y en las prohibiciones. Se verifica con `rg -n -i "etapa" .claude/skills/tareas-jira/SKILL.md`: no queda ningún uso en el sentido de epic

## 2. Reglas del repo

- [x] 2.1 `openspec/config.yaml`: la regla del proposal pide abrir con la clave del issue y la de su epic, y nombrar las decisiones del epic que el change aplica. Se verifica con `openspec validate fin-76-tareas-jira-epics-por-tema` en verde
- [x] 2.2 `AGENTS.md`, *Flujo de trabajo*: en el paso 1 la tarea se lee con su epic, y en el 3 el change parte de los dos. Se verifica leyendo la sección contra el criterio 1

## 3. Verificación y cierre

- [x] 3.1 `.\gradlew.bat build` en verde, con el conteo real de tests — con `--rerun-tasks`: 1262 tests, 0 fallos, 0 errores, 0 omitidos; cobertura de línea 98,6 %
- [x] 3.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con los requests, tests, assertions y cuentas cuadradas reales — 641 requests, 511/511 tests, 1234/1234 assertions; las 61 cuentas cuadran
- [x] 3.3 Al cerrar FIN-76 se deja el comentario `## Notas de implementación` con el procedimiento del 1.4. Se verifica con `getJiraIssue` con `comment` en los campos: el comentario aparece con su encabezado — comentario 10017 del 13-10-2026
