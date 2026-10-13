# Proposal

Origen: FA-148. El encargo llegó por chat el 11-10-2026 y se registró en el tablero antes de
proponer. En resumen: la aplicación creció y el usuario la quiere separar en módulos, con la
arquitectura hexagonal dentro de cada uno. Son tres módulos: el backend que atiende al front y a la
base, la IA que recibe peticiones HTTP y usa la API key del modelo, y Telegram, que todavía no se
diseña. Para Telegram planteó consultas propias a la base, específicas y sin los datos de más que
traen las consultas actuales.

Decisiones tomadas en el chat al registrar la tarea:

- **Un solo servicio.** Los módulos son subproyectos de Gradle que se ensamblan en una sola app:
  una imagen y un servicio de Cloud Run, como hoy.
- **La IA lee por su cuenta.** Hace sus propias consultas para resolver nombres, para el reporte y
  para el saldo, y por eso su respuesta pasa a ser más liviana que la de `/reports`.
- **La IA escribe por el core.** El alta del movimiento pendiente y la puesta al día de las series
  antes de leer pasan por el core, para que esas reglas vivan en un solo lugar.
- **Telegram no entra.** Su diseño sigue en FA-78.

## Why

Hoy todo vive en un solo proyecto con un solo `domain/`, `application/` e `infrastructure/`. El
asistente comparte modelos, puertos y DTO con el resto, así que cualquier cambio en el reporte o en
el saldo le cambia la respuesta, y nada impide que un caso de uso nuevo se apoye en las piezas
internas de otro. Separar ahora, con un solo módulo además del core, cuesta menos que hacerlo
cuando Telegram exista. Deja además el molde en el que va a entrar Telegram.

## What Changes

- El proyecto pasa a ser multi-proyecto de Gradle con cuatro subproyectos:
  - `core`: finanzas, lo que atiende al front.
  - `ia`: el asistente.
  - `compartido`: núcleo común de errores, seguridad, trazabilidad y reloj.
  - `app`: el `main`, la configuración de arranque y los adaptadores que conectan la IA con el core.
- `core` e `ia` tienen cada uno su `domain/`, `application/` e `infrastructure/`. `ia` no depende de
  `core`: Gradle no le deja ver ninguna de sus clases.
- El asistente lee de la base con consultas propias:
  - las cuentas y categorías activas, para resolver nombres;
  - los movimientos del rango;
  - los totales del periodo y desde siempre;
  - el saldo de cada cuenta.
- El core expone un puerto de entrada nuevo para poner al día las series de un usuario. El
  asistente lo llama antes de leer, como ya hacen `/reports/transactions` y `/reports/balance`.
- **BREAKING** (solo para clientes de `POST /api/assistant/messages`): `transaction`, `report` y
  `balance` dejan de ser los mismos objetos de `/transactions` y `/reports`. Pasan a una forma
  liviana, con nombres de cuenta y categoría en vez de ids y sin los campos que el asistente no
  usa. Los totales siguen coincidiendo con los de `/reports`. El front no consume este endpoint.
- `deployment/Dockerfile`, `.dockerignore`, `verificar-bruno.ps1`, el umbral de JaCoCo y
  `AGENTS.md` se ajustan a la nueva estructura. `cloudbuild.yaml` no cambia: el build se sigue
  lanzando desde la raíz.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `asistente`: cambia la forma de `transaction`, `report` y `balance` en la respuesta, y los
  requisitos de consultar movimientos y saldo, que ya no devuelven el mismo objeto de `/reports`.

## Impact

- **Código.** Todo `src/` se reparte entre `core/`, `ia/`, `compartido/` y `app/`, y los paquetes
  cambian de nombre, de `com.oscargabriel.financeapp.domain…` a `…financeapp.core.domain…`, etc. Los
  tests se mueven con su módulo; los `*IT` y `CloudRunConfigTest` van a `app`.
- **Configuración.** `application.yaml` pasa a `app/src/main/resources/`. Los dos yaml sin
  versionar (`application-local.yaml`, `application-prod.yaml`) también se mueven, a mano, en la
  máquina del usuario.
- **Base de datos.** No cambia el esquema. La IA lee tablas del esquema `finance` que hoy solo lee
  el core.
- **API.** Solo cambia la forma de la respuesta de `POST /api/assistant/messages`. Las demás rutas
  quedan idénticas.
- **Despliegue.** Sigue siendo una imagen y un servicio. Cambian las rutas que copia el Dockerfile
  y el jar que extrae.

## Fuera de alcance

- El módulo de Telegram y sus consultas propias (FA-78).
- Ponerle tope o paginación a la lista de movimientos que devuelve el asistente.
- Separar la base por módulo (esquemas o usuarios propios para la IA).
- Desplegar los módulos como servicios distintos.
