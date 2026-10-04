# Tasks

## 1. OpenSpec en el repo

- [x] 1.1 `openspec init . --tools claude --language es` y verificar que existan `openspec/config.yaml` y las skills `openspec-*`
- [x] 1.2 Escribir el contexto y las reglas en `openspec/config.yaml`, y verificar con `openspec instructions tasks --change fa-57-flujo-sdd-openspec --json` que las reglas lleguen al artefacto

## 2. Guía del proyecto común a cualquier agente

- [x] 2.1 Pasar la guía de `CLAUDE.md` a `AGENTS.md` con la sección del flujo SDD, y verificar que `AGENTS.md` no mencione herramientas de Claude
- [x] 2.2 Dejar `CLAUDE.md` con `@AGENTS.md` y solo lo propio de Claude Code

## 3. Ciclo de tareas

- [x] 3.1 Desactivar superpowers en `.claude/settings.json` del proyecto y permitir los comandos de `openspec`; verificar que el JSON siga siendo válido
- [x] 3.2 Reescribir los pasos de `tareas-notion` para el ciclo tarea → change → aprobación → apply → verificar → archive → entregar → cerrar, y verificar que no queden referencias a superpowers

## 4. Verificación

- [x] 4.1 `openspec validate fa-57-flujo-sdd-openspec` en verde
- [x] 4.2 `gradlew build` en verde: el cambio no toca `src/`, pero la suite confirma que nada del classpath se rompió
- [x] 4.3 Archivar el change y verificar que quede en `openspec/changes/archive/`
