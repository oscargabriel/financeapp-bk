package com.oscargabriel.financeapp.compartido.infrastructure.config;

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
        variables.put("GOOGLE_CLIENT_ID", "cliente.apps.googleusercontent.com");
        variables.put("TELEGRAM_BOT_TOKEN", "123456:token-del-bot");
        variables.put("TELEGRAM_WEBHOOK_SECRET", "secreto-del-webhook-de-32-caracteres");
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

    /** N se ajusta en el servicio tras probar con el usuario, sin recompilar (FIN-113). */
    @Test
    void elMaximoDeImagenesEsTresYLoCambiaLaVariable() throws IOException {
        Map<String, Object> variables = variablesDelServicio();
        assertThat(configuracion(variables).getRequiredProperty("asistente.max-imagenes", Integer.class)).isEqualTo(3);

        variables.put("ASISTENTE_MAX_IMAGENES", "5");

        assertThat(configuracion(variables).getRequiredProperty("asistente.max-imagenes", Integer.class)).isEqualTo(5);
    }

    /** El stub solo existe fuera de prod; el servicio verifica contra las llaves de Google (FIN-52). */
    @Test
    void elLoginConGoogleVerificaContraLasLlavesDeGoogle() throws IOException {
        StandardEnvironment entorno = configuracion(variablesDelServicio());

        assertThat(entorno.getRequiredProperty("google.proveedor")).isEqualTo("google");
        assertThat(entorno.getRequiredProperty("google.client-id")).isEqualTo("cliente.apps.googleusercontent.com");
        assertThat(entorno.getRequiredProperty("google.jwks-uri"))
                .isEqualTo("https://www.googleapis.com/oauth2/v3/certs");
        assertThat(entorno.getRequiredProperty("google.timeout")).isEqualTo("5s");
    }

    /** El stub solo existe fuera de prod; el servicio habla con la Bot API de Telegram (FIN-89). */
    @Test
    void elBotHablaConLaBotApiDeTelegram() throws IOException {
        StandardEnvironment entorno = configuracion(variablesDelServicio());

        assertThat(entorno.getRequiredProperty("telegram.proveedor")).isEqualTo("telegram");
        assertThat(entorno.getRequiredProperty("telegram.bot-token")).isEqualTo("123456:token-del-bot");
        assertThat(entorno.getRequiredProperty("telegram.webhook-secret"))
                .isEqualTo("secreto-del-webhook-de-32-caracteres");
        assertThat(entorno.getRequiredProperty("telegram.base-url")).isEqualTo("https://api.telegram.org");
        assertThat(entorno.getRequiredProperty("telegram.timeout")).isEqualTo("5s");
    }

    /** Tras un fallo de ExchangeRate-API no se le vuelve a consultar en una hora (FA-121). */
    @Test
    void elReintentoDeLasTasasTrasUnFalloEsDeUnaHoraSinVariable() throws IOException {
        StandardEnvironment entorno = configuracion(variablesDelServicio());

        assertThat(entorno.getRequiredProperty("tasas.reintento-tras-fallo")).isEqualTo("1h");
    }

    /** La sesion dura 24 horas desde el login sin tocar el servicio (FIN-47). */
    @Test
    void laSesionDura24HorasSinVariable() throws IOException {
        StandardEnvironment entorno = configuracion(variablesDelServicio());

        assertThat(entorno.getRequiredProperty("spring.security.jwt.refresh-expiration")).isEqualTo("24h");
    }

    @Test
    void laDuracionDeLaSesionSeCambiaPorVariable() throws IOException {
        Map<String, Object> variables = variablesDelServicio();
        variables.put("JWT_REFRESH_EXPIRATION", "12h");

        StandardEnvironment entorno = configuracion(variables);

        assertThat(entorno.getRequiredProperty("spring.security.jwt.refresh-expiration")).isEqualTo("12h");
    }

    /** Falla cerrado: un servicio que no declara la variable queda con el registro restringido (FA-103). */
    @Test
    void laRestriccionDelAltaQuedaEncendidaSinVariable() throws IOException {
        StandardEnvironment entorno = configuracion(variablesDelServicio());

        assertThat(entorno.getRequiredProperty("registro.admitidos.enabled", Boolean.class)).isTrue();
    }

    @Test
    void laRestriccionDelAltaSeApagaPorVariable() throws IOException {
        Map<String, Object> variables = variablesDelServicio();
        variables.put("REGISTRO_ADMITIDOS_ENABLED", "false");

        StandardEnvironment entorno = configuracion(variables);

        assertThat(entorno.getRequiredProperty("registro.admitidos.enabled", Boolean.class)).isFalse();
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
            "GEMINI_MODEL, asistente.gemini.model",
            "GOOGLE_CLIENT_ID, google.client-id",
            "TELEGRAM_BOT_TOKEN, telegram.bot-token",
            "TELEGRAM_WEBHOOK_SECRET, telegram.webhook-secret"
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
