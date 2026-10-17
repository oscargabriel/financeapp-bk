# Consumir, tomar, proponer y aprobar (pasos 1 a 4)

Esta sesión termina en la aprobación del paso 4. La implementación va en otra.

## 1. Consumir

`jira.ps1 buscar 'project = FIN AND component = Backend AND issuetype != Epic AND status = "Por hacer" ORDER BY priority DESC, key ASC' -Campos summary,issuetype,priority,labels,parent -Max 5`.
Mostrar las candidatas con clave, título, tipo, prioridad y epic. Si el usuario pidió "la
siguiente", tomar la primera; si no, esperar a que elija.

La clave desempata dentro de un mismo nivel de prioridad, y hace falta porque la mayoría de los
issues comparten prioridad: sin ella, "la siguiente" no es una pregunta con una sola respuesta.

### Qué significa cada prioridad

| Nivel | Cuándo | Cuántas esperar |
|---|---|---|
| `High` | Sale primero: un bug, una corrección urgente, o que el usuario diga que esa va antes que las demás. | Pocas; lo normal es una |
| `Medium` | **El valor por defecto de todo issue nuevo.** El trabajo ordinario de cada tema. | La mayoría |
| `Low` | Accesorio. Se hace cuando no queda nada en `Medium`. | Las que sobran |

`Highest` y `Lowest` existen en Jira y no se usan, a propósito: un nivel nuevo se infla igual que el
anterior si no hay una regla de uso, y `High` con la regla escrita ya cubre el caso de "esta va
primero". Si la mayoría de los issues tomables acaba en `High`, la prioridad dejó de discriminar: se
recalibra, no se agregan niveles.

`getJiraIssue` del elegido, con `responseContentFormat: "markdown"` y
`fields: ["summary", "description", "parent", "issuelinks", "components"]`, para leer `## Contexto`,
`## Criterios de aceptación`, sus enlaces y su componente.

**Si los criterios no permiten decidir cuándo el issue está hecho, no lo tomes.** Deja la duda en un
comentario (`jira.ps1 comentar`), mantén el estado en `Por hacer` y dilo en chat.

### El epic de la tarea

Después, `getJiraIssue` del epic (`parent`), también en markdown y con
`fields: ["description", "labels"]`, para leer sus `## Decisiones`,
`## Fuera de alcance` y `## Flujo`. Ahí está lo acordado con el usuario para todo el tema, y la
tarea no lo repite. El flujo dice qué otras tareas del tema hay, en los dos repos, y en qué orden.

**Si un criterio de la tarea choca con una decisión del epic, no la tomes.** Deja un comentario que
cite el criterio y la decisión, con su fecha, mantén el estado y dilo en chat. Resolver el choque es
refinar el tema, y eso se hace desde la raíz, no aquí.

Un epic migrado de Notion puede no tener esas secciones, o tener la etiqueta `por-refinar`. Eso no
bloquea: sin decisiones no hay choque. Se menciona en el resumen del paso 4.

### Dependencias entre issues

El orden que da la consulta —prioridad, luego clave— no conoce las ataduras reales. Un issue declara
lo que tiene que estar cerrado antes con enlaces `is blocked by`, y lo repite como texto en su
`## Depende de`:

```markdown
## Depende de
- FIN-44 — confirma los parámetros de TLS que este archivo tiene que escribir
```

**Antes de tomar un issue, revisar los dos.** El estado de los que lo bloquean se pide en una sola
llamada, `jira.ps1 estado FIN-a FIN-b`, no con un `getJiraIssue` por cada uno. Si alguno no está en
`Por revisar` o `Listo`, no lo tomes: dilo en chat y ofrece el que lo desbloquea. Una dependencia
escrita como "FA-n (entregada, queda en Notion)" ya está cumplida.

Las claves `FIN-n` de `## Depende de` y los `is blocked by` tienen que coincidir uno a uno. **Si no
coinciden, dilo en chat antes de transicionar**: qué claves están solo en el texto y cuáles solo en
los enlaces. Para decidir si el issue está bloqueado se usa la unión de los dos lados; si todos
están cerrados, se sigue. No se corrige ninguno de los dos lados: no se sabe cuál está mal, y lo
arregla la auditoría *Revisar dependencias* de `crear-tareas` desde la raíz.

