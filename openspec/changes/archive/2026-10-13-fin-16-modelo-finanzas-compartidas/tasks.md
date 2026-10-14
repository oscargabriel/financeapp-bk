# Tasks

## 1. Update y esquema

Skills: ninguna. No toca Java ni `bruno/`.

- [x] 1.1 `docs/database/update/20261013_01_finanzas_compartidas.sql`, siguiendo el Migration Plan
  de design.md:
  - las tablas `affiliations` y `expense_shares` (decisiones 1, 3 y 4), con sus índices, el trigger
    `set_updated_at` de cada una y los comentarios de tabla y columnas;
  - las columnas `shared_by_user_id` y `share_id` en `transactions`, con el índice parcial por
    `share_id` (decisión 5);
  - `account_id` nullable, `ck_transactions_shape` reemplazado y `ck_transactions_account` nuevo
    (decisión 6);
  - el `COMMENT` de `transactions.status` al día.

  El encabezado dice que va en cualquier orden respecto a la app y que reinicia los datos locales, y
  cierra con el bloque de reversión comentado. Verificar aplicándolo a la base local con
  `psql -v ON_ERROR_STOP=1`.
- [x] 1.2 Lo mismo en `schema.sql`, con las dos tablas nuevas en el orden que exigen sus FK
  (`expense_shares` después de `transactions`). Verificar con la comparación de esquemas de
  `modelo-datos.md`: `sin diferencias`.
- [x] 1.3 Comprobar las restricciones con un script desechable en el scratchpad, dentro de
  `BEGIN … ROLLBACK` contra la base local. Pasan:
  - un gasto `PENDING` sin cuenta ni categoría;
  - un ingreso `PENDING` sin cuenta con `shared_by_user_id`;
  - aprobarlo con un `UPDATE` que pone cuenta, categoría y `CONFIRMED`, y el saldo de la cuenta se
    mueve por el monto;
  - borrar el gasto de origen deja `source_transaction_id` en `NULL` y la parte intacta;
  - borrar a un usuario se lleva sus afiliaciones y partes y deja en `NULL` el `shared_by_user_id`
    de los movimientos del otro.

  Fallan con la restricción esperada:
  - un `CONFIRMED` sin cuenta;
  - un gasto `CONFIRMED` sin categoría;
  - una `TRANSFER` sin cuenta;
  - A→B abierta más B→A;
  - dos solicitudes abiertas de A al mismo correo no registrado (dos a correos distintos pasan);
  - una solicitud sin destinatario en `ACCEPTED`;
  - una parte con `payer_id = debtor_id`;
  - una parte `PAID` sin `resolved_at`.

  Verificar con la salida de psql, que va como evidencia al PR.
- [x] 1.4 `cargar-datos-local.sql` dos veces seguidas sin error sobre la base actualizada. Verificar
  con `psql -v ON_ERROR_STOP=1`.

## 2. Documentación

Skills: ninguna.

- [x] 2.1 `docs/database/modelo-datos.md`:
  - el diagrama con las dos tablas, las dos columnas de `transactions` y sus relaciones;
  - una sección *Finanzas compartidas* con el resumen de las decisiones 1 a 7 y la tabla de quién
    apunta a qué;
  - *Movimientos pendientes* al día: cualquier origen, sin cuenta ni categoría, y el aviso de que el
    código que los lea tiene que tolerarlo;
  - «Personas afiliadas y finanzas compartidas entre usuarios» fuera de *Pendiente para próximas
    iteraciones*.

  Verificar que el bloque `mermaid` siga siendo un `erDiagram` válido (los mismos tipos de
  relación que ya usa) y releyendo la sección contra `schema.sql`.
- [x] 2.2 Crear en Jira la tarea del descubrimiento: `drop.sql` no lista lo agregado desde
  septiembre. Epic FIN-74, componente `Backend`, estado `pendiente`. Verificar con su clave en el
  comentario de cierre.

## 3. Verificación

- [x] 3.1 `.\gradlew.bat build` en verde, con el conteo real de tests.
- [x] 3.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  los conteos reales de requests y asserts, y `check-saldos.sql` sin cuentas descuadradas.
