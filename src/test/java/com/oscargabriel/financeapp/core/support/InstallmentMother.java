package com.oscargabriel.financeapp.core.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.Account;
import com.oscargabriel.financeapp.core.domain.model.AccountType;
import com.oscargabriel.financeapp.core.domain.model.CreateInstallmentPurchaseCommand;
import com.oscargabriel.financeapp.core.domain.model.InstallmentPurchase;
import com.oscargabriel.financeapp.core.domain.model.InstallmentPurchaseView;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.CreateInstallmentPurchaseRequest;
import com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.UpdateInstallmentPurchaseRequest;

/** Tarjetas y compras en cuotas (FA-108). Las cuentas y categorias base son las de TransactionMother. */
public final class InstallmentMother {

    /** Viernes 9 de octubre de 2026 en Bogota. */
    public static final LocalDate HOY = LocalDate.of(2026, 10, 9);

    public static final UUID VISA_ID = UUID.fromString("30000000-0000-7000-8000-000000000012");
    public static final UUID VISA_SIN_TASA_ID = UUID.fromString("30000000-0000-7000-8000-000000000013");
    public static final UUID VISA_SIN_CORTE_ID = UUID.fromString("30000000-0000-7000-8000-000000000014");
    public static final UUID VISA_SIN_PAGO_ID = UUID.fromString("30000000-0000-7000-8000-000000000015");
    public static final UUID VISA_UNA_CUOTA_ID = UUID.fromString("30000000-0000-7000-8000-000000000016");
    public static final UUID VISA_USD_ID = UUID.fromString("30000000-0000-7000-8000-000000000017");
    public static final UUID VISA_USD_SIN_TASA_ID = UUID.fromString("30000000-0000-7000-8000-000000000018");

    public static final UUID COMPRA_ID = UUID.fromString("90000000-0000-7000-8000-000000000001");

    private InstallmentMother() {
    }

    /**
     * Las del usuario de TransactionMother mas siete tarjetas: corte 20 y pago 5, con y sin tasa, una que cobra
     * interes en las compras a 1 cuota (FA-134) y dos en USD, con y sin tasa (FA-123).
     */
    public static List<Account> cuentasConTarjetas() {
        List<Account> cuentas = new ArrayList<>(TransactionMother.cuentasDelUsuario());
        cuentas.add(tarjeta(VISA_ID, "Visa", 20, 5, new BigDecimal("2.0000")));
        cuentas.add(tarjeta(VISA_SIN_TASA_ID, "Visa sin tasa", 20, 5, null));
        cuentas.add(tarjeta(VISA_SIN_CORTE_ID, "Visa sin corte", null, 5, null));
        cuentas.add(tarjeta(VISA_SIN_PAGO_ID, "Visa sin pago", 20, null, null));
        cuentas.add(new Account(VISA_UNA_CUOTA_ID, "Visa una cuota", AccountType.CREDIT, "COP", BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("5000000.0000"), 20, 5, new BigDecimal("2.0000"), true, true,
                BigDecimal.ZERO));
        cuentas.add(tarjeta(VISA_USD_ID, "Visa USD", "USD", new BigDecimal("2.0000")));
        cuentas.add(tarjeta(VISA_USD_SIN_TASA_ID, "Visa USD sin tasa", "USD", null));
        return cuentas;
    }

    private static Account tarjeta(UUID id, String nombre, Integer corte, Integer pago, BigDecimal tasa) {
        return new Account(id, nombre, AccountType.CREDIT, "COP", BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("5000000.0000"), corte, pago, tasa, true);
    }

    private static Account tarjeta(UUID id, String nombre, String moneda, BigDecimal tasa) {
        return new Account(id, nombre, AccountType.CREDIT, moneda, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("5000.0000"), 20, 5, tasa, true);
    }

    public static Compra unaCompra() {
        return new Compra();
    }

