# Design

## Context

FA-148 dejó cuatro subproyectos de Gradle, y su design (decisión 1) descartó los paquetes en un solo
proyecto porque el límite fallaría en un test y no al compilar. El usuario prefiere ahora un solo
proyecto y decidió que el límite quede por convención. Este design registra ese cambio y dónde queda
lo que vivía en `app`.

## Goals / Non-Goals

**Goals:**

- La misma suite, con el mismo número de tests y la misma cobertura, sobre un solo proyecto.

**Non-Goals:**

- Tocar la lógica de cualquier módulo.

## Decisions

### 1. Los módulos son paquetes y el límite es una convención

Los paquetes `com.oscargabriel.financeapp.{core,ia,compartido}` se quedan como están; solo cambia la
carpeta. La regla es la misma de FA-148: un módulo de negocio no importa otro, y `ia` pide lo que
necesita del core por sus puertos de salida. Desde ahora la sostiene `AGENTS.md`, no el compilador.

Descartadas (por decisión del usuario en el chat):

- **Un test que lea las fuentes de `ia/` y falle si importan `core`.** No agrega dependencias, pero el
  usuario no lo quiso.
- **ArchUnit.** No lo gestiona el BOM de Boot.

Esto revierte la decisión 1 de FA-148 y deja sin objeto su decisión 7 (test fixtures y configuración
de slice) y la parte de la decisión 8 que existía solo por los subproyectos. La cobertura vuelve a
medirse sobre un proyecto, que de hecho ya era un número agregado.

### 2. Dónde queda lo que era de `app`

- **Conexión R2DBC** (`R2dbcSearchPathConfig`, `R2dbcCredentialsCheck`): a
  `compartido/infrastructure/config/`. La usan los dos módulos de negocio, y es exactamente el tipo
  de pieza transversal que ya vive ahí.
- **Puentes**: a `com.oscargabriel.financeapp.puente`, un paquete de la raíz junto a los módulos.
  - Dentro de `ia` romperían la convención, porque importan el core.
  - Dentro de `core` harían que el core conozca los puertos de `ia`.
  - Fuera de los dos, se ve que son el único lugar donde dos módulos se tocan.
- **Tests de `app`**:
  - cada `*IT` va al paquete web del módulo cuyo endpoint ejercita (`JwtSecurityIT` y
    `WebExceptionHandlerIT` a `compartido`, el resto a `core`);
  - `CloudRunConfigTest` y los tests de la conexión van a `compartido/infrastructure/config/`;
  - `FinanceappBkApplicationTests` va a la raíz.

## Risks / Trade-offs

- **[Riesgo] Alguien importa el core desde `ia` y nadie lo nota.** → Queda escrito en `AGENTS.md`.
  Es el precio aceptado en el chat.
- **[Riesgo] Mover cientos de archivos esconde un cambio.** → Un commit solo con renombres; `git show
  -M` lo comprueba.
