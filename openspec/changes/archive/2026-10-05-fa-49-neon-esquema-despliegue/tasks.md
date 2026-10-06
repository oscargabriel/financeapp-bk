# Tasks

Skills: ninguna de la tabla del paso 5 de `tareas-notion` (no toca código ni `bruno/`).

## 1. Neon al día

- [x] 1.1 El usuario hace el snapshot manual del branch de producción en la consola de Neon (Backup
  & Restore → Create snapshot). Verificar: que confirme en el chat que el snapshot existe, con su
  fecha.
- [x] 1.2 Aplicar `20261005_02_cuentas_saldo_inicial_editable.sql` y después
  `20261005_01_categorias_icono_color_obligatorios.sql` en Neon con `psql -X -v ON_ERROR_STOP=1`.
  La conexión sale de Secret Manager (ver design, decisión 4) y la contraseña no aparece en el
  comando. Verificar: `COMMIT` en la salida de cada script y código de salida 0.
  Resultado (05-10-2026 22:53 -05:00): 02 → `CREATE FUNCTION`, `CREATE TRIGGER`, `COMMIT`; 01 →
  cuatro `UPDATE 0`, dos `ALTER TABLE`, `COMMIT`; código 0 en los dos.
- [x] 1.3 Comparar Neon con el `schema.sql` de hoy, construido en un `postgres:18` desechable, con
  el filtro de `restrict ` y de `Dumped from database version`. Verificar: `sin diferencias`, con
  el número de líneas por lado.
  Resultado: con el bloque de `docs/despliegue.md` y `$hasta = '20261005_02'`, Neon 648 líneas ·
  esperado 648 · `sin diferencias`. Antes de aplicar, con `$hasta = '20260905_01'`: 633 · 633 ·
  `sin diferencias`. Que línea base más updates es igual al `schema.sql` de hoy se comprobó aparte
  (649 líneas por lado, filtrando solo `restrict `).
- [x] 1.4 En una sesión de solo lectura, comprobar que los datos no cambiaron (1 usuario, 2
  cuentas, 9 movimientos, 22 categorías, 9 monedas, 22 `default_categories`) y que sigue sin haber
  usuarios `@financeapp.local` ni `@bruno.local`. Después, el usuario comprueba que `/api/status`
  con la Basic de producción responde 200 `UP`. Verificar: los conteos en la salida y la
  confirmación del usuario.
  Resultado: 1 usuario, 2 cuentas, 9 movimientos, 22 categorías, 9 monedas, 22
  `default_categories`; 0 usuarios de prueba. El usuario confirmó `/api/status` en `UP` tras los
  updates.

## 2. Documentación

- [x] 2.1 `docs/despliegue.md`, sección *Base de datos (Neon)*:
  - Proyecto actual: región, versión, base, rol y host directo.
  - Creación desde cero: carga de `schema.sql` y `seed.sql`, la prohibición de `test-data.sql` y la
    consulta que la comprueba.
  - Conexión con variables `PG*` y la sesión de solo lectura.
  - Comparación de esquemas contra Neon.

  Verificar: correr tal como están escritas la consulta de `test-data` y la comparación del
  documento, con el mismo resultado que en 1.3 y 1.4.
- [x] 2.2 Misma sección:
  - Procedimiento para aplicar un `update/` nuevo a producción: orden respecto del despliegue,
    snapshot, `ON_ERROR_STOP`, comparación y registro.
  - Registro de updates aplicados: la línea base desde el primer despliegue, y 02 y 01 el
    05-10-2026.
  - Respaldo del plan gratuito: 6 horas y 1 GB de WAL, 1 snapshot manual, sin snapshots
    programados, con la fecha de consulta y las URLs de Neon.

  Quitar la línea "Pendiente de este documento (FA-49)". Verificar: releer el documento contra los
  seis criterios de FA-49.
- [x] 2.3 `docs/database/modelo-datos.md`: el porqué de no tener Flyway deja de decir "No hay
  despliegue". La condición de *Cuándo volver a mirar esto* se marca como cumplida el 05-10-2026,
  con el `ID` de la tarea nueva que la reevalúa. Verificar:
  `Select-String 'No hay despliegue|PROD_HOST'` sin coincidencias.
- [x] 2.4 `AGENTS.md`, *Base de datos*: una línea que remite al procedimiento de Neon de
  `docs/despliegue.md`. Verificar: el enlace apunta a la sección que existe.

## 3. Tareas nuevas

- [x] 3.1 Crear en el Backlog de Notion:
  - El rol de aplicación con privilegios mínimos en lugar de `neondb_owner`.
  - La región de Neon (`us-east-1`) frente a la de Cloud Run (`europe-west1`).
  - La reevaluación de Flyway.

  Cada una con `## Contexto`, que diga que surgió en FA-49. Verificar: los tres `ID` anotados en
  2.3 y en el comentario de cierre.

## 4. Verificación

- [x] 4.1 `openspec validate fa-49-neon-esquema-despliegue --strict` en verde.
- [x] 4.2 `gradlew build` y `verificar-bruno.ps1 -RecargarDatos` en verde con los conteos reales,
  aunque el change no toque código. Bruno requiere que antes se resuelva el bloqueo de Smart App
  Control sobre `plpgsql.dll` del PostgreSQL local, que resuelve el usuario.
  Resultado: `gradlew build` 539 tests, 0 fallos, 0 omitidos, cobertura de línea 98,04 %
  (1002/1022). Bruno: 217/217 requests, 188/188 tests, 489/489 aserciones. El bloqueo de Smart App
  Control sobre `plpgsql.dll` y `libpq.dll` desapareció tras reiniciar el equipo.
