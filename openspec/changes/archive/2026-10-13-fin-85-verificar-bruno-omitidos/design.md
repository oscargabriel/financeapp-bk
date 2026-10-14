## Context

`bru` 4.2.0 recorre toda la carpeta de la colección al cargarla, no solo la de `-Objetivo`. Cada
archivo que no puede leer lo anota en una lista global (`utils/collection.js`) y, al final de cada
iteración, lo agrega a los resultados como uno más, con `skipped: true`, `path` relativo a la
colección y `error` (`utils/run.js`, `createSkippedFileResults`). La consola lo muestra como
«N Skipped» en el resumen, y el proceso sale con 0.

## Goals / Non-Goals

**Goals:** que el script no salga en verde con requests omitidos, y que diga cuáles fueron.

**Non-Goals:** validar la colección por fuera de `bru`; cambiar qué se considera un fallo de `bru`.

## Decisions

### Leer el reporte JSON, no la consola

El script pasa `--reporter-json build/verificar-bruno/bru-resultados.json` y, después de la corrida,
cuenta los resultados con `skipped` en verdadero, en todas las iteraciones.

Descartado: capturar la salida y buscar «Skipping invalid file» o «Skipped». Obliga a redirigir la
salida de `bru` sin perder lo que se ve en pantalla, depende de textos con colores ANSI que cambian
entre versiones y no distingue bien los omitidos de un request que se llame así. El JSON es la
salida estructurada que `bru` ofrece para herramientas, y el campo `skipped` es el mismo que usa su
propio resumen.

El archivo se borra antes de cada corrida, para no leer el de una corrida anterior si esta no llega
a escribirlo.

### Cualquier `skipped` cuenta, no solo los archivos inválidos

El criterio de FIN-85 pide fallar también con requests `Skipped`. Un `bru.runner.skipRequest` en un
script también deja un escenario sin verificar, y hoy ningún request de `bruno/` lo usa. Si algún
día un request se salta a propósito, ese día se decide cómo excluirlo, con el caso a la vista.

### Código 5, y el de `bru` tiene prioridad

El script ya usa 0, 2, 3 y 4, y cualquier otro código viene de `bru`. Un código propio nuevo, el 5,
distingue «`bru` pasó, pero no corrió todo» de un fallo de `bru`, igual que el 4 distingue los
saldos descuadrados. `bru` usa del 1 al 10 para sus propios errores
(`constants.EXIT_STATUS`), y su 5 es `ERROR_FILE_NOT_FOUND`, por ejemplo con un `-Objetivo` que no
existe. El choque ya existía con el 2, el 3 y el 4 del script. Se acepta porque el script solo
asigna sus códigos cuando `bru` salió en 0, y cada caso imprime su propio mensaje: el de `bru` en su
salida, el del script en rojo al final. Saltar a un número fuera de ese rango (11, 20) evitaría el
choque solo hasta que `bru` agregue otro código, y rompería la numeración que ya documentan
`AGENTS.md` y `verificar`.

Con omitidos y saldos descuadrados a la vez, gana el 5. Una corrida con omitidos no es un `bru` en
verde, y el 4 se define como «saldos descuadrados con `bru` en verde». Los dos mensajes se imprimen
siempre.

Descartado: devolver 1 como un fallo cualquiera de `bru`. Se pierde el motivo, y quien lea solo el
código pensaría que falló una assertion.

### Sin reporte, tampoco hay verde

Si `bru` sale en 0 y el JSON no existe (un cambio de flags en otra versión de `bru`, un disco
lleno), el script sale con 5 y lo dice. Volver a confiar en el código de salida es justo lo que
dejó pasar el error de FIN-58.

### Un omitido fuera de `-Objetivo` también falla

Como `bru` carga la colección entera, un archivo inválido en otra carpeta aparece también al correr
`-Objetivo assistant`. Se deja así: el archivo está roto igual y va a fallar en la corrida completa.
Mejor enterarse en la corrida parcial.

## Risks / Trade-offs

- [El formato del JSON cambia en otra versión de `bru`] → el script lee solo `results[].skipped`,
  `path` y `error`. Si la forma cambia y no encuentra `results`, cae en el caso «sin reporte» y
  falla en vez de pasar.
