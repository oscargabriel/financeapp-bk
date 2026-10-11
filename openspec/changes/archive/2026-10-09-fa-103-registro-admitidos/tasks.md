# Tasks

## 1. Esquema y datos

- [x] 1.1 `docs/database/update/20261009_01_registro_lista_admitidos.sql`:
  - la tabla `finance.registration_allowlist` de la decisión 1 de design.md, con su CHECK;
  - el comentario de la tabla, que advierte que una fila de dominio admite el dominio completo;
  - el bloque de reversión comentado.

  El encabezado dice que va **antes** del despliegue y que crea la tabla vacía, así que en
  producción el registro queda cerrado hasta insertar los correos. También dice que no reinicia los
  datos locales: basta con recargar `test-data.sql` y la demo.
- [x] 1.2 La misma tabla en `schema.sql`, después de `USERS`. Verificar con la comparación de
  esquemas de `docs/database/modelo-datos.md`, que tiene que salir sin diferencias.
- [x] 1.3 `test-data.sql`:
  - inserta `@bruno.local` e `invitado@financeapp.local` con `ON CONFLICT DO NOTHING`;
  - el borrado inicial suma `invitado@financeapp.local` a los usuarios que borra;
  - el comentario del borrado dice por qué está ahí.

  `demo-data.sql` inserta `@front.local` con `ON CONFLICT DO NOTHING`. Verificar aplicando el update
  a la base local, corriendo `cargar-datos-local.sql` dos veces seguidas sin error y consultando con
  psql que la tabla tiene exactamente esas tres filas.
- [x] 1.4 Verificar el CHECK con psql: `INSERT ... VALUES ('Ana@Correo.com')` y
  `INSERT ... VALUES ('sin-arroba')` fallan con `ck_registration_allowlist_entry`.

## 2. Bruno en RED

Skills: `bruno-cli`.

- [x] 2.1 En `bruno/auth/`, después de los requests de alta que ya existen:
  - `registro-admitido-exacto.yml`: `"email": "Invitado@FinanceApp.LOCAL"` da 201, con `email`
    `invitado@financeapp.local`;
  - `registro-no-admitido.yml`: `fuera-<timestamp>@no-admitido.local` da 403, con un único error
    `REGISTRATION_NOT_ALLOWED` en `email`;
  - `login-no-admitido.yml`: el mismo correo y la misma contraseña dan 401 `INVALID_CREDENTIALS`,
    así que el usuario no se creó;
  - `registro-subdominio.yml`: `ana-<timestamp>@otro.bruno.local` da 403 `REGISTRATION_NOT_ALLOWED`;
  - `registro-no-admitido-con-cuenta.yml`: `prueba@financeapp.local`, que existe y no está en la
    lista, da 403 y no 409.

  `registro-email-duplicado.yml` usaba `PRUEBA@financeapp.local`, que no está en la lista: con la
  restricción daría 403 en vez del 409 que prueba. Pasa a mandar en mayúsculas el correo que acaba
  de registrar `registro.yml` (`{{emailNuevo}}`, admitido por `@bruno.local`). Así sigue probando el
  409 de un correo admitido y repetido, sin distinguir mayúsculas.

  El `docs` de la carpeta dice que `registro-admitido-exacto` necesita `test-data.sql` recargado
  entre corridas. También dice que `login.yml`, con `prueba@`, que no está en la lista, es la prueba
  de que el login no consulta la lista.
- [x] 2.2 Verificar que fallan hoy por la razón correcta. Con la base del grupo 1 y la app de `dev`,
  correr `bru run auth -r --env local`. Los de no admitido dan 201 o 409 en vez de 403. El exacto ya
  pasa, porque hoy el alta está abierta. Anotar aquí qué pasa y qué falla.

  Hecho en el 8081 (el 8080 lo ocupa otra app), con `verificar-bruno.ps1 -Objetivo auth
  -RecargarDatos -Puerto 8081`: 12 requests, 8 pasan y 4 fallan, y 15 de 23 asserts pasan. Pasan
  desde ya el exacto (201, porque el alta está abierta) y el duplicado reescrito (409). Fallan por
  la razón correcta:
  - `Registro no admitido` y `Registro con un subdominio` dan 201 en vez de 403;
  - `Login no admitido` da 200, porque el alta creó el usuario;
  - `Registro no admitido con cuenta` da 409 `DUPLICATE_RESOURCE` en vez de 403.
- [x] 2.3 `bruno-personal/`: los requests nuevos **no se replican**. Dependen de los usuarios y de
  la lista de `test-data.sql`, que en Neon no existen. Leer `bruno-personal/auth/registro.yml` y
  confirmar que su ruta, su cuerpo y su autenticación siguen valiendo, porque este change no cambia
  ninguno de los tres.

## 3. Dominio y caso de uso

Skills: `java-architect`, `java-exceptions`.

- [x] 3.1 `REGISTRATION_NOT_ALLOWED` en `ErrorCodes`. Puerto de salida
  `RegistrationAllowlistPort.isAllowed(String email)`, que emite `Mono<Boolean>` y recibe el correo
  ya normalizado.
