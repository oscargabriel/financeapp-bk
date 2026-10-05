package com.oscargabriel.financeapp.infrastructure.config.startup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Las credenciales se declaran como en el application.yaml de main. PropertyPlaceholderAutoConfiguration
 * es la que hace estricto al @Value en una app real: sin ella el contexto resolveria con el resolver
 * tolerante del Environment y los casos de fallo pasarian a arrancar.
 */
class R2dbcCredentialsCheckTest {

    private static final String USUARIO = "usuario-de-pruebas";
    private static final String CLAVE = "clave-que-no-debe-salir";

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(R2dbcCredentialsCheck.class)
            .withPropertyValues(
                    "spring.r2dbc.username=${DB_USERNAME}",
                    "spring.r2dbc.password=${DB_PASSWORD}");

    @Test
    void sinDbUsernameNoArrancaYNombraLaVariable() {
        contexto.withPropertyValues("DB_PASSWORD=" + CLAVE).run(arrancado -> {
            assertThat(arrancado).hasFailed();
            assertThat(mensajes(arrancado.getStartupFailure()))
                    .anyMatch(m -> m.contains("Could not resolve placeholder 'DB_USERNAME'"));
        });
    }

    @Test
    void sinDbUsernameElErrorNoTraeLaClave() {
        contexto.withPropertyValues("DB_PASSWORD=" + CLAVE).run(arrancado -> {
            assertThat(arrancado).hasFailed();
            assertThat(mensajes(arrancado.getStartupFailure())).noneMatch(m -> m.contains(CLAVE));
        });
    }

    @Test
    void sinDbPasswordNoArrancaYNombraLaVariable() {
        contexto.withPropertyValues("DB_USERNAME=" + USUARIO).run(arrancado -> {
            assertThat(arrancado).hasFailed();
            assertThat(mensajes(arrancado.getStartupFailure()))
                    .anyMatch(m -> m.contains("Could not resolve placeholder 'DB_PASSWORD'"));
        });
    }

    @Test
    void sinDbPasswordElErrorNoTraeElUsuario() {
        contexto.withPropertyValues("DB_USERNAME=" + USUARIO).run(arrancado -> {
            assertThat(arrancado).hasFailed();
            assertThat(mensajes(arrancado.getStartupFailure())).noneMatch(m -> m.contains(USUARIO));
        });
    }

    @Test
    void conLasDosVariablesArranca() {
        contexto.withPropertyValues("DB_USERNAME=" + USUARIO, "DB_PASSWORD=" + CLAVE)
                .run(arrancado -> assertThat(arrancado).hasNotFailed());
    }

    /** Es el caso del perfil local: application-local.yaml fija los valores sin las variables. */
    @Test
    void conValoresLiteralesSinVariablesArranca() {
        contexto.withPropertyValues("spring.r2dbc.username=" + USUARIO, "spring.r2dbc.password=" + CLAVE)
                .run(arrancado -> assertThat(arrancado).hasNotFailed());
    }

    private static List<String> mensajes(Throwable fallo) {
        List<String> mensajes = new ArrayList<>();
        for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
            mensajes.add(String.valueOf(causa.getMessage()));
        }
        return mensajes;
    }
}
