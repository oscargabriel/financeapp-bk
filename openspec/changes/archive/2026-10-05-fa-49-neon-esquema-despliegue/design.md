# Design

## Context

Ver `proposal.md` (*Why*) para el estado de Neon el 05-10-2026. Las restricciones que condicionan
el cómo son estas:

- Sin Flyway, ninguna tabla registra qué `update/` tiene cada base. En local da igual, porque la
  base se recrea. En Neon, no.
- Cada `update/` dice en su encabezado si va antes o después del despliegue de su app. El de FA-66
  dice "después"; el de FA-24 dice "antes". Los dos están en `dev` y se promueven juntos a `main`.
- El plan gratuito da 6 horas de historia y 1 snapshot manual.

## Goals / Non-Goals

**Goals:**

- Que Neon quede igual al `schema.sql` de hoy, comprobado con la comparación.
- Que el siguiente `update/` se aplique siguiendo un procedimiento escrito, sin depender de la
  memoria de nadie.

**Non-Goals:**

- Automatizar la aplicación de los `update/`: eso es la reevaluación de Flyway, en una tarea
  nueva.

## Decisions

### 1. Los dos updates se aplican ahora, no en la promoción a `main`

Decisión del usuario (05-10-2026). Aplicarlos ya deja Neon igual a `schema.sql` y saca la base de
la secuencia de la promoción: el merge de `dev` a `main` despliega sin un `psql` intermedio.

El encabezado de `20261005_01` pide aplicarlo **después** de la app de FA-66. El riesgo que nombra
es una app que permita dar de alta categorías sin icono contra una base que lo exige. Esa app es
la de FA-19, que no está en `main`: la app que corre hoy no da de alta categorías y solo copia
`default_categories`, que en Neon tienen icono y color. Con la app de hoy no hay ventana de riesgo.
`20261005_02` agrega un trigger que solo actúa cuando cambia `initial_balance`, y la app de hoy
nunca actualiza cuentas.

Descartado: dejarlos para la promoción, con la secuencia escrita (02 antes del merge, 01 después).
Es correcto, pero convierte cada promoción en un procedimiento de tres pasos con la base en medio,
justo lo que no hay que recordar.

### 2. El registro de updates aplicados vive en `docs/despliegue.md`, no en una tabla de la base

Una tabla con una fila por script, insertada por cada `update/`, es la mitad de Flyway sin su
validación de checksums: habría que mantenerla a mano dentro de cada script, y un script olvidado
la dejaría mintiendo igual que el documento. La comparación de esquemas es lo que de verdad dice
si Neon está al día. El registro solo responde qué se aplicó y cuándo, y para eso basta el
documento.

Descartado: la tabla de control. Se retoma, si hace falta, en la reevaluación de Flyway.

### 3. Antes de cada update, el snapshot manual de Neon

El snapshot se queda en Neon y se restaura desde la consola. El plan gratuito permite uno, así
que el nuevo reemplaza al anterior. Las 6 horas de historia cubren cualquier error que se note el
mismo día. El snapshot cubre lo que se note después.

Descartado: un `pg_dump` completo a un archivo local. Deja los datos financieros reales en el disco
del equipo, fuera de cualquier control, y restaurarlo sobre Neon es más lento que el snapshot.
Queda mencionado como alternativa si algún día hay que sacar los datos de Neon.

### 4. Inspección en solo lectura y aplicación con `ON_ERROR_STOP`

Toda consulta de diagnóstico contra Neon corre con
`PGOPTIONS='-c default_transaction_read_only=on'`: aunque alguien pegue un `UPDATE` por error, la
sesión lo rechaza. Los `update/` ya traen su propio `BEGIN`/`COMMIT`. Se corren con
`-v ON_ERROR_STOP=1`, para que un error aborte el script en vez de seguir con el resto de
sentencias fuera de la transacción.

La conexión sale de Secret Manager (`gcloud secrets versions access latest`) como variables
`PG*`, sin que la contraseña aparezca en la línea de comandos ni en la salida. Son los mismos
valores que lee Cloud Run, así que si el documento conecta, el servicio también. Se descartó leerla
de `application-prod.yaml`: es una copia local que puede quedarse vieja tras una rotación.

## Risks / Trade-offs

- [El registro en `despliegue.md` se queda viejo si alguien aplica un update y no lo anota] → El
  procedimiento termina en la comparación de esquemas, que detecta la diferencia la próxima vez que
  se corra, aunque el registro esté mal.
- [Un update falla a mitad] → La transacción de cada script lo deja sin efecto. Si fallara después
  del `COMMIT`, el script trae su reversión comentada y, en último caso, queda el snapshot.
- [La política de Neon para el plan gratuito cambia] → El documento dice la fecha de la consulta y
  la URL de la fuente.

## Migration Plan

1. El usuario hace el snapshot manual del branch de producción en la consola de Neon.
2. `20261005_02` y después `20261005_01`, con `psql -v ON_ERROR_STOP=1`.
3. Comparación de Neon contra el `schema.sql` de hoy: sin diferencias.
4. Comprobar que siguen 1 usuario, 2 cuentas, 9 movimientos y 22 categorías, y que `/api/status`
   responde 200.

Reversión: el bloque comentado al final de cada script, en orden inverso (01 y después 02), o
restaurar el snapshot.
