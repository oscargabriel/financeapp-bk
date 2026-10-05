# Design

## Context

Ver proposal.md, sección Why. Precedencia de skills en Claude Code: enterprise sobre personal y
personal sobre proyecto; las de plugins llevan prefijo y no chocan. `CLAUDE.md` en el directorio de
trabajo o arriba hace que Claude ignore `AGENTS.md`; los archivos de `.claude/rules/` no cuentan para
esa regla y cargan junto a `AGENTS.md`.

## Goals / Non-Goals

**Goals:**
- Cada repo es dueño de las skills que usa, y puede modificarlas sin afectar a los demás.
- Saber de dónde salió cada copia, para compararla cuando cambie el original.

**Non-Goals:**
- Sincronizar copias automáticamente. Se comparan a mano contra el catálogo o contra superpowers.

## Decisions

### Catálogo fuera de `~/.claude/skills/`

Las skills globales se mueven, no se copian: si quedara la global, ganaría sobre la del repo.
Descartado `skillOverrides` en el proyecto: filtra por nombre y apagaría también la copia del repo.

### El origen de cada copia en el frontmatter

`metadata.origen` dice de dónde vino y cuándo (`skills-catalogo 2026-10-04`, o
`superpowers 6.4.2 systematic-debugging`). Cuando el catálogo sea un repo de git, el siguiente que
copie anota el commit.

### systematic-debugging fusionado en java-debugging, no como skill aparte

`java-debugging` ya era la skill de depuración de este stack y delegaba el método. Fusionarlos deja
una sola skill que se dispara ante un bug. Se trae el método completo (las cuatro fases, la regla de
tres intentos, las señales de alarma) y `root-cause-tracing.md` como referencia. Se dejan fuera
`condition-based-waiting.md` (en un stack reactivo los tiempos se prueban con `StepVerifier` y
tiempo virtual, que ya cubre `java-testing`), `defense-in-depth.md` (lo cubre la validación en el
borde de `AGENTS.md`) y los archivos de pruebas de presión de la skill. La licencia MIT de
superpowers va completa en la carpeta de la skill.

### `.claude/rules/claude-code.md` en vez de `CLAUDE.md`

Mantiene `AGENTS.md` como la única guía del proyecto y deja lo de Claude en un archivo que no impide
leerla. Riesgo: una sesión en una versión de Claude Code anterior a 2.1.277, o un `CLAUDE.local.md`
en el repo, deja de ver `AGENTS.md`; la regla lo advierte.

## Risks / Trade-offs

- Los otros repos pierden las skills globales hasta que se les copien → el usuario lo sabe y lo hace
  él; el catálogo conserva todo.
- Las copias divergen del catálogo → el origen en el frontmatter permite compararlas.
- `/init` de Claude Code crea un `CLAUDE.md`, que taparía `AGENTS.md` → la regla de Claude Code lo
  advierte: no correr `/init` en este repo.
