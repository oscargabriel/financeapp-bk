# Proposal

Origen: FA-99. Llegó como pregunta del usuario el 08-10-2026: «como back y front están en el mismo
ecosistema, cuando yo tengo la aplicación ejecutándose en local esa misma ejecución funciona para
las pruebas de back o tienes que hacer una ejecución independiente en el puerto 8081?». La
respuesta fue que hoy ninguna de las dos, y el usuario pidió registrar como tarea que el script
acepte otro puerto.

## Why

`verificar-bruno.ps1` toma el puerto de `host` en `bruno/environments/local.yml` (8080), se detiene
si está ocupado y siempre levanta su propia app (FA-61). La app que usa el front corre en ese mismo
8080, así que verificar una rama obliga a apagarla. Reutilizarla no es opción: el script existe
para garantizar que Bruno pruebe el código de la rama contra `localhost/financeapp`, y la app del
front puede ser de otra rama o apuntar a Neon. Lo que falta es levantar la instancia propia en otro
puerto. La base compartida no estorba: `-RecargarDatos` solo recrea los usuarios del back.

## What Changes

- `verificar-bruno.ps1` recibe `-Puerto <n>`. Sin él, el puerto sigue saliendo de
  `bruno/environments/local.yml`.
- El script comprueba que ese puerto esté libre, arranca la app con `SERVER_PORT` igual a ese
  puerto y, después del arranque, comprueba que la app escuche ahí.
- Con `-Puerto`, el script pasa a `bru` `host` y `baseUrl` con ese puerto. Si el usuario también
  pasa `host=` o `baseUrl=`, se detiene con código 2.
- `AGENTS.md` (*Comandos*) y la skill `verificar` documentan el parámetro.

## Capabilities

### New Capabilities

Ninguna (`skip_specs: true`): es una herramienta de verificación, no comportamiento del API.

### Modified Capabilities

Ninguna.

## Fuera de alcance

- Reutilizar una app que ya esté corriendo: lo descartó FA-61 y sigue descartado.
- Elegir un puerto libre automáticamente cuando el de `local.yml` esté ocupado: ver `design.md`.
- Un entorno de Bruno nuevo para el 8081: el puerto viaja por `--env-var`, sin duplicar
  `local.yml`.

## Impact

- `.claude/scripts/verificar-bruno.ps1`, `AGENTS.md` y `.claude/skills/verificar/SKILL.md`.
- Sin cambios en `src/`, `bruno/` ni la base.
