# Design

## Context

El servicio ya está desplegado y en uso por una sola persona, con Cloud Run y Neon en sus planes
gratuitos (escala a 0). El change no crea infraestructura: decide qué se acepta de lo que ya hay y
deja escrito cómo se opera.

## Goals / Non-Goals

**Goals:**
- Que `docs/despliegue.md` permita identificar y rotar cualquier secreto sin leer el código.
- Evidencia de cada criterio de FA-48, o la decisión que lo relaja.

**Non-Goals:**
- Endurecer IAM o CORS.

## Decisions

### 1. Una sola cuenta de servicio, con permisos de proyecto

`financeapp@…` ejecuta la app y tiene `secretmanager.secretAccessor`, `run.admin` e
`iam.serviceAccountUser` sobre todo el proyecto. Decisión del usuario del 04-10-2026: se mantiene
así, y los permisos los administra él.

Descartado: dos cuentas, una de ejecución con `secretAccessor` solo sobre los ocho secretos y otra
de despliegue con `run.admin` e `iam.serviceAccountUser`. Es lo que pide el criterio de FA-48, pero
con un único usuario y un único servicio en el proyecto añade una pieza más que mantener.

El precio queda escrito en `docs/despliegue.md`: si la app se compromete, quien la controle puede
leer cualquier secreto del proyecto y redesplegar servicios, no solo leer sus ocho credenciales.

### 2. Los secretos se montan como `:latest`

Cloud Run resuelve `:latest` al arrancar cada instancia. Rotar es agregar una versión y desplegar
una revisión nueva; con escala a 0, una instancia que arranque después también toma la versión
nueva aunque no haya revisión nueva.

Descartado: fijar la versión (`jwt-secret:2`). Hace explícito qué versión usa cada revisión, pero
cada rotación obliga a editar el servicio además de agregar la versión, y con un solo operador esa
trazabilidad no se usa. El riesgo de `:latest` es que una versión mala entra sin desplegar nada; se
mitiga escribiendo en el procedimiento que la versión anterior se deshabilita solo después de
comprobar `/api/status`, no antes.

### 3. La prueba de "sin secreto no arranca" se hace con la imagen en local

Se construye la imagen de `deployment/Dockerfile` y se corre sin `JWT_SECRET`. Es la misma imagen
que despliega el disparador, y el fallo ocurre al resolver la configuración, antes de tocar la base
o la red de Cloud Run.

Descartado: una revisión sin tráfico en el servicio de prod a la que se le quita el secreto. Prueba
el entorno real, pero modifica producción y deja una revisión fallida que hay que limpiar. Decisión
del usuario del 04-10-2026.

### 4. El servicio escala de 0 a 1 instancia

La revisión permitía 20 instancias y el servicio ya tenía un máximo de 1 a nivel de servicio, que es
el que manda (Cloud Run aplica el menor). `application.yaml` y `AGENTS.md` dimensionaban el pool
para 3 × 10. Decisión del usuario del 04-10-2026: máximo 1 en el servicio y en la revisión
(`--max-instances=1`, aplicado por él; revisión `00005`), porque tiene un solo usuario y el plan
gratuito no gana nada con más. `AGENTS.md` y el comentario del pool en `application.yaml` pasan a
1 × 10 = 10 conexiones.

Descartado: 3 instancias, como decía la documentación. Funcionaba igual, pero mantenía un margen
que nadie usa.

El mínimo sigue en 0 (escala a 0, gratis). Un despliegue conserva el máximo: es configuración del
servicio, no de la imagen.

## Risks / Trade-offs

- [Cuenta con permisos amplios] → aceptado por el usuario; escrito en `docs/despliegue.md`.
- [`:latest` puede tomar una versión mala sin despliegue] → orden del procedimiento de rotación.
- [La prueba local no ejercita Cloud Run] → el fallo es de configuración de Spring, independiente
  de dónde corra la imagen.
