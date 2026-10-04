# Proposal

Origen: FA-58. El encargo llegó en conversación el 04-10-2026, justo después de FA-57: incorporar
en `java-debugging` lo que hace falta de `superpowers:systematic-debugging` con su licencia y su
origen, mover las skills globales a una carpeta aparte (que después será un repo), traer a este repo
las que use, y cambiar `CLAUDE.md` por `AGENTS.md`.

## Why

- Con superpowers apagado en el repo (FA-57), `java-debugging` quedó delegando su método en una
  skill que ya no carga.
- Las skills globales aplican a todos los proyectos por igual. Para modificar una sola en un repo
  hay que copiarla, y la copia **no corre** mientras exista la global con el mismo nombre: Claude
  Code da precedencia a la personal sobre la del proyecto.
- `CLAUDE.md` existía solo para importar `AGENTS.md`. Claude Code 2.1.277 o posterior lee
  `AGENTS.md` directamente cuando no hay `CLAUDE.md`, así que un archivo menos que mantener.

## What Changes

- Las skills de `~/.claude/skills/` pasan a `C:\Users\ozamb\skills-catalogo\`, salvo `synced/`, que
  administra claude.ai.
- `.claude/skills/` del repo recibe las que este proyecto usa: `java-architect`, `java-exceptions`,
  `java-security`, `java-logging`, `java-testing`, `java-debugging`, `bruno-cli` y `verificar`,
  cada una con su origen.
- `java-debugging` incorpora el método de `systematic-debugging` (superpowers 6.4.2, MIT) y su
  técnica de rastreo hacia atrás, con la licencia y la versión de origen.
- `verificar` del repo deja de pedir checkstyle.
- Se borra `CLAUDE.md`. Lo propio de Claude Code pasa a `.claude/rules/claude-code.md`, que carga
  junto a `AGENTS.md`.

## Capabilities

### New Capabilities

Ninguna (`skip_specs: true`): es un cambio de herramientas.

### Modified Capabilities

Ninguna.

## Impact

- Fuera del repo: `~/.claude/skills/` queda solo con `synced/`. **Los otros repositorios dejan de
  ver esas skills** hasta que se les copien las que usen.
- En el repo: `.claude/skills/*`, `.claude/rules/claude-code.md`, `AGENTS.md`, `tareas-notion`;
  se borra `CLAUDE.md`.

## Fuera de alcance

- Crear el repo de git del catálogo y copiar skills a otros repositorios: lo hace el usuario.
- `requesting-code-review` y `receiving-code-review`: nunca se invocaron en este proyecto, y una
  revisión por subagente choca con la regla global "no usar subagentes para revisar el propio
  trabajo". Si se quieren, primero hay que cambiar esa regla.
- Llevar las skills también a `.agents/skills/` (el estándar que leen Codex y otros): Claude Code no
  lee esa carpeta; se hace si se cambia de herramienta.