- [x] 3.2 `RegisterUserUseCase`: un constructor explícito con
  `@Value("${registro.admitidos.enabled}")`, y la comprobación entre la moneda y el correo libre.
  TDD en `RegisterUserUseCaseTest`:
  - `rechazaConProhibidoUnCorreoQueNoEstaAdmitido`: 403 `REGISTRATION_NOT_ALLOWED` en `email`, sin
    llamar a `createWithDefaultCategories`;
  - `consultaLaListaConElCorreoNormalizado`: recibe `invitado@financeapp.local` aunque el alta traiga
    `Invitado@FinanceApp.LOCAL`;
  - `noRevelaSiUnCorreoNoAdmitidoYaTieneCuenta`: 403, sin llamar a `existsByEmail`;
  - `laMonedaInexistenteSaleAntesQueElRechazo`: 400 en `baseCurrencyCode`, sin consultar la lista;
  - `conLaRestriccionApagadaNoConsultaLaLista`: 201, y el puerto no se llama;
  - los tests que ya existen siguen en verde, con la lista admitiendo.
- [x] 3.3 `AuthControllerTest`:
  - el 403 sale con el JSON de errores, con `code` y `field`;
  - un cuerpo inválido da 400 en `password` sin llamar al caso de uso.

  Confirmar en `JwtSecurityIT` que `/api/auth/register` sin credencial y con Bearer da 401.

  Hecho. `JwtSecurityIT.elRegistroNoEsAlcanzableSinCredenciales` cubre el alta sin credencial, pero
  nada cubría el alta con Bearer: se agregó `AuthControllerTest.unTokenValidoNoSirveEnElRegistro`.
  Ese test y el del 403 pasan de entrada, porque el handler ya responde con el status de la excepción
  y la cadena Basic ya rechaza el Bearer. Quedan como guardas. El 400 sin llamar al caso de uso ya
  lo probaba `devuelve400ConTodosLosCamposInvalidosSinLlamarAlCasoDeUso`, y su comentario ahora dice
  que por eso no se consulta la lista. Caso de uso: 15 tests, y 3 de los nuevos fallaron antes del
  GREEN. Controlador: 20 tests.

## 4. Persistencia y configuración

Skills: `java-architect`.

- [x] 4.1 `RegistrationAllowlistR2dbcAdapter` con la consulta de la decisión 1 de design.md, con
  bind variables. No lleva test JUnit: los adapters R2DBC están fuera de la suite. Lo verifica Bruno
  en 4.3.
- [x] 4.2 `registro.admitidos.enabled: ${REGISTRO_ADMITIDOS_ENABLED:true}` en
  `src/main/resources/application.yaml`, con un comentario que explica por qué lleva default, y
  `true` en `src/test/resources/application.yaml`. TDD en `CloudRunConfigTest`:
  - `laRestriccionDelAltaQuedaEncendidaSinVariable`;
  - `laRestriccionDelAltaSeApagaPorVariable`.

  El working tree tiene un cambio del usuario en `application.yaml` (`spring.profiles.active` con
  default) que no es de esta tarea: el commit lleva solo el hunk de `registro`.

  Hecho así: el diff del usuario se guardó como parche fuera del repo y el archivo volvió a HEAD
  mientras dura la tarea. El parche se vuelve a aplicar después del commit. `CloudRunConfigTest`
  tiene 18 tests, y los 2 nuevos fallaron con `Required key 'registro.admitidos.enabled' not found`
  antes de agregar la propiedad.
- [x] 4.3 Bruno en verde: con el update aplicado, `test-data.sql` recargado y la app de la rama,
  `bru run auth -r --env local` pasa completo. `bru run . -r --env local` también, porque todas las
  carpetas que registran usan `@bruno.local`.

  Hecho en el 8081 con `verificar-bruno.ps1 -RecargarDatos -Puerto 8081`, sobre la colección
  completa: 338 requests, 268 de 268 tests y 755 de 755 asserts.

## 5. Documentación

- [x] 5.1 `docs/api/contrato-api.md`:
  - en `POST /api/auth/register`: el 403, el orden de las validaciones y que la lista la mantiene el
    dueño;
  - en `POST /api/auth/login`: que no consulta la lista;
  - la fila 403 `REGISTRATION_NOT_ALLOWED` en la tabla de errores.

  Verificar releyendo contra los escenarios de la delta de `usuarios`.
- [x] 5.2 `docs/database/modelo-datos.md`: la tabla en el diagrama y una sección corta sobre las
  entradas de correo exacto y de dominio.
- [x] 5.3 `docs/despliegue.md`:
  - una sección «Correos admitidos» con la sentencia para agregar un correo, la de quitarlo y la de
    listarlos, y la advertencia sobre las filas de dominio;
  - `REGISTRO_ADMITIDOS_ENABLED` junto a `ASISTENTE_PROVEEDOR`, entre las variables que no se
    definen;
  - el paso de aplicar el update antes de promover a `main`.
- [x] 5.4 `AGENTS.md`: `invitado@financeapp.local` en la fila del back de la tabla de usuarios, y
  una línea que dice que `bruno/auth/`, como `bruno/pending/`, solo pasa con `test-data.sql` recién
  cargado.

## 6. Verificación

- [x] 6.1 `.\gradlew.bat build` en verde: anotar el conteo de tests y la cobertura de línea. El
  cambio del usuario en `application.yaml` rompe `CloudRunConfigTest`, así que se aparta con
  `git stash` durante el build y se repone después.
- [x] 6.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde:
  anotar el conteo de requests y de asserts.

  6.1: `BUILD SUCCESSFUL`, 755 tests sin fallos ni omitidos, y 98,32 % de cobertura de línea. 6.2:
  hecho en el 8081 (`-Puerto 8081`), con 338 de 338 requests, 268 de 268 tests y 755 de 755
  asserts.
