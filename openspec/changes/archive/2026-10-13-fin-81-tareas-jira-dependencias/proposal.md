## Why

FIN-81, del epic FIN-74 (Calidad y mantenimiento). Aplica su decisión del 13-10-2026: todo skill que
crea tareas valida dependencias y orden al crear. Una tarea depende de otra cuando consume lo que la
otra crea o cambia, incluido el texto del contrato; cuando necesita algo que la otra decide o
despliega; o cuando cambia lo mismo y supone el estado que la otra deja. Nunca se escribe que una
tarea «no bloquea» sin preguntar, y `## Depende de` y los enlaces `Blocks` coinciden uno a uno.

El 13-10-2026 la raíz encontró FIN-50 sin su dependencia de FIN-49, escrita como «no bloquea» porque
FIN-49 solo cambiaba el texto del contrato, y otras cuatro dependencias sin enlazar (FIN-51, FIN-73,
FIN-78, FIN-40). `crear-tareas` ya valida al crear. `tareas-jira` de este repo revisa los
`is blocked by` antes de tomar, pero al crear un issue (*Un encargo que llega suelto*, paso 10 y
*Crear un issue*) solo enlaza «si depende de otro», sin criterio para decidirlo ni cruce con las
tareas abiertas.

## What Changes

- **`.claude/skills/tareas-jira/SKILL.md`**:
  - *Crear un issue*: una subsección *Dependencias y orden*, que es la única definición de la regla y
    a la que remiten *Un encargo que llega suelto* y el paso 10. Antes de redactar, el issue se cruza
    con las tareas abiertas de su epic y con las de otros epics que toquen lo mismo (JQL por palabra
    clave). Dice qué es dependencia (los tres casos del epic más el del contrato) y qué no (el
    origen, una mención de contexto). Si no está claro, se pregunta.
  - *Dependencias y orden*, el contrato: un issue que cambia `docs/api/contrato-api.md`, aunque sea
    solo texto, bloquea a los issues `Frontend` abiertos que usan esa parte. Si el front la consume y
    no hay ninguno, se crea el issue `Frontend` que sincroniza su copia de `contrato-api.md`,
    bloqueado por este. Se aplica al crear y otra vez al cerrar, si el change terminó tocando el
    contrato (ver `design.md`).
  - *Dependencias y orden*, enlaces hacia issues existentes: un issue nuevo puede bloquear a uno
    existente, también del otro repo. Se crea el enlace y se dice en chat. *Prohibiciones* lo agrega
    como excepción a «nunca modificar issues distintos del que está en curso».
  - *Crear un issue*, después de crear: `getJiraIssue` comprueba que las claves de `## Depende de` y
    los `is blocked by` coinciden uno a uno, salvo las `FA-n` de Notion. En chat se dice el orden de
    ejecución que queda y se avisa si un issue en `Por hacer` con `sprint-actual` quedó bloqueado
    por uno en `pendiente`.
  - *1. Consumir*, *Dependencias entre issues*: al tomar, si `## Depende de` y los enlaces no
    coinciden, se dice en chat antes de transicionar, y la comprobación de bloqueo usa la unión de
    los dos.
  - *9. Cerrar*: si el change tocó `docs/api/contrato-api.md`, se aplica la regla del contrato antes
    de pasar a `Por revisar`.
  - *Prohibiciones*: nunca escribir que un issue «no bloquea» a otro.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna. Cambia el flujo de trabajo del agente, no el comportamiento del API. El change lleva
`skip_specs: true`.

## Cobertura de los criterios

| Criterio de FIN-81 | Cómo se cumple |
|---|---|
| 1. Cruce con las tareas abiertas del epic y de otros epics; los tres casos de dependencia; el origen no lo es | *Dependencias y orden* |
| 2. Un cambio de contrato bloquea a los `Frontend` abiertos que lo usan; si no hay, se crea el de sincronizar la copia | *Dependencias y orden* al crear y paso 9 al cerrar |
| 3. Un issue nuevo puede bloquear a uno existente, también del otro repo; enlazar no es modificar | *Dependencias y orden* y *Prohibiciones* |
| 4. Si no está claro, se pregunta; nunca «no bloquea» | *Dependencias y orden* y *Prohibiciones* |
| 5. `## Depende de` y enlaces coinciden uno a uno, verificado con `getJiraIssue` | *Crear un issue*, paso de verificación |
| 6. Se dice el orden de ejecución; aviso si un `Por hacer` con `sprint-actual` queda bloqueado por un `pendiente` | *Crear un issue*, paso de reporte |
| 7. Al tomar, si `## Depende de` y enlaces no coinciden, se dice en chat antes de seguir | *Dependencias entre issues* del paso 1 |

## Impact

- Solo `.claude/skills/tareas-jira/SKILL.md`. Sin código de la app, ni esquema, ni Bruno, ni
  `AGENTS.md`: su *Flujo de trabajo* no describe cómo se crean los issues.
- FIN-82 hace lo mismo en `financeapp-fr` y no depende de este change.

## Fuera de alcance

- Corregir las dependencias que ya faltan en el tablero: es la auditoría *Revisar dependencias* de
  `crear-tareas`, desde la raíz.
- Editar la descripción de un issue existente para agregarle la línea de `## Depende de` cuando un
  issue nuevo lo bloquea: se enlaza y se avisa (ver `design.md`).
- La versión de `tareas-jira` del front: FIN-82.
