package com.oscargabriel.financeapp.infrastructure.config.startup;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.domain.port.out.HealthCheckPort;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Verifica que la base responda antes de que Netty abra el puerto: se ejecuta al terminar de
 * instanciar los singletons, asi que un fallo aqui rompe el refresh del contexto y la aplicacion
 * no llega a aceptar trafico. Los reintentos existen porque en contenedores la app suele arrancar
 * antes que la base y perder esa carrera por segundos no deberia tumbar el despliegue.
 */
@Component
@ConditionalOnProperty(name = "startup.db-check.enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseStartupCheck implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(DatabaseStartupCheck.class);

    private final HealthCheckPort databaseHealthCheck;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration timeout;

    public DatabaseStartupCheck(
            @Qualifier("postgresHealthCheckAdapter") HealthCheckPort databaseHealthCheck,
            @Value("${startup.db-check.max-attempts}") int maxAttempts,
            @Value("${startup.db-check.initial-backoff}") Duration initialBackoff,
            @Value("${startup.db-check.timeout}") Duration timeout) {
        this.databaseHealthCheck = databaseHealthCheck;
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.timeout = timeout;
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            // defer: sin el, retryWhen se resuscribe al mismo Mono y nunca vuelve a pedir conexion.
            Mono.defer(databaseHealthCheck::ping)
                    .timeout(timeout)
                    .retryWhen(Retry.backoff(maxAttempts - 1L, initialBackoff)
                            .doBeforeRetry(reintento -> log.warn(
                                    "Verificacion de arranque fallida (intento {} de {}): {}",
                                    reintento.totalRetries() + 1,
                                    maxAttempts,
                                    reintento.failure().toString())))
                    .block();
        } catch (RuntimeException e) {
            String servicio = databaseHealthCheck.serviceName();
            log.error("Arranque abortado: {} no respondio tras {} intentos", servicio, maxAttempts, e);
            // El mensaje no arrastra la causa: puede traer detalle del driver y sube al log de arranque.
            throw new IllegalStateException(
                    "Arranque abortado: el servicio %s no respondio tras %d intentos"
                            .formatted(servicio, maxAttempts));
        }
    }
}
