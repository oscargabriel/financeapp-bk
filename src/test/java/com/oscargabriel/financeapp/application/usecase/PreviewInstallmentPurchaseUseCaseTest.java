package com.oscargabriel.financeapp.application.usecase;

import static com.oscargabriel.financeapp.support.InstallmentMother.HOY;
import static com.oscargabriel.financeapp.support.InstallmentMother.unaCompra;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.model.CreateInstallmentPurchaseCommand;
import com.oscargabriel.financeapp.domain.model.ScheduledInstallment;
import com.oscargabriel.financeapp.domain.model.UserProfile;
import com.oscargabriel.financeapp.domain.port.out.AccountQueryPort;
import com.oscargabriel.financeapp.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.domain.port.out.UserRepositoryPort;
import com.oscargabriel.financeapp.support.InstallmentMother;
import com.oscargabriel.financeapp.support.TransactionMother;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PreviewInstallmentPurchaseUseCaseTest {

    /** Viernes 9 de octubre a las 10:00 en Bogota. */
    private static final Instant AHORA = Instant.parse("2026-10-09T15:00:00Z");

    private static final UUID USER_ID = TransactionMother.USER_ID;

    @Mock
    private AccountQueryPort cuentas;

    @Mock
    private CategoryQueryPort categorias;

    @Mock
    private UserRepositoryPort usuarios;

    @BeforeEach
    void escenario() {
        when(cuentas.findByUser(USER_ID, true)).thenReturn(Flux.fromIterable(InstallmentMother.cuentasConTarjetas()));
        when(categorias.findActiveByUser(eq(USER_ID), any()))
                .thenReturn(Flux.fromIterable(TransactionMother.categoriasDelUsuario()));
        when(usuarios.findActiveProfile(USER_ID)).thenReturn(Mono.just(new UserProfile(USER_ID, "ana@ejemplo.com",
                "Ana", null, null, "COP", "America/Bogota")));
    }

    private PreviewInstallmentPurchaseUseCase casoDeUso(Instant ahora) {
        return new PreviewInstallmentPurchaseUseCase(cuentas, categorias, usuarios, Clock.fixed(ahora, ZoneOffset.UTC));
    }

    @Test
    void calculaLasCuotasConLaTasaDeLaTarjeta() {
        StepVerifier.create(casoDeUso(AHORA).preview(USER_ID, unaCompra().build()))
                .assertNext(plan -> {
                    assertThat(plan.accountId()).isEqualTo(InstallmentMother.VISA_ID);
                    assertThat(plan.currencyCode()).isEqualTo("COP");
                    assertThat(plan.purchaseDate()).isEqualTo(HOY);
                    assertThat(plan.installmentCount()).isEqualTo(3);
                    assertThat(plan.monthlyInterestRate()).isEqualByComparingTo("2");
                    assertThat(plan.installments()).extracting(ScheduledInstallment::dueAt).containsExactly(
                            Instant.parse("2026-11-05T05:00:00Z"), Instant.parse("2026-12-05T05:00:00Z"),
                            Instant.parse("2027-01-05T05:00:00Z"));
                    assertThat(plan.installments()).extracting(ScheduledInstallment::amount).containsExactly(
                            new BigDecimal("424000"), new BigDecimal("416000"), new BigDecimal("408000"));
                    assertThat(plan.installments()).allSatisfy(c -> assertThat(c.transactionId()).isNull());
                    assertThat(plan.totalInterest()).isEqualByComparingTo("48000");
                    assertThat(plan.totalAmount()).isEqualByComparingTo("1248000");
                })
                .verifyComplete();
    }

    @Test
    void unaTarjetaSinTasaNoCobraInteres() {
        CreateInstallmentPurchaseCommand compra = unaCompra().accountId(InstallmentMother.VISA_SIN_TASA_ID.toString())
                .amount("900000").build();

        StepVerifier.create(casoDeUso(AHORA).preview(USER_ID, compra))
                .assertNext(plan -> {
                    assertThat(plan.monthlyInterestRate()).isEqualByComparingTo("0");
                    assertThat(plan.installments()).allSatisfy(c -> {
                        assertThat(c.interest()).isEqualByComparingTo("0");
                        assertThat(c.amount()).isEqualByComparingTo("300000");
                    });
                })
                .verifyComplete();
    }

    @Test
    void unaCuentaQueNoEsTarjetaEsErrorSobreAccountId() {
        rechaza(unaCompra().accountId(TransactionMother.ORIGEN_ID.toString()), AHORA, errores ->
                assertThat(errores).containsExactly(error("Las cuotas solo se registran sobre una tarjeta de credito",
                        "accountId")));
    }

    @Test
    void unaTarjetaSinCorteOSinPagoEsErrorSobreAccountId() {
        String mensaje = "La tarjeta necesita dia de corte y dia de pago para calcular las cuotas";
        rechaza(unaCompra().accountId(InstallmentMother.VISA_SIN_CORTE_ID.toString()), AHORA, errores ->
                assertThat(errores).containsExactly(error(mensaje, "accountId")));
        rechaza(unaCompra().accountId(InstallmentMother.VISA_SIN_PAGO_ID.toString()), AHORA, errores ->
                assertThat(errores).containsExactly(error(mensaje, "accountId")));
    }

    @Test
    void unaCuentaEnUsdEsErrorSobreAccountId() {
        rechaza(unaCompra().accountId(TransactionMother.USD_ID.toString()), AHORA, errores ->
                assertThat(errores).containsExactly(
                        error("Por ahora las cuotas solo se registran en tarjetas en COP", "accountId")));
    }

    @Test
    void unaTarjetaAjenaNoExiste() {
        rechaza(unaCompra().accountId(TransactionMother.AJENA_ID.toString()), AHORA, errores ->
                assertThat(errores).containsExactly(error("La cuenta no existe", "accountId")));
    }

    @Test
    void unaCategoriaDeIngresosEsErrorSobreCategoryId() {
        rechaza(unaCompra().categoryId(TransactionMother.SALARIO_ID.toString()), AHORA, errores ->
                assertThat(errores).extracting(ErrorDetail::getField).containsExactly("categoryId"));
    }

    @Test
    void unaCompraFuturaEnLaZonaDelUsuarioEsErrorSobrePurchaseDate() {
        rechaza(unaCompra().purchaseDate(HOY.plusDays(1)), AHORA, errores ->
                assertThat(errores).containsExactly(error("La fecha de compra no puede ser futura", "purchaseDate")));
    }

    /** A las 22:00 del 9 en Bogota ya es 10 en UTC: el 10 sigue siendo futuro para el usuario. */
    @Test
    void hoyEsElDiaDeLaZonaDelUsuario() {
        rechaza(unaCompra().purchaseDate(HOY.plusDays(1)), Instant.parse("2026-10-10T03:00:00Z"), errores ->
                assertThat(errores).extracting(ErrorDetail::getField).containsExactly("purchaseDate"));
    }

    @Test
    void reportaTodosLosErroresJuntos() {
        rechaza(unaCompra().accountId(TransactionMother.ORIGEN_ID.toString()).purchaseDate(HOY.plusDays(1)), AHORA,
                errores -> assertThat(errores).extracting(ErrorDetail::getField)
                        .containsExactlyInAnyOrder("accountId", "purchaseDate"));
    }

    private void rechaza(InstallmentMother.Compra compra, Instant ahora, Consumer<List<ErrorDetail>> errores) {
        StepVerifier.create(casoDeUso(ahora).preview(USER_ID, compra.build()))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(BadRequestException.class);
                    BadRequestException rechazo = (BadRequestException) e;
                    assertThat(rechazo.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    errores.accept(rechazo.getErrorResponse().getErrors());
                })
                .verify();
    }

    private static ErrorDetail error(String descripcion, String campo) {
        return ErrorDetail.of("VALIDATION_ERROR", descripcion, campo);
    }
}
