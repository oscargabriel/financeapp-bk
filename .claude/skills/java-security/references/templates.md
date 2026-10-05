# Plantillas — SecurityConfig y migración a JWT

Léelo al generar una configuración de seguridad desde cero o al migrar de HTTP Basic a Bearer
tokens.

## SecurityConfig con HTTP Basic

Antes de generar, definir: paquete destino, qué endpoints son públicos (por defecto `/v1/status` y
las rutas de Swagger), si Swagger UI queda público (habitual en dev), y qué métodos HTTP admite
CORS. Las credenciales vienen de propiedades, nunca hardcodeadas.

```java
package <package>.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

  private final String username;
  private final String password;
  private final String allowedOrigins;

  public SecurityConfig(
      @Value("${cors.allowed-origins}") String allowedOrigins,
      @Value("${spring.security.basic.username}") String username,
      @Value("${spring.security.basic.password}") String password) {
    this.allowedOrigins = allowedOrigins;
    this.username = username;
    this.password = password;
  }

  @Bean
  public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
    return http
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .authorizeExchange(exchanges -> exchanges
            .pathMatchers("/v1/status").permitAll()
            .pathMatchers("/swagger-ui.html", "/swagger-ui/**",
                          "/v3/api-docs/**", "/webjars/**").permitAll()
            .anyExchange().authenticated())
        .httpBasic(httpBasic -> {})
        .build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();

    if ("*".equals(allowedOrigins)) {
      configuration.addAllowedOriginPattern("*");
    } else {
      Arrays.stream(allowedOrigins.split(","))
          .map(String::trim)
          .forEach(configuration::addAllowedOrigin);
    }
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  public MapReactiveUserDetailsService userDetailsService() {
    UserDetails user = User.builder()
        .username(username)
        .password(passwordEncoder().encode(password))
        .roles("USER")
        .build();
    return new MapReactiveUserDetailsService(user);
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
```

Propiedades correspondientes (los valores reales vienen de variables de entorno o del secret del
despliegue, no versionados):

```properties
spring.security.basic.username=${SECURITY_USERNAME}
spring.security.basic.password=${SECURITY_PASSWORD}
cors.allowed-origins=*
```

Nota sobre el orden de `pathMatchers`: la primera regla que coincide gana. Un `permitAll()`
colocado después de un `authenticated()` que ya cubre esa ruta queda sombreado y no aplica. La
última regla es `anyExchange().authenticated()` (o `denyAll()`), para negar por defecto.

## Migración a JWT

### 1. Dependencias

```gradle
implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
```

### 2. `JwtService`

- `generateToken(String username)` → JWT firmado (HS256)
- `validateToken(String token)` → `true/false`
- `extractUsername(String token)` → claim `subject`
- Clave desde `@Value("${jwt.secret}")` — 256 bits mínimo, desde variable de entorno
- Expiración desde `@Value("${jwt.expiration-ms:86400000}")` (24 h por defecto)

### 3. `JwtAuthenticationFilter`

`WebFilter` reactivo que lee `Authorization: Bearer <token>`, lo valida con `JwtService`, puebla
`ReactiveSecurityContextHolder` con el usuario autenticado y deja pasar las rutas públicas sin
validar.

### 4. `SecurityConfig`

Quitar `MapReactiveUserDetailsService` y `httpBasic()`; añadir
`.addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)`. CORS y rutas públicas quedan
igual.

### 5. Propiedades

```properties
jwt.secret=${JWT_SECRET}
jwt.expiration-ms=86400000
```

## CORS

- Con `allowCredentials(true)`, el origen comodín requiere `addAllowedOriginPattern("*")`;
  `addAllowedOrigin("*")` es rechazado por Spring Security.
- En producción, lista de orígenes explícita:
  `cors.allowed-origins=https://app.example.com,https://admin.example.com`. El bean ya soporta
  varios separados por coma vía `Arrays.stream(...split(","))`.
- `setAllowCredentials(true)` solo hace falta si el frontend envía cookies o cabeceras de auth.

## Reglas de autorización por ruta

```java
.pathMatchers("/v1/my-endpoint").permitAll()      // pública
.pathMatchers("/v1/admin/**").hasRole("ADMIN")    // por rol
.pathMatchers("/v1/protected/**").authenticated() // requiere autenticación
```

Al modificar reglas, mostrar el before/after del bloque `authorizeExchange` completo — es donde el
sombreado de reglas se ve de un vistazo.
