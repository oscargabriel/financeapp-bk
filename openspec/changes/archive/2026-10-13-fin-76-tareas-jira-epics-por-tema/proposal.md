## Why

FIN-76, del epic FIN-74 (Calidad y mantenimiento). Aplica sus decisiones del 13-10-2026: el
tablero tiene dos capas, con un epic por tema y una tarea por repo. Los epics los mantiene
`crear-tareas` desde la raíz y los repos solo los leen. Los bugs y descubrimientos van al epic de la
tarea donde aparecieron.

`crear-tareas` ya trabaja así y guarda en el epic el objetivo, las decisiones, el flujo y los
pedidos. Las tareas que crea no los copian, los nombran por su clave. `tareas-jira` sigue con el
modelo anterior:

- describe los epics como «las etapas, también con su componente»;
- al tomar una tarea solo lee su `## Contexto` y sus criterios, así que no ve las decisiones que la
  limitan;
- crea issues con el epic solo «si alguno encaja».

Con eso, un change puede contradecir una decisión del tema sin enterarse, y un descubrimiento puede
quedar sin epic, fuera de la vista de la raíz.

## What Changes

- **`.claude/skills/tareas-jira/SKILL.md`**:
  - *Dónde está el tablero*: describe las dos capas. El epic es un tema, no lleva componente, se
    mantiene desde la raíz y el repo no edita su descripción. La tarea lleva componente y `parent`
    obligatorios.
  - *1. Consumir*: la consulta excluye los epics (`issuetype != Epic`). Al elegir una tarea se lee
    también su epic: `## Decisiones`, `## Fuera de alcance` y `## Flujo`. Si un criterio de la tarea
    choca con una decisión del epic, la tarea no se toma: se comenta el choque, sigue en su estado y
    se dice en chat. Si el usuario nombra un epic por su clave, se ofrecen sus tareas `Backend`.
  - *3. Proponer*: el change parte de la tarea y de su epic.
  - *9. Cerrar*: antes de pasar la tarea a `Por revisar`, se deja un comentario
    `## Notas de implementación` con la ruta del change archivado y con cada decisión de
    implementación que cambie algo general del tema. Ahí la lee la raíz al refinar el epic. Va en un
    comentario y no en la sección de la descripción porque el MCP no devuelve ADF (ver `design.md`).
  - *Un encargo que llega suelto*, *10. Descubrimientos* y *Crear un issue*: toda tarea nueva lleva
    `parent`. Un descubrimiento o un bug va al epic de la tarea donde apareció. Un encargo suelto va
    al epic de su tema y, si ninguno encaja, se pregunta, porque el repo no crea epics. El
    `## Contexto` abre con «Parte del epic FIN-x: leer sus `## Decisiones` antes de proponer» y no
    copia las decisiones. La plantilla no lleva `## Notas de implementación`, porque las notas van
    en un comentario.
  - *Prohibiciones*: nunca crear un epic ni editar su descripción, y nunca crear una tarea sin
    `parent`.
  - Donde dice «etapa» por epic (prioridad `Medium`, paso 10, prohibiciones), pasa a decir «tema» o
    «epic».
- **`openspec/config.yaml`**: la regla del proposal pide abrir con la clave del issue **y la de su
  epic**, y nombrar las decisiones del epic que el change aplica.
- **`AGENTS.md`**, *Flujo de trabajo*: el paso 1 dice que la tarea se lee con su epic, y el 3 que el
  change parte de los dos.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna. Cambia el flujo de trabajo del agente, no el comportamiento del API. El change lleva
`skip_specs: true`.

## Cobertura de los criterios

| Criterio de FIN-76 | Cómo se cumple |
|---|---|
| 1. Al tomar se lee el epic (`## Decisiones`, `## Fuera de alcance`, `## Flujo`); el change parte de los dos | Paso 1, paso 3, `openspec/config.yaml` y `AGENTS.md` |
| 2. Choque entre un criterio y una decisión: no se toma, se comenta, sigue en su estado, se dice en chat | Paso 1 |
| 3. Toda tarea creada lleva `parent`; descubrimiento o bug al epic de la tarea; sin epic que encaje, se pregunta | Encargo suelto, paso 10, *Crear un issue*, prohibiciones |
| 4. El `## Contexto` abre con «Parte del epic FIN-x…» y no copia las decisiones | *Crear un issue* (plantilla) |
| 5. La consulta de «siguiente tarea» excluye los epics | Paso 1 |
| 6. El texto describe el modelo nuevo; el repo no edita la descripción del epic | *Dónde está el tablero* y prohibiciones |
| 7. Una decisión que cambie algo general del tema queda en `## Notas de implementación` | Paso 9, en un comentario con ese encabezado y no en la sección de la descripción. Decisión del usuario del 13-10-2026 (ver `design.md`). `crear-tareas` tiene que leerlo ahí: ver *Fuera de alcance* |

## Impact

- Archivos del flujo de trabajo: `SKILL.md` de `tareas-jira`, `openspec/config.yaml` y
  `AGENTS.md`. Sin código de la app, ni esquema, ni Bruno.
- Hoy todas las tareas `Backend` abiertas tienen `parent`, y ningún epic abierto tiene componente.
  Lo comprobó la consulta del 13-10-2026, así que el modelo nuevo no deja tareas huérfanas.
- FIN-77 hace lo mismo en `financeapp-fr` y no depende de este change.

## Fuera de alcance

- Editar la descripción de FIN-74 u otro epic: le toca a `crear-tareas` desde la raíz.
- La versión de `tareas-jira` del front: FIN-77.
- Pasar los epics viejos (los que tienen `notion-etapa-*`) al formato con `## Decisiones`: es
  refinamiento desde la raíz. El skill tolera un epic sin esas secciones.
- Que `crear-tareas` lea las notas en el comentario `## Notas de implementación` y deje de poner esa
  sección vacía en la plantilla. Hoy la busca en la descripción el punto 4 de su paso *2. Ubicar el tema*. El skill vive en la
  raíz `1-finanzas` y este repo no lo edita: se cambia desde allí.
- La memoria `pendientes-etapa-conexion`, que todavía dice que los pendientes viven en Notion, no es
  del repo. Se corrige aparte.
