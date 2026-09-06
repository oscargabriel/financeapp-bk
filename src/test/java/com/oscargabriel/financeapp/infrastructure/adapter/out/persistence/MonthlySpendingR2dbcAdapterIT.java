package com.oscargabriel.financeapp.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.r2dbc.test.autoconfigure.DataR2dbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import com.oscargabriel.financeapp.domain.model.MonthRange;
import com.oscargabriel.financeapp.domain.model.MonthlySpending;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * PostgreSQL real: es el unico punto donde se verifica que la vista existe, que sus columnas casan
 * con el mapeo y que el corte de mes respeta la zona horaria del usuario. El contenedor aplica el
 * schema y la semilla del repo, asi que un cambio en docs/database rompe aqui y no en produccion.
 */
@DataR2dbcTest
@Import(MonthlySpendingR2dbcAdapter.class)
@Testcontainers
class MonthlySpendingR2dbcAdapterIT {

    private static final UUID USUARIO_UNO = UUID.fromString("10000000-0000-7000-8000-000000000001");
    private static final UUID USUARIO_DOS = UUID.fromString("10000000-0000-7000-8000-000000000002");
    private static final UUID USUARIO_INEXISTENTE = UUID.fromString("10000000-0000-7000-8000-00000000000f");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("docs/database/schema.sql"),
                    "/docker-entrypoint-initdb.d/01-schema.sql")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("docs/database/seed.sql"),
                    "/docker-entrypoint-initdb.d/02-seed.sql")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("db/monthly-spending-fixture.sql"),
                    "/docker-entrypoint-initdb.d/03-fixture.sql");

    @Autowired
    private MonthlySpendingR2dbcAdapter adapter;

    @Test
    void devuelveLosMesesDelRangoDelMasRecienteAlMasAntiguo() {
        StepVerifier.create(buscar(YearMonth.of(2026, 1), YearMonth.of(2026, 3))
                        .map(mes -> mes.periodMonth().toString()))
                .expectNext("2026-03", "2026-02", "2026-01")
                .verifyComplete();
    }

    @Test
    void sumaEnElMesLocalDelUsuarioElGastoDelUltimoDiaAlas2130DeBogota() {
        StepVerifier.create(buscar(YearMonth.of(2026, 1), YearMonth.of(2026, 1)))
                .assertNext(enero -> {
                    assertThat(enero.totalSpent()).isEqualByComparingTo("125000");
                    assertThat(enero.transactionCount()).isEqualTo(2L);
                })
                .verifyComplete();
    }

    @Test
    void calculaMetaRestanteYPorcentajeCuandoElMesTieneMetaGlobal() {
        StepVerifier.create(buscar(YearMonth.of(2026, 1), YearMonth.of(2026, 1)))
                .assertNext(enero -> {
                    assertThat(enero.currencyCode()).isEqualTo("COP");
                    assertThat(enero.budgetAmount()).isEqualByComparingTo("200000");
                    assertThat(enero.remaining()).isEqualByComparingTo("75000");
                    assertThat(enero.percentUsed()).isEqualByComparingTo("62.50");
                })
                .verifyComplete();
    }

    @Test
    void dejaEnNullMetaRestanteYPorcentajeCuandoElMesNoTieneMetaGlobal() {
        StepVerifier.create(buscar(YearMonth.of(2026, 2), YearMonth.of(2026, 2)))
                .assertNext(febrero -> {
                    assertThat(febrero.budgetAmount()).isNull();
                    assertThat(febrero.remaining()).isNull();
                    assertThat(febrero.percentUsed()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void ignoraLosIngresosAlSumarElGastoDelMes() {
        StepVerifier.create(buscar(YearMonth.of(2026, 2), YearMonth.of(2026, 2)))
                .assertNext(febrero -> {
                    assertThat(febrero.totalSpent()).isEqualByComparingTo("300000");
                    assertThat(febrero.transactionCount()).isEqualTo(1L);
                })
                .verifyComplete();
    }

    @Test
    void devuelveElMesConMetaYSinGastosConTotalEnCero() {
        StepVerifier.create(buscar(YearMonth.of(2026, 3), YearMonth.of(2026, 3)))
                .assertNext(marzo -> {
                    assertThat(marzo.totalSpent()).isEqualByComparingTo("0");
                    assertThat(marzo.transactionCount()).isZero();
                    assertThat(marzo.budgetAmount()).isEqualByComparingTo("500000");
                    assertThat(marzo.remaining()).isEqualByComparingTo("500000");
                    assertThat(marzo.percentUsed()).isEqualByComparingTo("0.00");
                })
                .verifyComplete();
    }

    @Test
    void excluyeLosMesesFueraDelRango() {
        StepVerifier.create(buscar(YearMonth.of(2026, 1), YearMonth.of(2026, 3)))
                .expectNextCount(3)
                .verifyComplete();

        StepVerifier.create(buscar(YearMonth.of(2025, 12), YearMonth.of(2025, 12)))
                .assertNext(diciembre -> assertThat(diciembre.totalSpent()).isEqualByComparingTo("50000"))
                .verifyComplete();
    }

    @Test
    void noMezclaLosMesesDeOtroUsuario() {
        StepVerifier.create(adapter.findByUserAndRange(
                        USUARIO_DOS, new MonthRange(YearMonth.of(2026, 1), YearMonth.of(2026, 1))))
                .assertNext(mes -> assertThat(mes.totalSpent()).isEqualByComparingTo("777000"))
                .verifyComplete();

        StepVerifier.create(buscar(YearMonth.of(2026, 1), YearMonth.of(2026, 1)))
                .assertNext(mes -> assertThat(mes.totalSpent()).isEqualByComparingTo("125000"))
                .verifyComplete();
    }

    @Test
    void noDevuelveNadaCuandoElUsuarioNoExiste() {
        StepVerifier.create(adapter.findByUserAndRange(USUARIO_INEXISTENTE,
                        new MonthRange(YearMonth.of(2026, 1), YearMonth.of(2026, 3))))
                .verifyComplete();
    }

    private Flux<MonthlySpending> buscar(YearMonth from, YearMonth to) {
        return adapter.findByUserAndRange(USUARIO_UNO, new MonthRange(from, to));
    }
}
