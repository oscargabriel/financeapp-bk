# Tasks

Ningún grupo toca la tabla del paso 5 de `tareas-jira`: no se cargan skills de dominio.

## 1. Tasa de respaldo en `demo-data.sql`

- [x] 1.1 Ensayar el fallo antes del cambio: en una base desechable `financeapp_fin45`, `schema.sql` + `seed.sql` + `demo-data.sql` aborta con `FX001`
- [x] 1.2 Insertar en `demo-data.sql`, antes de los movimientos, la tasa `USD→COP` 4000 `MANUAL` del día 1 de hace seis meses (Bogotá) solo si el par no tiene ninguna; verificar recreando `financeapp_fin45` y cargando `schema.sql` + `seed.sql` + `demo-data.sql` sin errores, una sola fila en `exchange_rates` y `check-saldos.sql` sin cuentas descuadradas. Borrar la base al terminar
- [x] 1.3 Verificar que con tasas previas no inserta nada: `cargar-datos-local.sql` sobre la base local deja en `exchange_rates` exactamente las tres filas de `test-data.sql`
- [x] 1.4 Reescribir el encabezado de `demo-data.sql`: qué tasa trae, que solo la inserta si el par no tiene ninguna, y que correrlo contra Neon es seguro

## 2. Documentación

- [x] 2.1 `docs/despliegue.md`, *La demo en Neon*: la tasa real previa pasa a recomendada, y sin ella el script usa la de respaldo; verificar releyendo la sección
- [x] 2.2 `AGENTS.md`, *Base de datos*: quitar «Cargarla en una base nueva exige antes una tasa USD→COP (FIN-45)» o reescribirla como regla vigente

## 3. Verificación

- [x] 3.1 `.\gradlew.bat build` en verde, con el conteo real de tests
- [x] 3.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con el conteo real de requests y `check-saldos` sin descuadres

## Workflow follow-up

- Archivar el change en la rama y entregar según `tareas-jira`.
