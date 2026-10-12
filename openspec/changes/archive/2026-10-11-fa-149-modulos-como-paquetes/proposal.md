# Proposal

Origen: FA-149. El encargo llegó por chat el 11-10-2026, justo después de entregar FA-148, y se
registró en el tablero antes de proponer. Texto original:

> los modulos todo tiene el mismo build.gradle y estan dentro de src/main/java/com/oscargabriel/financeapp/
> en esto estarian ls carpetas core, ia, compartido y habria un solo resurces para todos

Decidido en el chat: el límite entre `ia` y `core` queda **por convención**, documentado en
`AGENTS.md`. Nada lo verifica; se descartaron un test que revise los imports y ArchUnit.

## Why

FA-148 separó el backend en cuatro subproyectos de Gradle, cada uno con su `build.gradle` y su
`src/`. El usuario prefiere ver todo en un solo proyecto: un `build.gradle`, un `src/main/resources`
y los módulos como paquetes, cada uno con su hexagonal adentro. Así la estructura se lee de un vistazo
y desaparecen las piezas que solo existían para que los subproyectos funcionaran.

## What Changes

- Vuelve a haber un solo proyecto de Gradle: `settings.gradle` sin `include` y un solo `build.gradle`
  en la raíz, con el plugin de Spring Boot, el umbral del 85 % y las mismas exclusiones.
- El código vuelve a `src/main/java/com/oscargabriel/financeapp/`. Los paquetes de `core`, `ia` y
  `compartido` **no cambian de nombre**: solo cambia la carpeta. Lo que vivía en `app` se reparte:
  - el `main` queda en la raíz, como siempre;
  - `R2dbcSearchPathConfig` y `R2dbcCredentialsCheck` pasan a `compartido/infrastructure/config/`,
    porque la conexión es común a los módulos;
  - los dos puentes pasan al paquete `puente/` de la raíz, junto a los módulos.
- Hay un solo `src/main/resources` y un solo `src/test`, con el `application.yaml` de test en
  `src/test/resources`. Cada test vuelve al paquete de la clase que prueba, y los `*IT` al del módulo
  cuyo endpoint ejercitan.
- Se quitan las piezas que solo existían por los subproyectos:
  - los test fixtures de `compartido`;
  - las dos `ConfiguracionDeSlices`, porque el `main` vuelve a ser visible desde los tests;
  - el `-parameters` manual, porque el plugin de Boot lo pone en el único proyecto;
  - la copia del yaml de test en `app`.
- `Dockerfile`, `.dockerignore`, `verificar-bruno.ps1`, la skill `verificar`,
  `docs/verificaciones-manuales.md` y `AGENTS.md` vuelven a las rutas de un solo proyecto. `AGENTS.md`
  describe los módulos como paquetes y la convención de que `ia` no importa nada de `core`.
- Los yaml sin versionar vuelven a `src/main/resources/` en la máquina local.

No cambia comportamiento. El asistente sigue con sus puertos, sus consultas y su respuesta liviana de
FA-148.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna. El change lleva `skip_specs: true`.

## Impact

- **Código.** Mueve todos los archivos de las cuatro carpetas a `src/`. Solo se renombran los
  paquetes de lo que estaba en `app`.
- **Build.** Un `build.gradle` en vez de cinco. `gradlew bootRun` y `--tests` sin prefijo de módulo
  vuelven a funcionar.
- **Arquitectura.** Se pierde la garantía del compilador de FA-148: si alguien importa algo del core
  desde `ia`, nada avisa.
- **API, base y despliegue.** Sin cambios: una imagen y un servicio, como antes.

## Fuera de alcance

- Verificar el límite entre módulos con un test o con ArchUnit (descartado en el chat).
- Cambiar los nombres de los paquetes de los módulos.