### Un issue que no es de este repo

El filtro de la consulta no protege cuando el usuario nombra el issue por su clave ("toma la
FIN-n"), y el usuario puede equivocarse de número. **Antes de tomar cualquier issue, comprobar que su
componente sea `Backend`.** Si es `Frontend`, o no tiene componente, no se toma ni se toca: ni
estado, ni comentario, ni rama. Decirlo en chat —el issue es del front y se resuelve desde
`financeapp-fr`— y ofrecer las candidatas de backend. Aunque el usuario insista, desde aquí no se
resuelve: si en realidad necesita algo del API, lo que corresponde es un issue nuevo con componente
`Backend`, no tomar el del front.

Si la clave es de un epic, tampoco se toma: un epic no se implementa. Se ofrecen sus tareas de este
repo, con `parent = FIN-x AND component = Backend AND statusCategory != Done`.

### Un encargo que llega suelto

Si el usuario pide algo para este repo sin clave —un mensaje, lo que salió de una conversación—,
primero se registra como issue en `Por hacer`, con componente `Backend` y el epic de su tema como
`parent` (ver `crear-issue.md`). El texto **tal como llegó** va bajo `## Contexto`, después de la
línea que nombra el epic, y los criterios que se puedan deducir van bajo
`## Criterios de aceptación`. El epic se elige entre los vigentes. **Si ninguno encaja, se
pregunta**: este repo no crea epics, así que un tema nuevo se abre desde la raíz con `crear-tareas`.
Lo que no se pueda deducir también se pregunta antes de seguir. Sus dependencias se resuelven con
*Dependencias y orden* (`crear-issue.md`) antes de crearlo. Después se toma como cualquier otro.

## 2. Tomar

`jira.ps1 transicion FIN-n 31` → `En curso`. Solo un issue de backend en curso a la vez: si
`jira.ps1 buscar 'project = FIN AND component = Backend AND status = "En curso"' -Campos summary`
devuelve otro, avisar y preguntar antes de tomar el nuevo.

Crear la rama desde `dev` en este paso, no al entregar: el change vive en la rama desde el primer
archivo.

```powershell
git checkout dev; git pull --ff-only; git checkout -b feature/fin-<n>-<slug>
```

## 3. Proponer

El change se llama igual que la rama sin el prefijo: `fin-<n>-<slug>`.

1. Si el área a tocar no es evidente o el issue deja dudas de alcance, `/opsx:explore` (skill
   `openspec-explore`) para leer el código y aclarar antes de escribir nada.
2. `/opsx:propose fin-<n>-<slug>` (skill `openspec-propose`). La entrada es el `## Contexto` y los
   `## Criterios de aceptación` de la tarea, más las `## Decisiones` y el `## Fuera de alcance` de
   su epic. Cada criterio termina como requisito con sus escenarios en la delta de spec, o queda
   explicado en `proposal.md` si no es comportamiento. Las decisiones del epic acotan el diseño, y
   lo que el epic deja fuera de alcance queda fuera del change.
3. **Lo que se aclare en el chat se escribe en los artefactos.** Un alcance que solo se dijo
   conversando —como el de FA-55, que no estaba en la tarea— no existe para el siguiente que lea.
4. **`tasks.md` lleva el mapa de archivos**: cada tarea nombra los archivos y los métodos que crea o
   toca, con lo que esta sesión ya exploró. La sesión de implementación no vuelve a explorar: parte
   de ese mapa.
5. `openspec validate fin-<n>-<slug>` en verde.

Las reglas de cada artefacto están en `openspec/config.yaml`; no se repiten aquí.

**Se lee por sección.** `AGENTS.md` ya está cargada en la sesión: no se relee. Las specs
(`transacciones` mide 66k caracteres) se leen por partes: primero los encabezados
(`grep -n "^### Requirement"`, `grep -n "^#"`), después `Read` con `offset` y `limit` sobre la
sección que toca el issue. El código se ubica con `grep -n` antes de leerlo, y se lee el rango, no el
archivo con `cat -n`. Nunca varios archivos concatenados en un solo `cat`, ni un change archivado
entero. Todo lo que se lee aquí se paga en cada turno que queda de la sesión.

El contrato del API está en `docs/api/` del back: un archivo por recurso (`cuentas.md`,
`transacciones.md`…), `generalidades.md` con la autenticación, los errores, CORS y lo que el API
todavía no tiene, y `contrato-api.md` como índice, con una fila por endpoint y el archivo que lo
describe. Se busca la ruta en el índice (`grep -n "/api/accounts" docs/api/contrato-api.md`) y se
lee solo ese archivo; el más grande, `transacciones.md`, mide 24k. `docs/api/` del front es una
copia que se trae con `npm run sincronizar-contrato` y no se edita a mano.

### Skills de dominio por grupo

Cada grupo de `tasks.md` nombra las skills que la implementación tiene que cargar. Sus
descripciones se disparan con "diseñar" o "configurar", no con "implementar FIN-n", así que sin
esa línea no se leen: el constructor manual en todos los beans (corregido en el PR #16) salió de
ignorar `java-architect`, que pedía `@AllArgsConstructor` desde antes del primer commit.

| Si el grupo crea o cambia… | Nombrar |
|---|---|
| Un puerto, caso de uso, adapter, controlador, record de dominio o DTO nuevos, o su forma | `java-architect` |
| `ErrorCodes`, `BadRequestException`, `WebExceptionHandler` o errores en la cadena reactiva | `java-exceptions` |
| `SecurityConfig`, JWT, Basic, CORS o rutas protegidas | `java-security` |
| `LoggingFilter`, `ReactorMdcHook`, MDC o niveles de log | `java-logging` |
| Un bug, un test que falla sin causa evidente o un pipeline `Mono`/`Flux` que no se comporta | `java-debugging` |
| Cualquier archivo de `bruno/` | `bruno-cli` |

`java-testing` no se nombra: la carga siempre la guía de `apply`. Una fila cuenta cuando el grupo
cambia algo de lo que la skill decide, no cuando solo mueve, renombra o junta lo que ya existe
siguiendo una regla de `AGENTS.md`: un refactor que fusiona ports por controller no nombra
`java-architect`, y uno que no toca errores no nombra `java-exceptions`. Si el grupo no toca nada
de la tabla, no nombra ninguna.

## 4. Aprobar

Mostrar en el chat un resumen de la propuesta —el porqué, las decisiones del epic que aplica, los
requisitos con sus escenarios, lo que queda fuera de alcance y la lista de tareas— con la ruta del
change, y **esperar la aprobación explícita del usuario**. Si el epic no tiene `## Decisiones` o
lleva `por-refinar`, decirlo aquí. Los ajustes que pida se hacen con `/opsx:update` (skill
`openspec-update-change`) y se vuelve a mostrar lo que cambió.

Única excepción: que el usuario haya aprobado de antemano en el mismo pedido ("propón e implementa
sin parar"). Aun así el resumen se muestra, y se sigue en la misma sesión con `aplicar.md`: el
usuario pidió no parar.

Esta es la única espera del ciclo. La entrega del paso 8 sigue sin esperar revisión.

**Con el "aprobado", la sesión termina.** Antes, comprobar que lo aclarado en el chat esté escrito
en los artefactos (paso 3.3): es lo único que se pierde. Después, decir en chat:

> Aprobado. Abre una sesión nueva (`/clear`) y escribe `aplica fin-<n>-<slug>`.

El change ya guarda todo lo que la implementación necesita. Lo que entró al contexto para proponer
—issue, epic, specs, código explorado, plantillas de `openspec instructions`— no hace falta en el
apply y se pagaría en cada uno de sus turnos: con la sesión cortada aquí, las tareas medianas y
grandes procesan un 30-45 % menos.
