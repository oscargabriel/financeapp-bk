# Design

## Context

El perfil `local` fija el puerto con `server.port: ${SERVER_PORT:8080}` en
`application-local.yaml`, que pisa al `${PORT:${SERVER_PORT:8080}}` de `application.yaml`: con ese
perfil, `PORT` no cuenta. Hoy el script no le pasa puerto a la app. Si la terminal tiene un
`SERVER_PORT` distinto del de `local.yml`, el script valida un puerto, la app escucha en otro y
`bru` le pega al primero.

## Goals / Non-Goals

**Goals:**
- Verificar una rama mientras otra app ocupa el 8080, sin tocar esa app.
- Que el puerto que se valida, el de la app y el que usa `bru` sean siempre el mismo.

**Non-Goals:**
- Cambiar las validaciones de base, la recarga de datos o el apagado de FA-61.

## Decisions

### El script fija `SERVER_PORT` siempre, con o sin `-Puerto`

El proceso hijo recibe `SERVER_PORT` con el puerto resuelto, igual que hoy recibe
`SPRING_PROFILES_ACTIVE=local`. Así un `SERVER_PORT` que haya quedado en la terminal no saca a la
app del puerto validado. Además, después de `Started`, el script comprueba que el puerto tenga un
proceso escuchando y, si no, sale con código 3. Ese chequeo no depende de que
`application-local.yaml`, que no está versionado, lea `SERVER_PORT`: si una copia fija el puerto de
otra forma, la corrida falla con un mensaje claro en vez de probar contra la nada.

- **Descartado: fijar `PORT`.** El perfil `local` lo ignora.
- **Descartado: `--args='--server.port=n'` en `bootRun`.** Funciona, pero mezcla el puerto con los
  argumentos de Gradle, y la variable sigue el mismo camino que el perfil.

### `host` y `baseUrl` salen de `local.yml` con el puerto cambiado

Se toman los dos valores del entorno y se les cambia solo el puerto. El esquema, el host y el
base-path siguen siendo los de `local.yml`, sin duplicarlos en el script.

### `host=` o `baseUrl=` junto con `-Puerto`: detenerse

No se sabe qué valor gana `bru` cuando recibe dos `--env-var` con el mismo nombre. Si gana el del
usuario, la colección le pega a otro puerto que la app de la rama. Sin `-Puerto` no cambia nada:
esas variables siguen llegando a `bru` como hoy.

### Puerto ocupado: sigue deteniéndose

- **Descartado: buscar un puerto libre cuando el de `local.yml` esté ocupado.** Cambia la decisión
  de FA-61 de detenerse ante un puerto ocupado. Esa decisión obliga a ver qué proceso lo ocupa, y
  puede ser una app contra Neon que el usuario no sabía que seguía viva. Con `-Puerto`, el cambio de
  puerto lo pide el usuario a sabiendas.

## Risks / Trade-offs

- **La app del 8080 y la de la verificación comparten base.** → `-RecargarDatos` solo recrea los
  usuarios del back (`test-data.sql`), y la app del 8080 no tiene estado propio: ver *Base de datos*
  en `AGENTS.md`.
