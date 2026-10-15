# Crear un issue

La descripción va en ADF, no en markdown: con `contentFormat: "markdown"` Jira escapa `- [ ]` y los
criterios quedan como texto en vez de casillas.

0. Resolver sus dependencias con *Dependencias y orden*, más abajo.
1. Escribir la descripción en un archivo temporal, precedida de una línea `=== <clave>` (por ejemplo
   `=== nuevo`):

   ```markdown
   ## Contexto
   Parte del epic FIN-<x>: leer sus `## Decisiones` antes de proponer. <Por qué surgió, en qué
   issue apareció o el encargo tal como llegó, y las rutas de archivo que lo muestran.>
   ## Depende de
   - FIN-<n> — <qué entrega que esta necesita>
   ## Criterios de aceptación
   - [ ] <comportamiento observable, verificable>
   - [ ] <verificación del repo>
   ```

   - El `## Contexto` **no copia** las decisiones ni los pedidos del epic: los nombra por su clave.
     Una decisión que solo afecta a esta tarea sí va, como criterio.
   - `## Depende de` lleva una línea por cada enlace `is blocked by` del paso 4, y se omite si no
     hay dependencias.
   - El último criterio es la verificación del repo. `Backend`: tests de la capa que toca,
     `gradlew build` y `bru run` en verde, y si toca endpoints, `contrato-api.md` y `bruno/` al día.
     `Frontend`: tests de componente o servicio, `ng test` y `ng build` en verde.
   - No lleva `## Notas de implementación`: este repo deja las notas en un comentario al cerrar
     (paso 9).
2. `& .claude/skills/tareas-jira/md2adf.ps1 -Path <archivo> -OutDir <carpeta>` deja un
   `<clave>.json`. Soporta `## `, `> `, `- [ ]`, `- [x]`, `- `, párrafos, `código`, `**negrita**` y
   URLs; nada más.
3. `createJiraIssue`: `projectKey: "FIN"`, `issueTypeName`, `summary`, `parent` (clave del epic,
   **siempre**), `description` = contenido del `.json`, `contentFormat: "adf"`, `additional_fields` =
   `{"components": [{"name": "Backend"}], "priority": {"name": "Medium"}, "labels": [...]}`, con
   el componente del repo donde se resuelve. Sin `transition` queda en `pendiente`; con
   `transition: {"id": "21"}`, en `Por hacer`.
4. `createIssueLink` por cada dependencia, con `type: "Blocks"`, `inwardIssue` = el que bloquea y
   `outwardIssue` = el que depende. Incluye los enlaces en los que el nuevo bloquea a uno
   existente.
5. Verificar con `getJiraIssue` (campos `description`, `issuelinks`, `components`, `parent`) cada
   issue creado: las claves `FIN-n` de `## Depende de` y sus `is blocked by` coinciden uno a uno
   —las `FA-n` de Notion solo van en texto—, y los criterios salen como `- [ ]`, no `\[ \]`. Si
   algo sobra o falta, se corrige antes de seguir.
6. Decir en chat el orden de ejecución que queda, sacado de los enlaces y con las bloqueadoras de
   otros epics o del otro repo («en `financeapp-bk`: FIN-n; después, en `financeapp-fr`: FIN-m»).
   **Avisar si un issue en `Por hacer` quedó bloqueado por uno en `pendiente`**: no se
   puede tomar, y el usuario decide si sube la bloqueadora o baja
   la otra.

Los epics vigentes salen de `project = FIN AND issuetype = Epic AND statusCategory != Done` (campos
`summary` y `labels`).

## Dependencias y orden

Antes de redactar un issue, se cruza con las tareas abiertas de su epic
(`parent = FIN-x AND statusCategory != Done`) y con las de otros epics que toquen lo mismo
(`project = FIN AND issuetype != Epic AND statusCategory != Done AND text ~ "<palabra clave>"`, una
consulta por palabra clave: la ruta, la tabla, el archivo), con `summary`, `status` y `components`; los `issuelinks` se piden después, con
`key in (...)`, solo de las coincidencias. Las dos consultas incluyen los issues del otro repo.

**Es dependencia** —enlace `Blocks` y línea en `## Depende de`— cuando el issue:

- consume un endpoint, campo, código de error, tabla, función o script que otro crea o cambia;
- usa una parte del contrato que otro reescribe, **aunque sea solo texto** (ver *El contrato*);
- necesita algo que otro decide (una tarea de definición) o deja listo (despliegue, secreto,
  variable, dominio);
- cambia el mismo archivo o tabla que otro y supone el estado que ese deja.

**No es dependencia** el origen («surgió en FIN-n»), una mención de contexto ni dos issues del
mismo tema que se pueden cerrar en cualquier orden.

- **Si no está claro si hay dependencia, se pregunta.** Nunca se escribe que un issue «no bloquea»
  a otro ni se omite el enlace por suponerlo: así se perdió la dependencia de FIN-50 hacia FIN-49.
- La dependencia va en los dos sentidos: un issue nuevo —o, al cerrar, el que está en curso—
  puede bloquear a uno existente, también del otro repo. Se crea el enlace y se dice en chat. Enlazar no cuenta como modificar ese issue:
  su descripción no se toca, así que su `## Depende de` queda sin la línea nueva. Se dice en chat
  para que la raíz la agregue en *Revisar dependencias* de `crear-tareas`. Ver `design.md` de
  `fin-81-tareas-jira-dependencias`.
- Se revisa que los enlaces no formen un ciclo.

### El contrato

Un issue que cambia `docs/api/contrato-api.md`, aunque sea solo texto, **bloquea a los issues
`Frontend` abiertos que usan esa parte**: la copia `financeapp-fr/contrato-api.md` tiene que
reflejarla. Para saber si el front la usa:

1. `project = FIN AND component = Frontend AND statusCategory != Done AND text ~ "<ruta>"`.
2. Si `../financeapp-fr` existe, buscar la ruta en su `src/` y en su `contrato-api.md`, solo
   leyendo.
3. Si con eso no se puede decidir, se pregunta.

Si el front la usa y no hay ningún issue `Frontend` abierto que la cubra, se crea con este mismo
procedimiento el issue `Frontend` que sincroniza en su copia de `contrato-api.md` la sección que
cambió, en el epic del issue del back, en `pendiente` y bloqueado por él.

Se aplica al crear, si el issue ya dice qué parte del contrato cambia, y otra vez al cerrar (paso
9), si el change terminó tocando el contrato.