    /** 1.200.000 a 3 cuotas en la Visa, comprada hoy: 424.000, 416.000 y 408.000. */
    public static InstallmentPurchase televisor() {
        return new InstallmentPurchase(COMPRA_ID, TransactionMother.USER_ID, VISA_ID, TransactionMother.MERCADO_ID,
                new BigDecimal("1200000"), "COP", "Televisor", HOY, 3, new BigDecimal("2.0000"));
    }

    public static InstallmentPurchaseView televisorSinPagar() {
        return new InstallmentPurchaseView(televisor(), 0, new BigDecimal("1200000"), new BigDecimal("1248000"),
                new InstallmentPurchaseView.NextInstallment(1, UUID.fromString("50000000-0000-7000-8000-000000000011"),
                        Instant.parse("2026-11-05T05:00:00Z"), new BigDecimal("424000")));
    }

    public static Cuerpo unCuerpo() {
        return new Cuerpo();
    }

    public static CuerpoDeParche unCuerpoDeParche() {
        return new CuerpoDeParche();
    }

    /** El cuerpo del alta valido por defecto, tal como llega por HTTP. */
    public static final class Cuerpo {

        private String accountId = VISA_ID.toString();
        private String categoryId = TransactionMother.MERCADO_ID.toString();
        private BigDecimal amount = new BigDecimal("1200000");
        private String description = "Televisor";
        private String purchaseDate = "2026-10-09";
        private Integer installmentCount = 3;

        public Cuerpo accountId(String valor) {
            this.accountId = valor;
            return this;
        }

        public Cuerpo categoryId(String valor) {
            this.categoryId = valor;
            return this;
        }

        public Cuerpo amount(BigDecimal valor) {
            this.amount = valor;
            return this;
        }

        public Cuerpo description(String valor) {
            this.description = valor;
            return this;
        }

        public Cuerpo purchaseDate(String valor) {
            this.purchaseDate = valor;
            return this;
        }

        public Cuerpo installmentCount(Integer valor) {
            this.installmentCount = valor;
            return this;
        }

        public CreateInstallmentPurchaseRequest request() {
            return new CreateInstallmentPurchaseRequest(accountId, categoryId, amount, description, purchaseDate,
                    installmentCount);
        }
    }

    /** El parche minimo: solo el alcance. */
    public static final class CuerpoDeParche {

        private String scope = "FUTURE";
        private String description;
        private String categoryId;

        public CuerpoDeParche scope(String valor) {
            this.scope = valor;
            return this;
        }

        public CuerpoDeParche description(String valor) {
            this.description = valor;
            return this;
        }

        public CuerpoDeParche categoryId(String valor) {
            this.categoryId = valor;
            return this;
        }

        public UpdateInstallmentPurchaseRequest request() {
            return new UpdateInstallmentPurchaseRequest(scope, description, categoryId);
        }
    }

    /** El alta por defecto es el televisor; cada metodo cambia un campo. */
    public static final class Compra {

        private String accountId = VISA_ID.toString();
        private String categoryId = TransactionMother.MERCADO_ID.toString();
        private BigDecimal amount = new BigDecimal("1200000");
        private String description = " Televisor ";
        private LocalDate purchaseDate = HOY;
        private int installmentCount = 3;

        public Compra accountId(String valor) {
            this.accountId = valor;
            return this;
        }

        public Compra categoryId(String valor) {
            this.categoryId = valor;
            return this;
        }

        public Compra amount(String valor) {
            this.amount = new BigDecimal(valor);
            return this;
        }

        public Compra purchaseDate(LocalDate valor) {
            this.purchaseDate = valor;
            return this;
        }

        public Compra installmentCount(int valor) {
            this.installmentCount = valor;
            return this;
        }

        public CreateInstallmentPurchaseCommand build() {
            return new CreateInstallmentPurchaseCommand(accountId, categoryId, amount, description, purchaseDate,
                    installmentCount);
        }
    }
}
