package com.oscargabriel.financeapp.application.usecase;

import static com.oscargabriel.financeapp.support.InstallmentMother.HOY;
import static com.oscargabriel.financeapp.support.InstallmentMother.unaCompra;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.model.InstallmentPurchase;
import com.oscargabriel.financeapp.domain.model.InstallmentRef;
import com.oscargabriel.financeapp.domain.model.ScheduledInstallment;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.TransactionOrigin;
import com.oscargabriel.financeapp.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.model.UserProfile;
import com.oscargabriel.financeapp.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.domain.port.out.InstallmentPurchaseRepositoryPort;
import com.oscargabriel.financeapp.domain.port.out.UserRepositoryPort;
import com.oscargabriel.financeapp.support.InstallmentMother;
import com.oscargabriel.financeapp.support.TransactionMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreateInstallmentPurchaseUseCaseTest {

    /** Viernes 9 de octubre a las 10:00 en Bogota. */
    private static final Instant AHORA = Instant.parse("2026-10-09T15:00:00Z");

    private static final UUID USER_ID = TransactionMother.USER_ID;

    @Mock
    private AccountQueryPort cuentas;

    @Mock
    private CategoryQueryPort categorias;

    @Mock
    private UserRepositoryPort usuarios;

    @Mock
    private InstallmentPurchaseRepositoryPort compras;

    @Captor
    private ArgumentCaptor<InstallmentPurchase> compraGuardada;

    @Captor
    private ArgumentCaptor<List<Transaction>> cuotas;

    private CreateInstallmentPurchaseUseCase casoDeUso;

    @BeforeEach
    void escenario() {
        when(cuentas.findByUser(USER_ID, true)).thenReturn(Flux.fromIterable(InstallmentMother.cuentasConTarjetas()));
        when(categorias.findActiveByUser(eq(USER_ID), any()))
                .thenReturn(Flux.fromIterable(TransactionMother.categoriasDelUsuario()));
        when(usuarios.findActiveProfile(USER_ID)).thenReturn(Mono.just(new UserProfile(USER_ID, "ana@ejemplo.com",
                "Ana", null, null, "COP", "America/Bogota")));
        when(compras.save(any(), anyList())).thenReturn(Mono.empty());
        casoDeUso = new CreateInstallmentPurchaseUseCase(cuentas, categorias, usuarios, compras,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void guardaLaCompraConLaTasaDeLaTarjetaYSusCuotas() {
        StepVerifier.create(casoDeUso.create(USER_ID, unaCompra().build())).expectNextCount(1).verifyComplete();

        verify(compras).save(compraGuardada.capture(), cuotas.capture());
        InstallmentPurchase compra = compraGuardada.getValue();
        assertThat(compra.id().version()).isEqualTo(7);
        assertThat(compra.userId()).isEqualTo(USER_ID);
        assertThat(compra.accountId()).isEqualTo(InstallmentMother.VISA_ID);
        assertThat(compra.categoryId()).isEqualTo(TransactionMother.MERCADO_ID);
        assertThat(compra.currencyCode()).isEqualTo("COP");
        assertThat(compra.description()).isEqualTo("Televisor");
        assertThat(compra.purchaseDate()).isEqualTo(HOY);
        assertThat(compra.installmentCount()).isEqualTo(3);
        assertThat(compra.monthlyInterestRate()).isEqualByComparingTo("2");

        assertThat(cuotas.getValue()).extracting(Transaction::occurredAt).containsExactly(
                Instant.parse("2026-11-05T05:00:00Z"), Instant.parse("2026-12-05T05:00:00Z"),
                Instant.parse("2027-01-05T05:00:00Z"));
        assertThat(cuotas.getValue()).extracting(Transaction::amount).containsExactly(
                new BigDecimal("424000"), new BigDecimal("416000"), new BigDecimal("408000"));
        assertThat(cuotas.getValue()).extracting(Transaction::installment).containsExactly(
                new InstallmentRef(compra.id(), 1, 3, new BigDecimal("400000")),
                new InstallmentRef(compra.id(), 2, 3, new BigDecimal("400000")),
                new InstallmentRef(compra.id(), 3, 3, new BigDecimal("400000")));
        assertThat(cuotas.getValue()).allSatisfy(t -> {
            assertThat(t.id().version()).isEqualTo(7);
            assertThat(t.userId()).isEqualTo(USER_ID);
            assertThat(t.type()).isEqualTo(TransactionType.EXPENSE);
            assertThat(t.accountId()).isEqualTo(InstallmentMother.VISA_ID);
            assertThat(t.destinationAccountId()).isNull();
            assertThat(t.categoryId()).isEqualTo(TransactionMother.MERCADO_ID);
            assertThat(t.currencyCode()).isEqualTo("COP");
            assertThat(t.description()).isEqualTo("Televisor");
            assertThat(t.notes()).isNull();
            assertThat(t.status()).isEqualTo(TransactionStatus.CONFIRMED);
            assertThat(t.origin()).isEqualTo(TransactionOrigin.WEB);
            assertThat(t.recurrenceId()).isNull();
        });
    }

    @Test
    void devuelveLaCompraConSuPlanYElResumen() {
        StepVerifier.create(casoDeUso.create(USER_ID, unaCompra().build()))
                .assertNext(creada -> {
                    verify(compras).save(compraGuardada.capture(), cuotas.capture());
                    List<Transaction> guardadas = cuotas.getValue();
                    assertThat(creada.view().purchase()).isEqualTo(compraGuardada.getValue());
                    assertThat(creada.view().paidCount()).isZero();
                    assertThat(creada.view().remainingPrincipal()).isEqualByComparingTo("1200000");
                    assertThat(creada.view().remainingAmount()).isEqualByComparingTo("1248000");
                    assertThat(creada.view().nextInstallment().number()).isEqualTo(1);
                    assertThat(creada.view().nextInstallment().transactionId()).isEqualTo(guardadas.getFirst().id());
                    assertThat(creada.view().nextInstallment().dueAt()).isEqualTo(Instant.parse("2026-11-05T05:00:00Z"));
                    assertThat(creada.view().nextInstallment().amount()).isEqualByComparingTo("424000");
                    assertThat(creada.installments()).extracting(ScheduledInstallment::transactionId)
                            .containsExactlyElementsOf(guardadas.stream().map(Transaction::id).toList());
                    assertThat(creada.installments()).extracting(ScheduledInstallment::interest).containsExactly(
                            new BigDecimal("24000"), new BigDecimal("16000"), new BigDecimal("8000"));
                })
                .verifyComplete();
    }

    /** Del 10 de junio, a 6 cuotas: julio, agosto, septiembre y octubre ya vencieron. */
    @Test
    void unaCompraPasadaYaTienePagadasLasVencidas() {
        StepVerifier.create(casoDeUso.create(USER_ID, unaCompra().amount("600000").installmentCount(6)
                        .purchaseDate(LocalDate.of(2026, 6, 10)).build()))
                .assertNext(creada -> {
                    assertThat(creada.view().paidCount()).isEqualTo(4);
                    assertThat(creada.view().remainingPrincipal()).isEqualByComparingTo("200000");
                    assertThat(creada.view().remainingAmount()).isEqualByComparingTo("206000");
                    assertThat(creada.view().nextInstallment().number()).isEqualTo(5);
                })
                .verifyComplete();
    }

    @Test
    void unaCompraYaPagadaNoTieneProximaCuota() {
        StepVerifier.create(casoDeUso.create(USER_ID, unaCompra().purchaseDate(LocalDate.of(2026, 1, 10)).build()))
                .assertNext(creada -> {
                    assertThat(creada.view().paidCount()).isEqualTo(3);
                    assertThat(creada.view().remainingAmount()).isEqualByComparingTo("0");
                    assertThat(creada.view().nextInstallment()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void unErrorNoGuardaNada() {
        StepVerifier.create(casoDeUso.create(USER_ID, unaCompra().accountId(TransactionMother.ORIGEN_ID.toString())
                        .build()))
                .expectError(BadRequestException.class)
                .verify();

        verify(compras, never()).save(any(), anyList());
    }
}
