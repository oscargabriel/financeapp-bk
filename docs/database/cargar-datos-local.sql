-- =============================================================================
-- financeapp-bk : carga de todos los datos de la base local
--
-- El comando unico para dejar la base local lista: despues de crearla con
-- schema.sql y seed.sql, despues de vaciarla, o despues de aplicar un update/
-- que modifique tablas. Carga, en este orden:
--   test-data.sql                el escenario de bruno/ (prueba@, inactivo@)
--   demo-data.sql                la demo del front (demo@financeapp.local)
--   demo-data.sql con u = 1      la misma demo para dev@financeapp.local
-- Los tres usuarios entran con la clave claveDePrueba123.
--
-- Re-ejecutable: cada script borra y recrea solo lo suyo. Si uno falla,
-- ON_ERROR_STOP detiene la carga; volver a correrlo lo deja todo bien.
--
-- Solo para la base local; en Neon no se carga.
--
-- Ejecutar:  psql -U postgres -d financeapp -f docs/database/cargar-datos-local.sql
-- =============================================================================

\set ON_ERROR_STOP on

\ir test-data.sql

\set email 'demo@financeapp.local'
\set u 0
\ir demo-data.sql

\set email 'dev@financeapp.local'
\set u 1
\ir demo-data.sql
