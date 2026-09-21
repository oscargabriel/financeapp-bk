package com.oscargabriel.financeapp.infrastructure.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import com.oscargabriel.financeapp.infrastructure.adapter.in.web.UnauthenticatedEntryPoint;

import tools.jackson.databind.ObjectMapper;

/**
 * Dos cadenas, no una. Desde FA-43 el registro, el login y el status se autentican con la
 * credencial compartida del Basic, y el resto del API con el JWT. Lo que separa las dos es el
 * securityMatcher de la primera: lo que no cae ahi lo atiende la segunda.
 *
 * El precio, aceptado al disenarlo: no queda ninguna ruta publica. Un cliente sin la credencial
 * compartida no puede ni crear una cuenta, y un frontend de navegador la expone a quien abra las
 * DevTools. Protege contra escaneo y registro automatizado, no contra un atacante decidido.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Las rutas del Basic. Van sin el prefijo /api porque spring.webflux.base-path lo quita el
     * HttpHandler, antes de que la cadena de seguridad vea la peticion. El metodo es parte del
     * criterio: un GET a /auth/register no entra por aqui, cae en la cadena del JWT.
     */
    private static final ServerWebExchangeMatcher RUTAS_BASIC = ServerWebExchangeMatchers.matchers(
            ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, "/auth/register", "/auth/login"),
            ServerWebExchangeMatchers.pathMatchers(HttpMethod.GET, "/status"));

    private final String allowedOrigins;

    public SecurityConfig(@Value("${cors.allowed-origins}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    @Order(0)
    public SecurityWebFilterChain basicSecurityFilterChain(ServerHttpSecurity http,
            @Qualifier("basicEntryPoint") UnauthenticatedEntryPoint entryPoint) {
        return comun(http)
                .securityMatcher(RUTAS_BASIC)
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
                .authorizeExchange(exchanges -> exchanges.anyExchange().authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint))
                .build();
    }

    /**
     * Sin securityMatcher: recoge todo lo que la cadena anterior no reclamo. El Basic se deshabilita
     * explicitamente para que la credencial compartida no sirva tambien aqui — es la mitad del
     * criterio que importa, porque una llave que abriera las dos puertas no separaria nada.
     */
    @Bean
    @Order(1)
    public SecurityWebFilterChain jwtSecurityFilterChain(ServerHttpSecurity http,
            @Qualifier("bearerEntryPoint") UnauthenticatedEntryPoint entryPoint) {
        return comun(http)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .authorizeExchange(exchanges -> exchanges.anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(entryPoint)
                        .jwt(jwt -> {}))
                // Tambien para el 401 que nace fuera del resource server, como una ruta
                // desconocida que no lleva token.
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint))
                .build();
    }

    /** Lo que vale igual en las dos cadenas, en un solo sitio para que no se separen con el tiempo. */
    private ServerHttpSecurity comun(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable);
    }

    @Bean
    public UnauthenticatedEntryPoint basicEntryPoint(ObjectMapper objectMapper) {
        return new UnauthenticatedEntryPoint(objectMapper, "Basic");
    }

    @Bean
    public UnauthenticatedEntryPoint bearerEntryPoint(ObjectMapper objectMapper) {
        return new UnauthenticatedEntryPoint(objectMapper, "Bearer");
    }

    /**
     * Un unico usuario, el de la credencial compartida, en memoria. Las dos propiedades se leen sin
     * default en application.yaml: si faltan, el contexto no arranca, igual que con JWT_SECRET. La
     * clave se hashea al construir el bean, para no tenerla en claro en memoria mas de lo necesario.
     */
    @Bean
    public MapReactiveUserDetailsService basicUserDetailsService(
            @Value("${spring.security.basic.username}") String usuario,
            @Value("${spring.security.basic.password}") String clave,
            PasswordEncoder passwordEncoder) {
        return new MapReactiveUserDetailsService(User.withUsername(usuario)
                .password(passwordEncoder.encode(clave))
                .roles("SERVICE")
                .build());
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

    /** Lo usa BCryptPasswordHasherAdapter: el alta hashea con el, el login verifica con el. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
