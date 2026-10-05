# Tasks

## 1. Comprobación de las credenciales en el arranque

Sin skills de dominio: es configuración de arranque, no toca puertos, errores del API, seguridad ni
logging. La disciplina TDD la da `java-testing`.

- [x] 1.1 RED: `R2dbcCredentialsCheckTest` (`src/test/.../infrastructure/config/startup/`), con un
  `ApplicationContextRunner` que monta `PropertyPlaceholderAutoConfiguration` y el bean, y fija
  `spring.r2dbc.username=${DB_USERNAME}` y `spring.r2dbc.password=${DB_PASSWORD}`. Casos:
  - sin `DB_USERNAME`: el contexto falla, el mensaje contiene `DB_USERNAME` y no el valor de
    `DB_PASSWORD`;
  - sin `DB_PASSWORD`: el contexto falla, el mensaje contiene `DB_PASSWORD` y no el valor de
    `DB_USERNAME`;
  - con las dos variables, o con valores literales sin placeholder, el contexto arranca.

  Verificar que falla porque la clase no existe, y después, con una clase vacía, que los dos casos
  de fallo fallan porque el contexto arranca.
- [x] 1.2 GREEN: `R2dbcCredentialsCheck` (`infrastructure/config/startup/`), un `@Component` con un
  constructor que recibe las dos propiedades por `@Value`, sin guardarlas, y un Javadoc con el
  porqué. Verificar con `.\gradlew.bat test --tests "*R2dbcCredentialsCheckTest"` en verde.
- [x] 1.3 Comprobar que los contextos de integración siguen arrancando con el yaml de test (valores
  literales). Verificar con `.\gradlew.bat test` en verde.

## 2. Documentación de la limitación

- [x] 2.1 `CloudRunConfigTest`: quitar del Javadoc de `noResuelveUnSecretoSinSuVariable` la frase
  que dice que `DB_USERNAME` y `DB_PASSWORD` fallan en `DatabaseStartupCheck`, y apuntar a
  `R2dbcCredentialsCheck`. Verificar leyendo el Javadoc y con la clase en verde.
- [x] 2.2 `AGENTS.md` (*Configuración y arranque*) y `docs/despliegue.md` (debajo de la tabla de
  secretos): las dos variables abortan el arranque con `Could not resolve placeholder` como los
  demás secretos, sin depender de `startup.db-check`. Verificar con
  `Select-String -Path AGENTS.md, docs/despliegue.md -Pattern 'binder'`: solo pueden quedar
  menciones que expliquen por qué existe el bean, no que el fallo llegue tarde.

## 3. Verificación

- [x] 3.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea reales.
- [x] 3.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  los conteos reales de requests, tests y aserciones.
- [x] 3.3 Prueba del escenario de la spec contra la imagen real: `docker build -f
  deployment/Dockerfile` y `docker run` con `SPRING_PROFILES_ACTIVE=prod`,
  `STARTUP_DB_CHECK_ENABLED=false`, los demás secretos con valores ficticios y sin `DB_USERNAME`.
  El contenedor sale con código distinto de 0, sin `Netty started`, y el log nombra `DB_USERNAME`
  sin contener el valor ficticio de `DB_PASSWORD`. Repetir sin `DB_PASSWORD`.
