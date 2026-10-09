package com.oscargabriel.financeapp.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

/**
 * La imagen de Cloud Run solo lleva el application.yaml de main, que en el classpath de test queda
 * tapado por el de test: se lee del disco y se resuelve con las variables del servicio simuladas.
 * Que conecte de verdad a Neon lo verifica el arranque manual documentado en FA-47.
 */
class CloudRunConfigTest {

    private static Map<String, Object> variablesDelServicio() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("SPRING_PROFILES_ACTIVE", "prod");
        variables.put("DB_HOST", "ep-ejemplo.us-east-1.aws.neon.tech");
        variables.put("DB_NAME", "neondb");
        variables.put("DB_USERNAME", "usuario");
        variables.put("DB_PASSWORD", "clave");
        variables.put("JWT_SECRET", "secreto-de-pruebas-de-32-bytes!!");
        variables.put("BASIC_USERNAME", "basic");
        variables.put("BASIC_PASSWORD", "clave-basic");
        variables.put("CORS_ALLOWED_ORIGINS", "*");
        variables.put("GEMINI_API_KEY", "clave-de-gemini");
        variables.put("GEMINI_MODEL", "modelo-de-pruebas");
        return variables;
    }

    private static StandardEnvironment configuracion(Map<String, Object> variables) throws IOException {
        StandardEnvironment entorno = new StandardEnvironment();
        entorno.getPropertySources().addFirst(new MapPropertySource("variables", variables));
        new YamlPropertySourceLoader()
                .load("application", new FileSystemResource("src/main/resources/application.yaml"))
                .forEach(entorno.getPropertySources()::addLast);
        return entorno;
    }

    @Test
    void escuchaEnElPuertoQueInyectaCloudRun() throws IOException {
        Map<String, Object> variables = variablesDelServicio();
        variables.put("PORT", "9090");
        variables.put("SERVER_PORT", "7070");

        assertThat(configuracion(variables).getRequiredProperty("server.port")).isEqualTo("9090");
    }

    @Test
    void sinPortUsaServerPort() throws IOException {
        Map<String, Object> variables = variablesDelServicio();
        variables.put("SERVER_PORT", "7070");

        assertThat(configuracion(variables).getRequiredProperty("server.port")).isEqualTo("7070");
    }

    @Test
    void sinNingunaVariableDePuertoEscuchaEn8080() throws IOException {
        assertThat(configuracion(variablesDelServicio()).getRequiredProperty("server.port")).isEqualTo("8080");
    }

    /** Neon rechaza conexiones sin TLS (FA-44); el esquema no va en la URL (R2dbcSearchPathConfig). */
    @Test
    void laUrlExigeTlsPorDefecto() throws IOException {
        assertThat(configuracion(variablesDelServicio()).getRequiredProperty("spring.r2dbc.url"))
                .isEqualTo("r2dbc:postgresql://ep-ejemplo.us-east-1.aws.neon.tech:5432/neondb?sslMode=require");
    }

    @Test
    void elModoTlsSePuedeCambiarPorVariable() throws IOException {
        Map<String, Object> variables = variablesDelServicio();
        variables.put("DB_SSL_MODE", "verify-full");

        assertThat(configuracion(variables).getRequiredProperty("spring.r2dbc.url")).endsWith("?sslMode=verify-full");
    }

    /** 3 instancias de Cloud Run x 10 = 30 conexiones, frente a las 901 del plan Free de Neon. */
    @Test
    void elPoolCabeEnElLimiteDeNeonYNoCargaElArranqueEnFrio() throws IOException {
        StandardEnvironment entorno = configuracion(variablesDelServicio());

        assertThat(entorno.getRequiredProperty("spring.r2dbc.pool.max-size", Integer.class)).isEqualTo(10);
        assertThat(entorno.getRequiredProperty("spring.r2dbc.pool.initial-size", Integer.class)).isEqualTo(1);
    }

    /** El stub solo existe fuera de prod; el servicio usa Gemini si nadie dice otra cosa (FA-77). */
    @Test
    void elAsistenteUsaGeminiContraLaApiDeGoogle() throws IOException {
        StandardEnvironment entorno = configuracion(variablesDelServicio());

        assertThat(entorno.getRequiredProperty("asistente.proveedor")).isEqualTo("gemini");
        assertThat(entorno.getRequiredProperty("asistente.gemini.base-url"))
                .isEqualTo("https://generativelanguage.googleapis.com");
    }

    /**
     * Comprueba que ninguno trae default. Que la ausencia aborte el arranque real lo hace @Value; para
     * DB_USERNAME y DB_PASSWORD, que enlaza el binder de Boot, lo prueba R2dbcCredentialsCheckTest.
     */
    @ParameterizedTest(name = "sin {0} no resuelve {1}")
    @CsvSource({
            "SPRING_PROFILES_ACTIVE, spring.profiles.active",
            "DB_USERNAME, spring.r2dbc.username",
            "DB_PASSWORD, spring.r2dbc.password",
            "JWT_SECRET, spring.security.jwt.secret",
            "BASIC_USERNAME, spring.security.basic.username",
            "BASIC_PASSWORD, spring.security.basic.password",
            "CORS_ALLOWED_ORIGINS, cors.allowed-origins",
            "GEMINI_API_KEY, asistente.gemini.api-key",
            "GEMINI_MODEL, asistente.gemini.model"
    })
    void noResuelveUnSecretoSinSuVariable(String variable, String propiedad) throws IOException {
        Map<String, Object> variables = variablesDelServicio();
        variables.remove(variable);
        StandardEnvironment entorno = configuracion(variables);

        assertThatThrownBy(() -> entorno.getRequiredProperty(propiedad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(variable);
    }
}
