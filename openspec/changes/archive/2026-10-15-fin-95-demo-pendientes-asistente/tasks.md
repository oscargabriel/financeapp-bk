# Tasks

Ningún grupo toca la tabla del paso 5 de `tareas-jira`: no se cargan skills de dominio. No hay
requests de `bruno/` nuevos ni cambiados, así que nada que replicar en `bruno-personal/`.

## 1. Pendientes del asistente en `demo-data.sql`

- [x] 1.1 Antes de tocar el script, con la demo de `dev` cargada (`cargar-datos-local.sql`) y la app local, guardar en el scratchpad las respuestas de `GET /api/accounts`, `GET /api/monthly-spending` del mes en curso y del anterior, y `GET /api/transactions/pending` de `demo@`: son la línea base de 2.1
- [x] 1.2 Definir los dos ids de grupo con el prefijo `e000000<u>` e insertar los cuatro movimientos de *Escenario* en `design.md`, con origen `TELEGRAM`, `status` `PENDING`, `created_at` del mensaje y sin `shared_by_user_id`; verificar cargando `cargar-datos-local.sql` dos veces seguidas sin errores
- [x] 1.3 Agregar al resumen final del script los pendientes propios del usuario por grupo (movimientos y monto)

## 2. Comparación con el API

- [x] 2.1 Con la app local y `demo@`: `GET /api/transactions/pending` trae los cuatro con `"origin": "TELEGRAM"`, el `groupId` de su grupo y fechas de los últimos días, junto a los compartidos de FIN-30; `GET /api/accounts` y `GET /api/monthly-spending` (mes en curso y anterior) son iguales a la línea base de 1.1; `check-saldos.sql` sin cuentas descuadradas
- [x] 2.2 `GET /api/transactions/pending?sharedBy=carlos@demo.financeapp.local` no trae ninguno de los cuatro; `POST /api/transactions/groups/{A}/approve` responde 200 y mueve los saldos de la tarjeta, la nómina y Nequi; `POST /api/transactions/groups/{B}/reject` responde 204
- [x] 2.3 Re-ejecutable: tras 2.2, recargar con `cargar-datos-local.sql` y comprobar que `GET /api/transactions/pending` vuelve a traer los dos grupos sin confirmar y los saldos vuelven a la línea base
- [x] 2.4 Lo mismo como `dev@`: sus dos grupos, con los ids de `u = 1`

## 3. Documentación

- [x] 3.1 Encabezado de `demo-data.sql`: los dos grupos pendientes del asistente, para la pantalla de pendientes del front y su filtro por persona (FIN-71, FIN-73), y que la recarga los deja sin confirmar; el comentario de *Cuentas* ya no dice que Nequi no tiene movimientos

## 4. Verificación

- [x] 4.1 `.\gradlew.bat build` en verde, con el conteo real de tests
- [x] 4.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con el conteo real de requests y `check-saldos` sin descuadres
- [x] 4.3 `psql -U postgres -d financeapp -f docs/database/cargar-datos-local.sql` corre de punta a punta

## Workflow follow-up

- Archivar el change en la rama y entregar según `tareas-jira`.
