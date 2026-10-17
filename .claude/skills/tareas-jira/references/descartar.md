# Cuando un issue queda obsoleto

Un issue muere de dos formas: **otro lo sustituye**, o **una decisión posterior vuelve sus criterios
irrealizables**. Jira no tiene un estado de descarte: se marca con la etiqueta `descartada` y la
transición a `Listo`, que lo saca de las consultas activas sin borrarlo. Sigue buscable
(`labels = descartada`), enlazable y se revierte con la transición de vuelta.

Hazlo por tu cuenta, sin pedir permiso, y dilo en chat. Antes, deja un comentario con
`jira.ps1 comentar FIN-n -Archivo <scratchpad>/descartada.md`:

```markdown
## Descartada

**Fecha** — DD-MM-AAAA.
**Qué la sustituye** — la clave del issue que la reemplaza, o la decisión que la dejó sin sentido,
con su fecha y dónde está escrita.
**Dónde quedó cubierto lo que pedía** — el archivo, el test o el request de Bruno concreto. Si no
quedó cubierto en ninguna parte, decirlo con esas palabras: es un hueco, no un issue muerto.
```

Los tres campos son obligatorios. Sin el tercero, descartar un issue equivale a perder trabajo
pendiente sin que nadie lo note. Después, con `jira.ps1`: `etiquetas FIN-n -Agregar descartada`
(conserva las que tenga) y `transicion FIN-n 41`. Sin el script, ver `jira-falla.md`.

**El único caso que no descartas tú** es un issue en el que el usuario ya invirtió decisiones y
quiere revisar: si dudas, deja el estado y pregunta.

Se descarta directamente, en vez de proponerlo en chat, porque un aviso que vive en una
conversación se pierde al cerrarla mientras el issue sigue en el tablero: un aviso que solo se dice
una vez no es un estado.
