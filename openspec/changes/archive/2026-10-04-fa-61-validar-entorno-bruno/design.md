# Design

## Context

El procedimiento actual depende de que el usuario tenga levantada la app correcta. La URL de la base
del perfil `local` es `r2dbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:financeapp}`,
y Spring además acepta `SPRING_R2DBC_URL` como variable de entorno, que reemplaza la propiedad
completa por *relaxed binding*.

## Goals / Non-Goals

**Goals:**
- Que ninguna corrida de verificación pueda escribir en una base que no sea la local de pruebas.
- Que la app contra la que corre Bruno sea la del código de la rama, y no quede viva al terminar.

**Non-Goals:**
- Detectar a qué base apunta una app que ya está corriendo: si el puerto está ocupado, no se sigue.

## Decisions

### Un script versionado, no solo instrucciones en la skill

Es una regla de seguridad: tiene que cumplirse igual en cada corrida y con cualquier agente. Un
párrafo en `verificar` depende de que el agente lo siga paso a paso. Además, el apagado en un
`finally` no se puede expresar como instrucción. PowerShell porque es el shell del proyecto, igual
que `merge-pr-dev.ps1`.

### Validar con el entorno heredado y arrancar sin él

La validación resuelve la URL con las variables de la terminal. Así, si quedó un `DB_HOST` de una
sesión contra Neon, el usuario se entera y la corrida se detiene. El arranque, además, quita esas
variables del proceso hijo. Lo primero avisa del problema; lo segundo es una defensa extra por si
la validación tuviera un hueco.

- **Descartado: solo limpiar las variables y no validar.** Corre siempre bien, pero esconde que la
  terminal apunta a producción, y eso es justo lo que el usuario necesita saber antes de usarla para
  otra cosa.
- **Descartado: validar contra `/api/status`.** El status dice si la base responde, no cuál es.

### Puerto ocupado: detenerse, no matar

Decisión del usuario. Un proceso en el puerto puede ser su app contra Neon con trabajo en curso. El
script informa el PID y el nombre del proceso, y termina con error.

### Apagar el árbol de procesos

`gradlew bootRun` lanza un daemon y un `java` hijo. Matar solo el wrapper deja el `java` con el
puerto tomado, como pasó en FA-60 con el `TaskStop`. Se usa `taskkill /T /F` sobre el PID que lanzó
el script y luego se comprueba que el puerto quede libre.

## Risks / Trade-offs

- **Un arranque lento no llega a `Started` dentro del tiempo de espera.** → El tiempo es un
  parámetro (120 s por defecto). Si se agota, el script apaga lo que levantó y falla mostrando las
  últimas líneas del log.
- **Que el script no esté en `permissions.allow` pide confirmación en cada corrida.** → Es
  deliberado: hoy `bru run` ya la pide.
