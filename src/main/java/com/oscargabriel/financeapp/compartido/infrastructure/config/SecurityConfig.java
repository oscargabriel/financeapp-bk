package com.oscargabriel.financeapp.compartido.infrastructure.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
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

import com.oscargabriel.financeapp.compartido.infrastructure.adapter.in.web.UnauthenticatedEntryPoint;

import tools.jackson.databind.ObjectMapper;

/**
 * Tres cadenas, no una. Desde FA-43 el registro, el login y el status se autentican con la
 * credencial compartida del Basic, y desde FIN-47 tambien el refresco y el logout; desde FIN-89 el
 * webhook de Telegram con su secreto; el resto del API con el JWT. Lo que las separa son los
 * securityMatcher de las dos primeras: lo que no cae en ninguno lo atiende la del JWT, que por eso
 * va ultima.
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
     *
     * El refresco y el logout van aqui y no con el Bearer (FIN-47): el refresh viaja en el cuerpo,
     * como la contrasena en el login, y el access puede estar vencido justo cuando hacen falta. El
     * registro y el login con Google (FIN-52), por lo mismo que el alta y el login.
     */
    private static final ServerWebExchangeMatcher RUTAS_BASIC = ServerWebExchangeMatchers.matchers(
            ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST,
                    "/auth/register", "/auth/login", "/auth/refresh", "/auth/logout",
                    "/auth/google/register", "/auth/google/login"),
            ServerWebExchangeMatchers.pathMatchers(HttpMethod.GET, "/status"));

    /** Telegram entrega los updates por POST; un GET a la misma ruta cae en la cadena del JWT. */
    private static final ServerWebExchangeMatcher RUTA_TELEGRAM =
            ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, "/telegram/webhook");

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
     * Solo el webhook de Telegram, solo con su secreto (FIN-89). Ni Basic ni resource server: ninguna
     * de las otras dos credenciales sirve aqui, y el 401 sale antes de leer el update. El secreto se
     * valida al construir la cadena, y sin el la aplicacion no arranca.
     */
    @Bean
    @Order(1)
    public SecurityWebFilterChain telegramSecurityFilterChain(ServerHttpSecurity http,
            @Qualifier("telegramEntryPoint") UnauthenticatedEntryPoint entryPoint,
            @Value("${telegram.webhook-secret}") String secreto) {
        return comun(http)
                .securityMatcher(RUTA_TELEGRAM)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .addFilterAt(AutenticacionDelWebhook.filtro(secreto, entryPoint), SecurityWebFiltersOrder.AUTHENTICATION)
                .authorizeExchange(exchanges -> exchanges.anyExchange().authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint))
                .build();
    }

    /**
     * Sin securityMatcher: recoge todo lo que las cadenas anteriores no reclamaron. El Basic se
     * deshabilita explicitamente para que la credencial compartida no sirva tambien aqui — es la
     * mitad del criterio que importa, porque una llave que abriera las dos puertas no separaria nada.
     */
    @Bean
    @Order(2)
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

    /** Lo que vale igual en las tres cadenas, en un solo sitio para que no se separen con el tiempo. */
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

    /** Telegram no lee el reto, pero un 401 sin WWW-Authenticate no es valido en HTTP. */
    @Bean
    public UnauthenticatedEntryPoint telegramEntryPoint(ObjectMapper objectMapper) {
        return new UnauthenticatedEntryPoint(objectMapper, "Telegram");
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
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
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
