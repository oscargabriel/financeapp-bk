# Design

## Context

Ver proposal.md, sección Why. Antes de este cambio el diseño de una tarea lo hacía superpowers
(brainstorming y writing-plans) solo cuando era arquitectónica; las acotadas iban directo a TDD, con
el plan en el chat.

## Goals / Non-Goals

**Goals:**
- Un flujo SDD probado en este repo que se pueda llevar a otro proyecto copiando archivos.
- Lo que se sabe del proyecto en Markdown dentro del repo, legible por el usuario y por cualquier
  agente.
- Lo propio de cada herramienta reducido a adaptadores que se regeneran solos.

**Non-Goals:**
- Construir una herramienta propia de SDD.
- Documentar de entrada todo el sistema.

## Decisions

### OpenSpec como herramienta de SDD

Descartadas:
- **Kiro**: es de pago y obliga a usar su editor.
- **Spec Kit**: su especificar, planear y tareas repite lo que hace superpowers, y genera mucho
  Markdown por cambio.
- **BMAD**: simula un equipo ágil completo; demasiado pesado.
- **superpowers solo**: es spec-first —la spec es la entrada del plan y queda fechada— y no
  mantiene una spec viva.
- **Algo propio**: quedaría obsoleto y lo mantendría solo el usuario.

OpenSpec es spec-anchored (las specs se actualizan con cada change archivado), está pensado para
código existente, lo mantiene una comunidad activa y genera adaptadores para más de 50 agentes,
Kiro entre ellos.

### superpowers apagado en el repo, no desinstalado

OpenSpec y superpowers tienen cada uno su paso de diseño. Con los dos activos habría dos dueños de
la misma decisión, que es la mezcla que el usuario quiere evitar. Se desactiva con `enabledPlugins`
en `.claude/settings.json` del proyecto, así sigue disponible en los demás repos. La disciplina de
ejecución la cubren skills propias (`java-testing`, `java-debugging`, `verificar` y las de dominio).

Descartado: combinarlos con una regla de reparto. Funcionaría hoy, pero los dos se actualizan por
su cuenta y la regla se rompería en silencio.

### No editar los archivos que generan las herramientas

`openspec update` regenera `.claude/skills/openspec-*` y pisa cualquier edición (probado el
04-10-2026 en una carpeta temporal), y superpowers se actualiza desde el marketplace. Todo lo propio
va en archivos que ninguna de las dos toca: `openspec/config.yaml`, `AGENTS.md`, `CLAUDE.md` y
`tareas-notion`.

### AGENTS.md como guía del proyecto

`AGENTS.md` lo leen la mayoría de los agentes, y OpenSpec lo genera con `--tools agents`. `CLAUDE.md`
lo importa con `@AGENTS.md`. Si llega otra herramienta, la guía ya está en su formato y solo falta
`openspec init --tools <herramienta>`.

### Archivar dentro del PR

La documentación de OpenSpec recomienda archivar después del merge para que `specs/` solo avance con
lo que se entregó. Aquí el PR a `dev` se mergea en el acto, así que esa ventaja no existe; archivar
en la misma rama deja código, spec y change archivado en un solo PR.

### Aprobación humana entre propose y apply

Es el punto que hace que la spec mande. El skill de propose de OpenSpec ya dice que el usuario
inicia apply explícitamente. El merge inmediato del PR no cambia: lo que se aprueba es la propuesta,
no el código.

## Risks / Trade-offs

- La guía de `config.yaml` es un consejo, no algo que se haga cumplir, igual que las aprobaciones de
  superpowers → la regla está también en `AGENTS.md` y en `tareas-notion`; si se salta en la
  práctica, se evalúan hooks.
- Al principio `openspec/specs/` está vacío y la documentación viva tarda en servir → es el costo
  aceptado de no documentar lo que no se toca.
- OpenSpec lo mantiene una empresa chica → los artefactos son Markdown en git y sobreviven a la
  herramienta.
- OpenSpec manda telemetría anónima por defecto → se apaga con
  `openspec config set telemetry.enabled false` si el usuario lo decide; es configuración de la
  máquina, no del repo.
