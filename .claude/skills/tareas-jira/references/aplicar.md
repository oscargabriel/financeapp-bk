# Retomar, implementar, verificar y archivar (pasos 5 a 7)

## Retomar un change aprobado

Es la entrada de la sesión nueva: «aplica fin-<n>-<slug>», «aplica FIN-n» o «retoma FIN-n».

1. `jira.ps1 buscar 'key = FIN-n' -Campos status,components`: tiene que estar `En curso` y ser
   `Backend`. Si no, no es un retomar: se sigue el ciclo desde el paso 1 (`proponer.md`).
2. Tiene que existir `openspec/changes/fin-<n>-*/`, y la rama actual tiene que ser
   `feature/fin-<n>-<slug>` (`git branch --show-current`). Si es otra, `git checkout` a esa rama;
   si se niega por cambios del working tree, decirlo y parar.
3. Leer `proposal.md`, `design.md` si existe, `tasks.md` y las deltas de `specs/` del change. No se
   relee el issue completo, ni el epic, ni el código explorado: lo que importa de ellos ya quedó en
   el change, y `tasks.md` dice qué archivos y métodos toca cada tarea.
4. Si `tasks.md` tiene casillas marcadas, se sigue desde la primera sin marcar.

## 5. Implementar

`/opsx:apply` (skill `openspec-apply-change`) recorre `tasks.md` y marca cada casilla. La disciplina
dentro de cada tarea no la da OpenSpec: la da la guía de `apply` de `openspec/config.yaml`, que
también dice cómo leer y editar sin gastar de más, y estas skills:

- `java-testing` para el ciclo RED-GREEN-REFACTOR y la ubicación de los tests por capa hexagonal.
- **Las de dominio que nombra cada grupo de `tasks.md`, y solo esas**, con `Skill` antes de su
  primera tarea. Un grupo que no nombra ninguna no carga ninguna. Si al implementar aparece algo de
  una skill que el grupo no nombró —un `ErrorCodes` nuevo que nadie previó—, se carga en ese
  momento. Las reglas de la skill ceden ante `AGENTS.md` y `.claude/rules/` cuando chocan.
- Si el issue agrega o cambia endpoints, el request de `bruno/` con su bloque `runtime.assertions`
  se escribe **en el mismo ciclo RED**, fallando antes de que exista el endpoint. Ver la memoria
  `convencion-coleccion-bruno`.
- Si apply revela que el diseño no alcanza, se para y se corrige el change con `/opsx:update`; no
  se implementa algo distinto de lo aprobado sin decirlo.

### En tramos

Un change de más de 3 grupos en `tasks.md`, o uno que parte o junta archivos grandes, se aplica en
tramos: cada turno de la segunda mitad de una sesión larga relee todo lo anterior. Al cerrar el
tercer grupo (y cada tres después), con sus casillas marcadas y sus tests en verde, se para y se
dice en chat:

> Grupos 1 a 3 listos, sin commit en la rama. Abre una sesión nueva (`/clear`) y escribe
> `aplica fin-<n>-<slug>`.

*Retomar* sigue desde la primera casilla sin marcar. El trabajo queda en el working tree de la rama;
no se commitea a medias.

## 6. Verificar

`.\gradlew.bat build` — la suite completa más el umbral de cobertura, que `test` no exige. No
necesita Docker ni base.

`pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos`. El script comprueba
que el puerto esté libre y que la base sea la local antes de levantar la app; si se detiene, ver
`verificar`. Va **siempre**, no solo cuando el issue toca endpoints. Es la única capa que ejerce
el SQL de las vistas y la base real, así que un issue que cambie una consulta, el esquema o el
escenario de datos sin pasar por aquí se cierra sin verificar. Si `bru` no está en el PATH, eso es
un bloqueo que se reporta, no un paso que se omite.

Las salidas se recortan (`Select-Object -Last 30`); los conteos de las dos corridas se anotan para
el cuerpo del PR del paso 8. Este proyecto **no tiene checkstyle ni linter**: no reportes un paso
de lint que no corrió.

**Sin verde no se pasa al paso 7.** No se cierra un issue con tests rojos ni con una corrida de
`bru` que no se ejecutó. Todas las casillas de `tasks.md` tienen que estar marcadas.

## 7. Archivar

`/opsx:archive fin-<n>-<slug>` (skill `openspec-archive-change`) **en la misma rama**, antes del PR:
la delta se fusiona en `openspec/specs/` y el change pasa a
`openspec/changes/archive/<fecha>-fin-<n>-<slug>/`. Se archiva dentro del PR porque aquí el merge a
`dev` es inmediato; esperar al merge, como recomienda OpenSpec, no protegería nada.

Revisar el diff de `openspec/specs/`: es lo que va a leer el siguiente. Si la delta toca una
capacidad que describía una memoria de Claude (`contrato-alta-lote`, por ejemplo), esa memoria se
borra en este paso y su índice se actualiza: la spec la reemplaza.

Después, seguir con `entregar.md`, en la misma sesión.

## 10. Descubrimientos — un issue, un resultado

**Nunca agrandes el issue en curso.** Si durante la implementación aparece algo que sus criterios de
aceptación no cubren —parametrizar los mensajes del bot, un filtro de seguridad contra inyección,
cualquier pieza que sea un feature en sí misma— se crea un issue nuevo, no se mete en este.

El discriminador es una sola pregunta:

> **¿Puedo dejar este issue en verde y cerrado sin eso?**

- **Sí** → issue nuevo en `pendiente` (ver `crear-issue.md`): componente del repo donde se resuelve,
  tipo correspondiente, prioridad `Medium` salvo que sea un bug o una urgencia, y bajo
  `## Contexto` **por qué surgió y en qué issue apareció**. El epic es el de la tarea en curso,
  también cuando el issue es del otro repo o es un bug. Si de verdad es de otro tema, se usa el epic
  de ese tema; si ninguno encaja, se pregunta, porque este repo no crea epics. Sus dependencias
  se resuelven con *Dependencias y orden* antes de crearlo: haber surgido del issue en curso no es
  una dependencia.
- **No** —el código no compila, la suite no pasa, el endpoint queda roto sin eso— es parte del issue
  actual, y se dice explícitamente en el comentario de cierre.

Decir siempre en chat qué issues se crearon, con el orden de ejecución que queda. Que un tema crezca
mientras se implementa es la regla funcionando, no un desvío.

Un mock o stub acordado se registra igual: el issue en Jira es el registro. En la memoria solo va
la decisión de diseño, si la hubo.
