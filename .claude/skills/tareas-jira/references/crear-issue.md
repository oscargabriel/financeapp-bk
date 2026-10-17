# Crear un issue

Se crea con `jira.ps1 crear-lote`, que convierte el markdown a ADF (con `contentFormat: "markdown"`
Jira escapa `- [ ]` y los criterios quedan como texto), crea, enlaza y verifica en una llamada.

0. Resolver sus dependencias con *Dependencias y orden*, más abajo.
1. Escribir el issue en un `lote.md` del scratchpad, con una cabecera y la descripción:

   ```markdown
   === nuevo-1
   tipo: Historia
   summary: <qué queda hecho, en una frase>
   componente: Backend
   padre: FIN-<x>
   prioridad: Medium
   estado: pendiente
   bloqueada-por: FIN-<n>
   bloquea: FIN-<m>
   ---
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
   - `## Depende de` lleva una línea por cada clave de `bloqueada-por`, y se omite si no
     hay dependencias.
   - El último criterio es la verificación del repo. `Backend`: tests de la capa que toca,
     `gradlew build` y `bru run` en verde, y si toca endpoints, el archivo del recurso en
     `docs/api/`, su fila en `contrato-api.md` y `bruno/` al día.
     `Frontend`: tests de componente o servicio, `ng test` y `ng build` en verde.
   - No lleva `## Notas de implementación`: este repo deja las notas en un comentario al cerrar
     (paso 9).
   - Cabecera: `componente` es el del repo donde se resuelve y `padre` el epic, **siempre**.
     `estado` es `pendiente` (por defecto) o `Por hacer`; `prioridad`, `Medium` por defecto. Una
     `Tarea` lleva su etiqueta (`etiquetas: deuda-tecnica`). `bloqueada-por` y `bloquea` llevan las
     claves separadas por comas, también hacia issues existentes; `## Depende de` repite las de
     `bloqueada-por`.
   - Varios issues van en el mismo lote, cada uno con su alias en minúsculas (`nuevo-1`, `fr-1`). El
     texto que cita a otro del lote lo nombra por su alias: el script pone la clave real y crea antes
     al citado.
2. `pwsh -NoProfile -File ../.claude/scripts/jira.ps1 crear-lote <ruta> -Simular` valida (campos,
   alias, ciclos, que las claves existan y que el padre sea un epic) sin escribir. Se corrige hasta
   que pase.
3. Lo mismo sin `-Simular`: imprime `nuevo-1 = FIN-n`, una línea por enlace y la línea de
   `verificar` de cada issue creado. Ninguna puede tener `ERROR` (criterios que no salieron como
   casillas, un `## Depende de` que no coincide con los `is blocked by`): si alguna lo tiene, se
   corrige con `descripcion` o `enlazar` y se vuelve a correr `verificar`. Si falla a mitad, el
   mismo comando retoma desde `<ruta>.hecho`.
4. Para enlazar el issue en curso con uno existente (al cerrar, por *El contrato*), basta
   `jira.ps1 enlazar <el que bloquea> <el bloqueado>`.
5. Decir en chat el orden de ejecución que queda, sacado de los enlaces y con las bloqueadoras de
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

Un issue que cambia un archivo de `docs/api/`, aunque sea solo texto, **bloquea a los issues
`Frontend` abiertos que usan esa parte**: la copia `financeapp-fr/docs/api/` tiene que reflejarla.
Para saber si el front la usa:

1. `project = FIN AND component = Frontend AND statusCategory != Done AND text ~ "<ruta>"`.
2. Si `../financeapp-fr` existe, buscar la ruta en su `src/`, solo leyendo.
3. Si con eso no se puede decidir, se pregunta.

Si el front la usa y no hay ningún issue `Frontend` abierto que la cubra, se crea con este mismo
procedimiento el issue `Frontend` que trae la copia con `npm run sincronizar-contrato` y ajusta lo
que use la parte que cambió, en el epic del issue del back, en `pendiente` y bloqueado por él.

Se aplica al crear, si el issue ya dice qué parte del contrato cambia, y otra vez al cerrar (paso
9), si el change terminó tocando el contrato.
