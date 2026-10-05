# Datos de prueba: Object Mother + Builder

Regla base: no construyas entidades de prueba con `new` inline. Siempre vía un Builder con
defaults sensatos, expuesto por un Object Mother con nombre de intención. Es lo que evita que un
campo nuevo obligatorio en el modelo obligue a tocar cientos de tests.

## Por qué la combinación (y no uno solo)

- **Object Mother solo** (`Usuarios.unUsuarioActivo()`, `unUsuarioSuspendido()`, ...) es muy
  legible pero explota en variantes a medida que crecen los escenarios. No escala.
- **Builder solo** escala y resiste cambios del modelo, pero sin un punto de entrada con
  intención cada test arranca con ruido (`new XxxBuilder().conNombre(...)...`).
- **Mother que devuelve Builder** toma lo mejor de ambos: el Mother da el punto de entrada legible
  con defaults completos y válidos; el Builder deja sobreescribir **solo** la dimensión que ese
  test prueba.

```java
unUsuario()                       // Mother: defaults completos, válidos, sin ruido
    .conSaldo(Money.of(100))      // Builder: la única variable relevante para este test
    .build();
```

Resultado: cada test resalta su variable bajo prueba y nada más.

Para los **DTOs y records de datos** que ya llevan Lombok `@Builder` (ver skill `java-architect`),
usa ese builder directamente en los tests — no escribas uno a mano. El builder manual descrito
aquí es para entidades y agregados de dominio, que se construyen con factory method y no llevan
`@Builder`.

## El Builder

Defaults que produzcan siempre un objeto **válido** y representativo. Métodos `con...()` que
retornan `this`. Un solo `build()`.

```java
public final class UsuarioBuilder {

    private UUID id = UUID.randomUUID();
    private String nombre = "Usuario de Prueba";
    private Email email = Email.of("test@example.com");
    private Money saldo = Money.ZERO;
    private Estado estado = Estado.ACTIVO;

    public UsuarioBuilder conId(UUID id)        { this.id = id; return this; }
    public UsuarioBuilder conNombre(String n)   { this.nombre = n; return this; }
    public UsuarioBuilder conEmail(Email e)     { this.email = e; return this; }
    public UsuarioBuilder conSaldo(Money s)     { this.saldo = s; return this; }
    public UsuarioBuilder suspendido()          { this.estado = Estado.SUSPENDIDO; return this; }

    public Usuario build() {
        return new Usuario(id, nombre, email, saldo, estado);
    }
}
```

Pautas:
- Los `con...()` que representan un estado completo pueden no recibir parámetro (`suspendido()`).
- No pongas lógica de negocio en el builder; solo ensambla.
- Si el objeto tiene sub-objetos, expón builders anidados o acepta el sub-builder.

## El Object Mother

Punto de entrada estático que devuelve el builder ya con defaults. Nómbralo por intención.

```java
public final class Usuarios {
    private Usuarios() {}

    public static UsuarioBuilder unUsuario() {
        return new UsuarioBuilder();
    }

    // Atajos para arquetipos MUY reutilizados (úsalos con mesura; el builder cubre el resto):
    public static UsuarioBuilder unUsuarioSuspendido() {
        return unUsuario().suspendido();
    }
}
```

Importa estático en el test para que se lea limpio:

```java
import static com.acme.fixtures.Usuarios.unUsuario;
```

No crees un atajo por cada combinación posible — ahí es donde el Mother puro fallaba. El builder
existe justamente para las combinaciones. Reserva los atajos para arquetipos que se repiten en
muchísimos tests.

---

## Dónde viven los fixtures

### Gradle multi-módulo (recomendado): `java-test-fixtures`

Expón builders y mothers desde el módulo que define el tipo, sin duplicarlos ni ensuciar `main`.

En el módulo de dominio (`:dominio/build.gradle.kts`):

```kotlin
plugins {
    `java-library`
    `java-test-fixtures`
}
```

Coloca los fixtures en `src/testFixtures/java/...`. Consúmelos desde otro módulo:

```kotlin
// :aplicacion/build.gradle.kts
dependencies {
    testImplementation(testFixtures(project(":dominio")))
}
```

Así el módulo de aplicación reutiliza los builders del dominio en sus tests sin copiarlos.

### Gradle módulo único

Pon los fixtures en `src/test/java` bajo un paquete `fixtures` o `testdata`. Son visibles para
todos los tests del módulo. Misma estructura de Mother + Builder.

### Maven

Maven no tiene `testFixtures` nativo. Opciones:
- Para módulo único: paquete `fixtures` en `src/test/java`.
- Para multi-módulo: un módulo `test-fixtures` aparte (o el `test-jar` goal de
  `maven-jar-plugin`) que los demás declaran con `<scope>test</scope>`.

---

## Datos reactivos y aleatoriedad

- Un builder construye **objetos de dominio planos**, no `Mono`/`Flux`. La envoltura reactiva la
  pone el stub del puerto en el test: `when(repo.buscar(id)).thenReturn(Mono.just(unUsuario().build()))`.
- Evita aleatoriedad no controlada (fechas `now()`, `UUID.randomUUID()` cuando el valor importa).
  Si el test compara contra el id/fecha, fíjalo en el builder (`conId(...)`, `conFecha(...)`) para
  que el test sea determinista.
