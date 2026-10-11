# Tasks

Skills: ninguna de la tabla del paso 5 (no toca código Java ni `bruno/`).

## 1. Script

- [x] 1.1 Parámetro `-Puerto` (`[ValidateRange(1, 65535)]`). Sin él, el puerto sale de `host` en
  `local.yml`, como hoy. Verificar: con otra app en el 8080, `-Puerto 8081` pasa la validación de
  puerto, y sin `-Puerto` se detiene con código 2 nombrando el PID.
  Resultado: con la app del IDE en el 8080 (PID 18976), sin `-Puerto` da código 2 nombrando el PID y
  sugiriendo `-Puerto`; `-Puerto 70000` lo rechaza el `ValidateRange`. El mensaje de puerto ocupado
  ahora menciona `-Puerto`.
- [x] 1.2 El proceso hijo recibe `SERVER_PORT` con el puerto resuelto, y después de `Started` el
  script sale con código 3 si nadie escucha en ese puerto. Verificar: con `SERVER_PORT=9999` en la
  terminal y sin `-Puerto`, la app levanta en el 8080 y la corrida pasa.
  Resultado: con el 8080 ocupado se probó con `-Puerto 8082`: con `SERVER_PORT=9999` en la terminal la
  app levantó en el 8082 y `system/` pasó (4/4 requests). Con `SPRING_APPLICATION_JSON` forzando el
  8083, el script salió con código 3 nombrando los dos puertos y dejó libres el 8082 y el 8083. Para
  eso busca en el log el puerto real de Netty: el `taskkill` del wrapper no alcanza al `java` hijo
  del daemon. Esto apareció al implementar.
- [x] 1.3 Con `-Puerto`, el script pasa a `bru` `host` y `baseUrl` de `local.yml` con el puerto
  cambiado, y se detiene con código 2 si el usuario también pasó `host=` o `baseUrl=`. Verificar:
  `-Puerto 8081 host=http://localhost:8080` da código 2 sin levantar la app.
  Resultado: código 2 antes de la validación de base.

## 2. Documentación

- [x] 2.1 `AGENTS.md`, *Comandos*: `-Puerto` para verificar con otra app en el 8080.
- [x] 2.2 `.claude/skills/verificar/SKILL.md`: `-Puerto` como salida cuando el puerto de `local.yml`
  está ocupado por una app que el usuario quiere mantener.

## 3. Verificación

- [x] 3.1 Sin `-Puerto` y con el 8080 libre, `verificar-bruno.ps1 -RecargarDatos` en verde con los
  conteos reales.
  Resultado: 286/286 requests, 238/238 tests, 652/652 aserciones, con la app en el 8080
  (`Netty started on port 8080`).
- [x] 3.2 Con una app del perfil `local` en el 8080, `-Puerto 8081 -RecargarDatos` en verde con los
  conteos reales, y `GET /api/status` del 8080 en 200 al terminar.
  Resultado: con la app del IDE en el 8080, 286/286 requests, 238/238 tests y 652/652 aserciones. El
  log muestra `Netty started on port 8081` y los requests a `localhost:8081`. Al terminar, el 8081
  quedó libre y `/api/status` del 8080 respondió 200.
- [x] 3.3 `gradlew build` en verde con los conteos reales.
  Resultado: 661 tests, 0 fallos, 0 omitidos; cobertura de línea 98,28 % (1146/1166). Corrido con el
  `application.yaml` de git: el working tree tiene un default en `spring.profiles.active`, ajeno a la
  tarea, que hace fallar `CloudRunConfigTest`. Con autorización del usuario se apartó durante el
  build y se repuso igual.
- [x] 3.4 `openspec validate fa-99-verificar-bruno-puerto --strict` en verde.
