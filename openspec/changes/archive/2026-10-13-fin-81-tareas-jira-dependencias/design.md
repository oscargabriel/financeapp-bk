## Context

`tareas-jira` crea issues por tres caminos: *Un encargo que llega suelto*, el paso 10
(descubrimientos) y *Crear un issue*, que es el procedimiento común. Hoy el último solo dice «si
depende de otro, `createIssueLink`». Al tomar, el paso 1 ya revisa los `is blocked by` y el
`## Depende de`, pero no qué hacer si no coinciden.

`crear-tareas`, en la raíz, ya tiene la regla en su *Dependencias y orden* y la auditoría en
*Revisar dependencias*. Este repo no edita ese skill ni los epics.

La copia del contrato del front está en `financeapp-fr/contrato-api.md`, hermana de este repo. La
fuente es `docs/api/contrato-api.md`, y el contrato de un endpoint se decide en el change del back,
no al crear la tarea.

## Goals / Non-Goals

**Goals:**

- Que todo issue que este repo crea salga con sus dependencias enlazadas y con el texto igual a los
  enlaces.
- Que un cambio de contrato no deje al front con una copia vieja sin un issue que lo diga.
- Que una discrepancia entre texto y enlaces se vea al tomar, no se pierda.

**Non-Goals:**

- Auditar el tablero. Lo hace la raíz.
- Reescribir descripciones de issues que no se crearon en la sesión.

## Decisions

### Una sola definición, en *Crear un issue*

La regla vive en una subsección *Dependencias y orden* de *Crear un issue*, y los otros dos caminos
remiten a ella. Repetirla en los tres lugares haría que se separen con el tiempo, que es lo que
pasó entre la raíz y este repo.

### La regla del contrato también se aplica al cerrar

El criterio 2 no dice cuándo se aplica. Al crear el issue casi nunca se sabe qué parte del contrato
va a cambiar: lo decide el change. El caso de FIN-49 fue justo ese: un cambio que «solo» tocaba el
texto. Por eso se aplica dos veces:

- al crear, si el issue ya dice qué parte del contrato cambia;
- en el paso 9, antes de pasar a `Por revisar`, si el PR tocó
  `docs/api/contrato-api.md`.

Para saber si el front usa esa parte: se buscan issues `Frontend` abiertos que la nombren (JQL
`text ~` con la ruta) y, si existe `../financeapp-fr`, la ruta en su `src/` y en su `contrato-api.md`,
solo leyendo. Si con eso no se puede decidir, se pregunta.

Descartado: aplicarla solo al crear. Dejaría fuera el caso que originó la tarea.

### Un issue existente que queda bloqueado se enlaza, sin tocar su descripción

Cuando un issue nuevo bloquea a uno existente, se crea el enlace `Blocks` y se dice en chat que a
ese issue le falta la línea en su `## Depende de`. La raíz la agrega en *Revisar dependencias*, que
ya marca «enlaces sin línea en `## Depende de`».

Descartado:

- **Reescribir la descripción del existente con `md2adf.ps1`.** El MCP la devuelve solo en markdown
  y reescribirla pierde lo que el conversor no soporta (ver `design.md` de
  `fin-76-tareas-jira-epics-por-tema`). Además puede ser del otro repo.
- **Comentar en el existente.** El criterio 3 deja enlazar, no comentar, y un issue del front no se
  comenta desde aquí.

Consecuencia: el uno a uno del criterio 5 se verifica sobre los issues creados. El existente queda
descuadrado hasta que la raíz lo arregle, y el aviso en chat es lo que lo hace visible.

### Al tomar, la discrepancia se reporta y se bloquea con la unión

Si `## Depende de` y los `is blocked by` no coinciden, se dice en chat cuáles sobran de cada lado
antes de transicionar. La comprobación de bloqueo toma la unión: un issue abierto en cualquiera de
los dos lados impide tomarlo. Si todos están cerrados, se sigue.

Descartado:

- **Detenerse hasta que el usuario responda.** Con la unión no hay riesgo de tomar algo bloqueado, y
  el ciclo tiene una sola espera, la del paso 4.
- **Crear el enlace que falta.** No se sabe cuál de los dos lados está mal: puede ser el texto el que
  sobra. Lo resuelve la auditoría de la raíz.

## Risks / Trade-offs

- **La búsqueda en `financeapp-fr` depende de que esté clonado al lado.** Si no está, queda la JQL y
  la pregunta.
- **La JQL por texto puede no encontrar un issue `Frontend` que usa la parte sin nombrar la ruta.**
  Por eso la búsqueda en el código del front, y la pregunta cuando no alcanza.
