# El histórico de Notion

Hasta el 11-10-2026 el tablero estuvo en Notion, con claves `FA-n`. Las `FA-n` que aparecen en el
código, en `AGENTS.md` y en los changes archivados apuntan allí. Las tareas que seguían abiertas se
migraron con la etiqueta `notion-FA-n` (JQL `labels = notion-FA-42`). El servidor `notion` está
declarado en `.mcp.json` pero apagado, para no cargarlo en cada sesión: para leer el contexto de una
`FA-n` entregada, pedir al usuario que lo active desde `/mcp` y usar `notion-search` o
`notion-fetch`. En Notion no se escribe nada.
