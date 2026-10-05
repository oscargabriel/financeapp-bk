# Design

## Context

Hay dos caminos por los que Spring resuelve `${...}`:

- **`@Value`**: lo resuelve el `PropertySourcesPlaceholderConfigurer` que registra Boot. Este no
  tolera un placeholder sin resolver, ni siquiera uno anidado. Por eso un `@Value("${spring.security.jwt.secret}")`
  sin `JWT_SECRET` aborta con `Could not resolve placeholder 'JWT_SECRET'`.
- **El binder de `@ConfigurationProperties`**: es el que llena `R2dbcProperties`, y deja el
  placeholder sin resolver como texto literal. Así llega `${DB_USERNAME}` al driver como nombre de
  usuario.

`CloudRunConfigTest.noResuelveUnSecretoSinSuVariable` ya prueba que `getRequiredProperty` sobre
`spring.r2dbc.username` falla nombrando `DB_USERNAME` cuando falta la variable. El dato está
disponible: lo único que falta es que algo lo pida de forma estricta durante el arranque.

## Goals / Non-Goals

**Goals:**
- Que falte `DB_USERNAME` o `DB_PASSWORD` aborte el contexto en la creación de beans, antes de
  `DatabaseStartupCheck` y de que Netty abra el puerto, con el mismo mensaje que los demás secretos.

**Non-Goals:**
- Validar el contenido de las credenciales: que no estén vacías o que sean correctas.
- Cambiar cómo Boot construye la `ConnectionFactory`.

## Decisions

### Un bean que lee las dos propiedades con `@Value`

Se agrega `R2dbcCredentialsCheck` (`infrastructure/config/startup/`), un `@Component` cuyo
constructor recibe `@Value("${spring.r2dbc.username}")` y `@Value("${spring.r2dbc.password}")`. No
guarda los valores y no hace nada más con ellos. Que el constructor se pueda invocar ya es la
comprobación. Lleva un comentario con el porqué, porque sin él la clase parece código muerto.

No lleva `@ConditionalOnProperty`, así que no depende de `startup.db-check`. Pide las propiedades de
Spring, no las variables. Por eso el perfil `local`, que fija `username` y `password` en su yaml,
sigue arrancando sin `DB_USERNAME` ni `DB_PASSWORD` en el entorno.

Alternativas descartadas:

- **Comprobarlo en `DatabaseStartupCheck`.** Se apaga con `startup.db-check.enabled=false`, y la
  tarea exige que no dependa de ese chequeo.
- **`EnvironmentPostProcessor` con `setRequiredProperties("DB_USERNAME", "DB_PASSWORD")`.** Fallaría
  todavía antes, pero exige las variables de entorno: rompería el perfil `local`, que da los valores
  en `application-local.yaml`. Además obliga a registrarlo en `META-INF/spring.factories`, otro
  archivo que el `.dockerignore` tiene que dejar pasar.
- **Pasar los valores al driver desde `R2dbcSearchPathConfig`** (`builder.option(USER, …)`). La
  comprobación quedaría con una función real, pero la contraseña pasaría por código propio, y Boot ya
  pone esas opciones. Duplicarlo sería una segunda fuente de verdad para la misma conexión.
- **Mensaje propio** ("falta la variable DB_USERNAME"). Exige capturar la excepción y reinterpretar
  el placeholder. El mensaje de Spring ya nombra la variable y es el mismo que dan los otros cinco
  secretos: un operador que vio uno los reconoce todos.

### Test con `ApplicationContextRunner`

`R2dbcCredentialsCheckTest` levanta un contexto mínimo con `PropertyPlaceholderAutoConfiguration` y
el bean, con `spring.r2dbc.username=${DB_USERNAME}` y `password=${DB_PASSWORD}` como en
`application.yaml`. Sin `PropertyPlaceholderAutoConfiguration`, el contexto resolvería `@Value` con
el resolver tolerante del `Environment` y el test pasaría por la razón equivocada. Los casos:

- Sin ninguna de las dos variables, o sin una sola, el contexto falla y el mensaje nombra la
  variable. La otra credencial lleva un valor reconocible que el mensaje no puede contener.
- Con las dos variables, el contexto arranca. Con valores literales, sin placeholder, también
  arranca: es el caso del perfil `local`.

No hace falta un `@SpringBootTest`: el yaml de test fija `username` y `password` literales, así que
el bean nuevo entra en todos los contextos de integración sin cambios.

## Risks / Trade-offs

- [Un bean sin comportamiento visible que alguien borre por limpieza] → el Javadoc dice por qué
  existe, y su test falla si desaparece la comprobación.
- [Boot podría cambiar el binder y empezar a fallar solo] → el bean sobraría pero no estorbaría.
  Su test seguiría verde.
