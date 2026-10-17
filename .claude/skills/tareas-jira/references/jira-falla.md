# Cuando Jira falla

Si el servidor MCP `atlassian` no responde o no está autenticado (`/mcp`), detener el ciclo y
decirlo. No implementar a ciegas un issue que no se pudo leer, y no guardar la evidencia en un
archivo local para sincronizarla después.

Si la escritura falla **después** de implementar, no revertir el código: reportar el fallo junto con
el contenido exacto que se iba a escribir, para pegarlo a mano.

## Un timeout no es un fallo

Una escritura que expira **puede haberse aplicado igual**: el servidor sigue procesando después de
que la llamada se rinde. Reintentarla a ciegas duplica el comentario o el issue.

Ante un timeout en cualquier escritura, **leer el issue antes de reintentar** (`getJiraIssue` con
`comment` si era un comentario; `jira.ps1 buscar` por `summary` o `created >= -1h` si era una
creación), y decidir con lo que diga: si ya está, no hay nada que reintentar; si quedó a medias, se
completa con `jira.ps1`. Con `crear-lote`, lo aplicado está en `<lote>.hecho`; si el último paso
expiró y Jira lo tiene, se agrega a mano su línea (`crear nuevo-1 FIN-n`, `enlace FIN-a FIN-b`)
antes de repetir el comando. Con `entregar.ps1`, la salida dice qué pasos se hicieron.

## Sin `jira.ps1`

Si el script no puede correr (faltan `JIRA_EMAIL` o `JIRA_API_TOKEN`, o falla por algo que no es un
HTTP de Jira), se sigue con el MCP y se avisa en chat. Las descripciones van en ADF: se convierten
con `& ../.claude/scripts/md2adf.ps1 -Path <archivo> -OutDir <carpeta>` (un bloque `=== <clave>` por
descripción) y se pasan a `createJiraIssue` con `contentFormat: "adf"` sin imprimirlas. Los enlaces
con `createIssueLink`, `type: "Blocks"`, `inwardIssue` = el que bloquea, `outwardIssue` = el
bloqueado. Comentarios con `addCommentToJiraIssue` y estados con `transitionJiraIssue`.
