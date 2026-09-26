package com.oscargabriel.financeapp.support;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.Account;
import com.oscargabriel.financeapp.domain.model.AccountType;
import com.oscargabriel.financeapp.domain.model.Category;
import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.model.CreateTransactionCommand;

/** Cuentas, categorias y elementos de lote para las pruebas del alta de transacciones. */
public final class TransactionMother {

    public static final UUID USER_ID = UUID.fromString("10000000-0000-7000-8000-000000000001");

    public static final UUID ORIGEN_ID = UUID.fromString("30000000-0000-7000-8000-000000000001");
    public static final UUID DESTINO_ID = UUID.fromString("30000000-0000-7000-8000-000000000002");
    public static final UUID USD_ID = UUID.fromString("30000000-0000-7000-8000-000000000003");
    public static final UUID INACTIVA_ID = UUID.fromString("30000000-0000-7000-8000-000000000004");
    /** Una cuenta que el usuario no tiene: de otro usuario o inexistente, da igual. */
    public static final UUID AJENA_ID = UUID.fromString("20000000-0000-7000-8000-000000000001");

    public static final UUID MERCADO_ID = UUID.fromString("40000000-0000-7000-8000-000000000001");
    public static final UUID SALARIO_ID = UUID.fromString("40000000-0000-7000-8000-000000000002");
    public static final UUID AMBAS_ID = UUID.fromString("40000000-0000-7000-8000-000000000003");

    public static final String FECHA = "2026-09-20T10:15:00-05:00";

    private TransactionMother() {
    }

    public static List<Account> cuentasDelUsuario() {
        return List.of(
                new Account(ORIGEN_ID, "Efectivo", AccountType.CASH, "COP",
                        new BigDecimal("1000000.0000"), null, true),
                new Account(DESTINO_ID, "Ahorros", AccountType.SAVINGS, "COP",
                        BigDecimal.ZERO, null, true),
                new Account(USD_ID, "Ahorros USD", AccountType.SAVINGS, "USD",
                        new BigDecimal("1200.0000"), null, true),
                new Account(INACTIVA_ID, "Nequi", AccountType.DEBIT, "COP",
                        new BigDecimal("80000.0000"), null, false));
    }

    public static List<Category> categoriasDelUsuario() {
        return List.of(
                new Category(MERCADO_ID, "Mercado", CategoryScope.EXPENSE, "shopping-cart", "#2E7D32", true),
                new Category(SALARIO_ID, "Salario", CategoryScope.INCOME, "wallet", "#1B5E20", true),
                new Category(AMBAS_ID, "Reintegros", CategoryScope.BOTH, null, null, false));
    }

    public static Elemento unGasto() {
        return new Elemento().type("EXPENSE").accountId(ORIGEN_ID.toString())
                .categoryId(MERCADO_ID.toString()).amount(new BigDecimal("50000"))
                .description("Mercado de la semana").occurredAt(FECHA);
    }

    public static Elemento unIngreso() {
        return new Elemento().type("INCOME").accountId(ORIGEN_ID.toString())
                .categoryId(SALARIO_ID.toString()).amount(new BigDecimal("200000"))
                .description("Pago quincena").occurredAt(FECHA);
    }

    public static Elemento unaTransferencia() {
        return new Elemento().type("TRANSFER").accountId(ORIGEN_ID.toString())
                .destinationAccountId(DESTINO_ID.toString()).amount(new BigDecimal("100000"))
                .description("Ahorro del mes").occurredAt(FECHA);
    }

    /** Builder de un elemento del lote: los records no traen withers. */
    public static final class Elemento {

        private String type;
        private String accountId;
        private String destinationAccountId;
        private String categoryId;
        private BigDecimal amount;
        private BigDecimal destinationAmount;
        private String currencyCode;
        private String description;
        private String notes;
        private String occurredAt;

        public Elemento type(String valor) {
            this.type = valor;
            return this;
        }

        public Elemento accountId(String valor) {
            this.accountId = valor;
            return this;
        }

        public Elemento destinationAccountId(String valor) {
            this.destinationAccountId = valor;
            return this;
        }

        public Elemento categoryId(String valor) {
            this.categoryId = valor;
            return this;
        }

        public Elemento amount(BigDecimal valor) {
            this.amount = valor;
            return this;
        }

        public Elemento destinationAmount(BigDecimal valor) {
            this.destinationAmount = valor;
            return this;
        }

        public Elemento currencyCode(String valor) {
            this.currencyCode = valor;
            return this;
        }

        public Elemento description(String valor) {
            this.description = valor;
            return this;
        }

        public Elemento notes(String valor) {
            this.notes = valor;
            return this;
        }

        public Elemento occurredAt(String valor) {
            this.occurredAt = valor;
            return this;
        }

        public CreateTransactionCommand build() {
            return new CreateTransactionCommand(type, accountId, destinationAccountId, categoryId, amount,
                    destinationAmount, currencyCode, description, notes, occurredAt);
        }
    }
}
