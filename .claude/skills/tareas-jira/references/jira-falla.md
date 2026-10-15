# Cuando Jira falla

Si el servidor MCP `atlassian` no responde o no está autenticado (`/mcp`), detener el ciclo y
decirlo. No implementar a ciegas un issue que no se pudo leer, y no guardar la evidencia en un
archivo local para sincronizarla después.

Si la escritura falla **después** de implementar, no revertir el código: reportar el fallo junto con
el contenido exacto que se iba a escribir, para pegarlo a mano.

## Un timeout no es un fallo

Una escritura que expira **puede haberse aplicado igual**: el servidor sigue procesando después de
que la llamada se rinde. Reintentarla a ciegas duplica el comentario o el issue.

Ante un timeout en cualquier escritura, **leer el issue con `getJiraIssue` antes de reintentar** (con
`comment` en los campos si era un comentario; por JQL sobre `summary` si era una creación), y decidir
con lo que diga: si ya está, no hay nada que reintentar; si quedó a medias, se completa con
`editJiraIssue`.
