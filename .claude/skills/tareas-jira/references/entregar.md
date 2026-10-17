# Entregar y cerrar (pasos 8 y 9)

Con los pasos 6 y 7 hechos, sin preguntar ni esperar revisión. Todo el paso 8 y el 9 salen en **una**
llamada a `entregar.ps1`; antes se preparan tres archivos.

## Antes del script

1. Si el issue tocó `bruno/`, leer en `bruno-personal/` los requests que su `tasks.md` mandó
   replicar y compararlos con los de `bruno/` en ruta, cuerpo y autenticación. Esa carpeta está en
   `.gitignore`: el PR no la muestra y nadie más va a notar que falta. No se ejecutan, porque
   apuntan a Neon.
2. Si la entrega toca `docs/api/` (`git status --porcelain -- docs/api`), aplicar *El contrato* de
   `crear-issue.md`: el contrato se decide en el change, así que al crear el issue casi nunca se
   sabía qué parte iba a cambiar. Los enlaces y el issue `Frontend` que salgan van en la sección
   `## Contrato` del cuerpo del PR.
3. Escribir en el scratchpad:
   - `commit.txt`: el mensaje del commit. La primera línea es `FIN-<n> <qué entrega>` y es también
     el título del PR; al final, las líneas de atribución que pida la sesión.
   - `pr.md`: el cuerpo del PR, que es también el comentario de cierre en Jira. Se escribe una vez y
     no se repite en el chat. Solo con lo que entiende `md2adf.ps1` (`## `, `- `, `código`):

     ```markdown
     ## Change
     `openspec/changes/archive/<fecha>-fin-<n>-<slug>/`: <el porqué de su proposal.md, en una o dos líneas>
     ## Specs
     - `<capacidad>`: <requisito que se agregó o cambió>
     ## Evidencia
     - `gradlew build`: <n> tests, 0 fallidos; cobertura de línea <x> %, de rama <y> %
     - `verificar-bruno.ps1`: <n> requests, <m> assertions, 0 fallidos; saldos cuadrados
     ## Fuera de alcance
     - <lo que quedó para otro issue, con su clave; o «Nada.»>
     ## Skills cargadas
     - <las del paso 5, o «Ninguna de dominio.»>
     ```

     Los números se copian de la salida del paso 6, nunca estimados. `## Contrato` va solo si aplicó
     el punto 2.
   - `notas.md`: las notas de implementación, que es donde la raíz lee qué se hizo de verdad cuando
     refina el epic:

     ```markdown
     ## Notas de implementación
     - <cada decisión de implementación que cambie algo general del tema: un contrato que otras
       tareas van a consumir, una regla que el resto del tema tiene que respetar, algo pedido que se
       hizo distinto. Si no hay ninguna: «Sin decisiones que cambien el tema.»>
     ```

     Se escriben siempre, aunque no haya decisiones: sin el comentario no se distingue «nada que
     reportar» de un olvido. Van en un comentario y no en la descripción: el MCP solo devuelve la
     descripción en markdown, y reescribirla desde ahí pierde las casillas (ver `design.md` de
     `fin-76-tareas-jira-epics-por-tema`).

## El script

```powershell
pwsh -NoProfile -File ../.claude/scripts/entregar.ps1 -Clave FIN-<n> `
  -Archivos <archivo>,<carpeta>,openspec/changes/archive/<fecha>-fin-<n>-<slug>,openspec/specs/<capacidad> `
  -Mensaje <scratchpad>/commit.txt -Cuerpo <scratchpad>/pr.md -Notas <scratchpad>/notas.md
```

`-Archivos` lleva **solo** los archivos y carpetas del issue, incluido todo `openspec/` que tocó: el
working tree suele tener cambios del usuario que no son de la entrega. Primero con `-Simular`, que
valida la rama, el stage vacío y que cada ruta tenga cambios, y lista lo que va a entrar.

Sin `-Simular`, en este orden: `git add` de esas rutas, commit, push, `gh pr create --base dev`,
`merge-pr-dev.ps1` (merge con merge commit, vuelta a `dev` con `pull --ff-only`, apartando y
reponiendo los cambios locales), comentario de notas, transición a `Por revisar` (51) y comentario de
cierre con el commit, el PR y el cuerpo del PR. Imprime una línea por paso. No usar `gh pr merge`
directo: el permiso del proyecto autoriza los scripts, no el comando suelto. Los PRs contra `main`
**no** se mergean nunca: `merge-pr-dev.ps1` los rechaza. El usuario revisa los PRs después en
GitHub; esto reemplaza en este repo al skill global `entregar`, que nunca mergea.

Si falla, dice en qué paso y qué quedó hecho. Lo que falta se termina a mano en el mismo orden, con
`git`, `gh pr create --base dev --title "<primera línea>" --body-file pr.md`,
`.claude/scripts/merge-pr-dev.ps1 <número>` y `jira.ps1 comentar`/`transicion`; nada de lo hecho
se repite.

## En chat

El enlace del issue y el del PR, en una línea. La evidencia ya está en los dos y no se vuelve a
redactar.
