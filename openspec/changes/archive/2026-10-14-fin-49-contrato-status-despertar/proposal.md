## Why

FIN-49, del epic FIN-2 (App web: base y experiencia general). Pedido desde la raíz de coordinación el
12-10-2026: el API corre en Cloud Run con mínimo 0 instancias y Neon suspende la base sin uso, así que
hoy el primer request tras un rato quieto es el `POST /api/auth/login` y el usuario espera varios
segundos después de enviar el formulario (`docs/despliegue.md`, sección *Escalado*).

Aplica la decisión del epic «Al abrir el login, el front llama a `GET /api/status` para despertar
Cloud Run y Neon antes de que el usuario envíe sus credenciales» (12-10-2026). La implementa FIN-50
en el front. El epic no tiene `## Fuera de alcance`.

`docs/api/contrato-api.md:426`, en la sección de `GET /api/status`, dice que el endpoint «está pensado
para monitoreo, no para el frontend». Con esa decisión deja de ser cierto.

## What Changes

- `docs/api/contrato-api.md`, sección `GET /api/status`: la frase «Está pensado para monitoreo, no
  para el frontend» se reemplaza por una que diga que sirve para monitoreo y que el front lo llama al
  abrir el login para despertar el servicio y la base tras un arranque en frío, y que ese uso ignora
  la respuesta, sea 200 o 503.
- Ningún cambio de código, configuración, esquema, Bruno ni specs. El endpoint ya consulta Postgres,
  así que una sola llamada despierta la instancia y la base.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna. Ninguna spec de `openspec/specs/` describe `/status` más allá de nombrar su credencial
Basic, y el comportamiento del endpoint no cambia. El change lleva `skip_specs: true`.

## Cobertura de los criterios

| Criterio de FIN-49 | Cómo se cumple |
|---|---|
| 1. La sección de `GET /api/status` dice que el front lo llama al abrir el login para despertar el servicio y la base tras un arranque en frío, y que ese uso ignora la respuesta (200 o 503) | Tarea 1.1 |
| 2. Sin cambios de comportamiento: `gradlew build` y `bru run` en verde | Grupo de verificación |

## Impact

- Solo `docs/api/contrato-api.md`. No toca `bruno/` ni `bruno-personal/`.
- La copia del front (`financeapp-fr/contrato-api.md:343`) tiene la misma frase. La sincroniza
  FIN-50, que ya tiene el criterio «La sección de `GET /api/status` de `contrato-api.md` queda igual
  a la del back después de FIN-49» y ya está enlazado como bloqueado por FIN-49: no hace falta un
  issue `Frontend` nuevo ni un enlace nuevo.

## Fuera de alcance

- Cambiar el endpoint para el caso de despertar: hacerlo público, otro timeout o una respuesta más
  liviana. La credencial Basic ya la pone el proxy del front.
- Subir el mínimo de instancias de Cloud Run o cambiar la suspensión de Neon.
