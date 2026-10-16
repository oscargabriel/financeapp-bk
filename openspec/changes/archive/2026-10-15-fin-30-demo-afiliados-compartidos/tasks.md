# Tasks

Ningún grupo toca la tabla del paso 5 de `tareas-jira`: no se cargan skills de dominio. No hay
requests de `bruno/` nuevos ni cambiados, así que nada que replicar en `bruno-personal/`.

## 1. Personas y afiliaciones en `demo-data.sql`

- [x] 1.1 Derivar de `:email` el dominio de las personas (`<local>.financeapp.local`) con `\gset`, y definir sus ids, los de sus cuentas, afiliaciones, partes y movimientos enlazados con los prefijos de `design.md`
- [x] 1.2 Ampliar el `DELETE` del principio a las tres personas, por id y por correo; verificar cargando `cargar-datos-local.sql` dos veces seguidas sin errores
- [x] 1.3 Crear a Carlos, Valentina y Andrés con el hash de `claveDePrueba123`, una cuenta `DEBIT` en COP cada uno y la copia de `default_categories`; verificar haciendo login de los tres con `POST /api/auth/login` contra la app local
- [x] 1.4 Insertar las cuatro afiliaciones de *Escenario* con su `created_at`; verificar con `GET /api/affiliations` como `demo@` (dos aceptadas, una recibida, una enviada sin cuenta)

## 2. Lo compartido en `demo-data.sql`

- [x] 2.1 Insertar G1 a G6 con sus partes y todas las filas de la tabla de *Context* de `design.md` para cada estado; verificar `check-saldos.sql` sin cuentas descuadradas
- [x] 2.2 Insertar las transferencias compartidas T1 y T2 con el ingreso pendiente de quien recibe
- [x] 2.3 Agregar al resumen final del script las afiliaciones del usuario y su deuda pendiente por persona

## 3. Comparación con el API

- [x] 3.1 Con la app local (`SPRING_PROFILES_ACTIVE=local`, otro puerto si el 8080 está ocupado) y `demo@`: `GET /api/affiliations/balances`, `GET /api/affiliations/{id}/shares` con Carlos y con Valentina, y `GET /api/transactions/pending` responden lo que dice *Escenario* en `design.md`
- [x] 3.2 Lo mismo como `dev@`: tiene sus propias personas `@dev.financeapp.local`, sin relación con las de `demo@`
- [x] 3.3 Re-ejecutable: aprobar como Carlos su pendiente de G2 y rechazar como `demo@` el de G4, recargar con `cargar-datos-local.sql`, y comprobar que `balances` y `pending` vuelven a lo de *Escenario*
- [x] 3.4 Aislamiento: el conteo de usuarios que no son de la demo (`test-data.sql` y los demás) es el mismo antes y después de recargar la demo

## 4. Documentación

- [x] 4.1 Encabezado de `demo-data.sql`: quiénes son las personas, sus correos por usuario, que se borran y recrean con él y que también se cargan en Neon
- [x] 4.2 Encabezado de `cargar-datos-local.sql`: las personas de `demo@` y de `dev@`
- [x] 4.3 `AGENTS.md`, tabla de usuarios de *Base de datos*: las personas `*@demo.financeapp.local` y `*@dev.financeapp.local`
- [x] 4.4 `docs/despliegue.md`, *La demo en Neon*: las personas que se cargan con `demo@` y que su contraseña es la del repo, como riesgo aceptado el 15-10-2026; verificar que la comprobación de datos de prueba sigue dando `0` con las personas cargadas en local

## 5. Verificación

- [x] 5.1 `.\gradlew.bat build` en verde, con el conteo real de tests
- [x] 5.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con el conteo real de requests y `check-saldos` sin descuadres
- [x] 5.3 `psql -U postgres -d financeapp -f docs/database/cargar-datos-local.sql` corre de punta a punta

## Workflow follow-up

- Archivar el change en la rama y entregar según `tareas-jira`.
