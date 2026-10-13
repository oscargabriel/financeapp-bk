## Context

Desde el 13-10-2026, `crear-tareas` crea las tareas con la plantilla `## Contexto` /
`## Depende de` / `## Criterios de aceptación` / `## Notas de implementación`, esta última vacía para
que la llene el repo al cerrar. La raíz lee después esas notas en las tareas que están en `Listo` o
`Por revisar` para saber qué se implementó de verdad.

La descripción solo se puede escribir en ADF, porque en markdown Jira escapa `- [ ]` y los criterios
dejan de ser casillas. El MCP `atlassian` la devuelve **siempre en markdown**, aunque se le pida ADF.
Se comprobó el 13-10-2026 con `getJiraIssue`, `searchJiraIssuesUsingJql` y `fetch` sobre FIN-76. Esa
lectura escapa corchetes y asteriscos (`\[...\]` en FIN-27, `\*` en FIN-25) y exporta los enlaces
como `[url](url)` (FIN-14, FIN-42). `md2adf.ps1` no deshace ninguna de las dos cosas.

## Goals / Non-Goals

**Goals:**

- Que el repo lea las decisiones del tema antes de proponer y no tome una tarea que las contradice.
- Que todo lo que el repo crea quede colgado de un epic.
- Que la raíz encuentre en la tarea cerrada las decisiones de implementación que afectan al tema.

**Non-Goals:**

- Que el repo mantenga los epics. Lo hace la raíz.
- Validar el formato de los epics viejos. El skill lee las secciones que haya.

## Decisions

### Las notas van en un comentario, no en la descripción

Al cerrar, antes del comentario de cierre, se deja un comentario aparte que empieza con el
encabezado `## Notas de implementación`. Un comentario se escribe en markdown sin perder nada,
porque no lleva casillas, y no hace falta leer ni reescribir la descripción.

Decisión del usuario del 13-10-2026, tomada al descubrir durante la implementación que el diseño
aprobado no se podía hacer. Ese diseño era leer la descripción en ADF y agregarle al final los nodos
de `md2adf.ps1 -Base`, pero el MCP no devuelve ADF.

Alternativas descartadas:

- **Leer en markdown, agregar las notas y reconvertir todo con `md2adf`**, enseñándole a quitar los
  escapes fuera del código y a convertir `[texto](url)`. Cumplía el criterio 7 al pie de la letra,
  pero cada cierre reescribiría la descripción entera a través de un conversor parcial. Lo que no
  soporte, como tablas o listas anidadas, se perdería sin aviso.
- **Escribir la descripción con `contentFormat: "markdown"`.** Los criterios dejarían de ser
  casillas.

Consecuencias:

- El criterio 7 se cumple con el encabezado en un comentario, no con la sección de la descripción.
- `crear-tareas` busca hoy las notas en la descripción (punto 4 de su paso *2. Ubicar el tema*) y pone la sección vacía en su
  plantilla. Tiene que cambiar desde la raíz para leer el comentario. Este repo no lo edita: queda
  en *Fuera de alcance* del proposal y se avisa al cerrar.
- La plantilla de *Crear un issue* de este repo deja de llevar `## Notas de implementación`: una
  sección que nunca se llena confunde.

### Las notas se escriben siempre, con la ruta del change

Aunque no haya decisiones que afecten al tema, el comentario lleva la ruta del change archivado y
dice que no hay nada que cambie el tema. Así la raíz distingue «el repo la cerró sin nada que
reportar» de «nadie escribió las notas», y tiene a mano dónde leer el detalle.

Se descartó escribirlas solo cuando hay una decisión general: la ausencia del comentario no se puede
distinguir de un olvido.

### El choque con el epic se comprueba antes de tomar

Se lee el epic en el paso 1, junto con los criterios y los enlaces, antes de pasar la tarea a
`En curso`. Así el choque cae en el mismo lugar que «criterios que no permiten decidir» y
«dependencia abierta»: la tarea no se toma y conserva su estado. No hay alternativa real. Detectarlo
al proponer obligaría a deshacer la transición y la rama.

### El encargo suelto conserva su texto en la tarea

Antes, el texto de un encargo suelto iba literal en el `## Contexto` de la tarea. En el modelo nuevo
los pedidos viven en `## Pedidos` del epic, pero el repo no edita epics. El texto sigue yendo en la
tarea, después de la línea «Parte del epic FIN-x…». No es una decisión del epic, así que el
criterio 4 no lo prohíbe, y la raíz lo pasa al epic cuando lo refina.

## Risks / Trade-offs

- **Los epics migrados de Notion no tienen `## Decisiones`.** Leerlos no bloquea nada: sin
  decisiones no hay choque. Si el epic tiene `por-refinar`, el resumen del paso 4 lo menciona.
- **Hasta que `crear-tareas` cambie, la raíz no ve las notas.** Las buscaría en la sección vacía de
  la descripción. El riesgo se reduce avisando al cerrar FIN-76 para que el cambio se haga desde la
  raíz.
