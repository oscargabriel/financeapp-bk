package com.oscargabriel.financeapp.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.Account;
import com.oscargabriel.financeapp.domain.model.AccountType;
import com.oscargabriel.financeapp.domain.model.Category;
import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.model.CreateTransactionCommand;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.model.UpdateTransactionCommand;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.CreateTransactionRequest;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.UpdateTransactionRequest;

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

    public static final UUID GASTO_GUARDADO_ID = UUID.fromString("50000000-0000-7000-8000-000000000001");
    public static final UUID TRANSFERENCIA_GUARDADA_ID = UUID.fromString("50000000-0000-7000-8000-000000000002");

    /** "2026-09-20T10:15:00-05:00" en UTC, como vuelve de la base. */
    public static final Instant INSTANTE = Instant.parse("2026-09-20T15:15:00Z");

    public static Transaction unGastoGuardado() {
        return new Transaction(GASTO_GUARDADO_ID, USER_ID, TransactionType.EXPENSE, ORIGEN_ID, null, MERCADO_ID,
                new BigDecimal("30000.5000"), "COP", "Fruta", "En la plaza", INSTANTE);
    }

    public static Transaction unGastoGuardadoEn(UUID cuenta) {
        Transaction gasto = unGastoGuardado();
        return new Transaction(gasto.id(), gasto.userId(), gasto.type(), cuenta, null, gasto.categoryId(),
                gasto.amount(), gasto.currencyCode(), gasto.description(), gasto.notes(), gasto.occurredAt());
    }

    public static Transaction unaTransferenciaGuardada() {
        return new Transaction(TRANSFERENCIA_GUARDADA_ID, USER_ID, TransactionType.TRANSFER, ORIGEN_ID, DESTINO_ID,
                null, new BigDecimal("100000.0000"), "COP", "Ahorro del mes", null, INSTANTE);
    }

    public static Parche unParche() {
        return new Parche();
    }

    /** Builder del parche de un movimiento: todo en null, que es "no cambia". */
    public static final class Parche {

        private String type;
        private String accountId;
        private String destinationAccountId;
        private String categoryId;
        private BigDecimal amount;
        private String description;
        private String occurredAt;

        public Parche type(String valor) {
            this.type = valor;
            return this;
        }

        public Parche accountId(String valor) {
            this.accountId = valor;
            return this;
        }

        public Parche destinationAccountId(String valor) {
            this.destinationAccountId = valor;
            return this;
        }

        public Parche categoryId(String valor) {
            this.categoryId = valor;
            return this;
        }

        public Parche amount(BigDecimal valor) {
            this.amount = valor;
            return this;
        }

        public Parche description(String valor) {
            this.description = valor;
            return this;
        }

        public Parche occurredAt(String valor) {
            this.occurredAt = valor;
            return this;
        }

        public UpdateTransactionCommand build() {
            return new UpdateTransactionCommand(type, accountId, destinationAccountId, categoryId, amount,
                    description, occurredAt);
        }

        /** El mismo parche como llega en el cuerpo, para validar sus reglas de formato. */
        public UpdateTransactionRequest request() {
            return new UpdateTransactionRequest(type, accountId, destinationAccountId, categoryId, amount,
                    description, occurredAt);
        }
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

        /** El mismo elemento como llega en el cuerpo, para validar sus reglas de formato. */
        public CreateTransactionRequest request() {
            return new CreateTransactionRequest(type, accountId, destinationAccountId, categoryId, amount,
                    destinationAmount, currencyCode, description, notes, occurredAt);
        }
    }
}
