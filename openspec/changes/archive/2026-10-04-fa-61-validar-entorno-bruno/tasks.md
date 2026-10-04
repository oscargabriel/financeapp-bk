# Tasks

## 1. Script

- [x] 1.1 Escribir `.claude/scripts/verificar-bruno.ps1` con las validaciones de puerto y base, la
  recarga opcional de datos, el arranque sin `DB_*` ni `SPRING_R2DBC_*`, `bru run` y el apagado en
  `finally`. Verificar que `pwsh -NoProfile -File` lo parsee sin errores.
- [x] 1.2 Probar el puerto ocupado: con un listener ajeno en el 8080, el script termina con error,
  nombra el proceso y el listener sigue vivo después.
- [x] 1.3 Probar la base ajena: con `DB_HOST=ejemplo.neon.tech` en el entorno, el script se detiene
  antes de levantar la app y el 8080 sigue libre. Repetir con `SPRING_R2DBC_URL`.
- [x] 1.4 Probar el camino feliz: `-RecargarDatos` contra la base local, `bru run . -r` en verde con
  los conteos reales, y el 8080 libre al terminar.

## 2. Documentación del procedimiento

- [x] 2.1 `verificar`, sección 2: el script en lugar de `bru run` con la app levantada a mano, y qué
  hacer si se detiene por puerto o por base. Verificar leyendo la skill.
- [x] 2.2 `tareas-notion`, paso 6: lo mismo, en una línea, apuntando a `verificar`.
- [x] 2.3 `AGENTS.md`, sección *Comandos*: el script como forma de correr la verificación contra la
  app real, sin quitar los comandos `bru run` sueltos que sirven para depurar.

## 3. Verificación

- [x] 3.1 `openspec validate fa-61-validar-entorno-bruno --strict` en verde. `gradlew build` no
  aplica: el change no toca `src/`. La corrida de Bruno de 1.4 es la evidencia.
