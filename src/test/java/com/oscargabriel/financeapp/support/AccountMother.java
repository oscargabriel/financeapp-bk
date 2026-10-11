package com.oscargabriel.financeapp.support;

import java.math.BigDecimal;
import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.Account;
import com.oscargabriel.financeapp.domain.model.AccountType;
import com.oscargabriel.financeapp.domain.model.CreateAccountCommand;

/** Cuentas para los tests. Los saldos son los del escenario de docs/database/test-data.sql. */
public final class AccountMother {

    public static final UUID USER_ID = UUID.fromString("10000000-0000-7000-8000-000000000001");

    public static final UUID EFECTIVO_ID = UUID.fromString("20000000-0000-7000-8000-000000000001");

    public static final UUID VISA_ID = UUID.fromString("20000000-0000-7000-8000-000000000003");

    private AccountMother() {
    }

    public static Account efectivo() {
        return new Account(EFECTIVO_ID, "Efectivo", AccountType.CASH, "COP", new BigDecimal("500000.0000"),
                new BigDecimal("322500.0000"), null, null, null, null, true);
    }

    /** Saldo negativo: la tarjeta debe 658.000 de un cupo de 5.000.000. */
    public static Account visa() {
        return new Account(VISA_ID, "Visa", AccountType.CREDIT, "COP", BigDecimal.ZERO,
                new BigDecimal("-658000.0000"), new BigDecimal("5000000.0000"), 15, 5, new BigDecimal("2.1000"),
                true);
    }

    /** La misma Visa con 1.200.000 de capital comprometido por cuotas que todavia no llegan. */
    public static Account visaConCuotas() {
        return new Account(VISA_ID, "Visa", AccountType.CREDIT, "COP", BigDecimal.ZERO,
                new BigDecimal("-658000.0000"), new BigDecimal("5000000.0000"), 15, 5, new BigDecimal("2.1000"),
                false, true, new BigDecimal("1200000.0000"));
    }

    /** Saldo a favor: se pago de mas y el cupo disponible supera el limite. */
    public static Account visaConSaldoAFavor() {
        return new Account(VISA_ID, "Visa", AccountType.CREDIT, "COP", BigDecimal.ZERO,
                new BigDecimal("120000.0000"), new BigDecimal("5000000.0000"), 15, 5, null, true);
    }

    /** La tabla admite una CREDIT sin credit_limit: no hay cupo que calcular. */
    public static Account creditoSinLimite() {
        return new Account(UUID.fromString("20000000-0000-7000-8000-000000000005"), "Tarjeta nueva",
                AccountType.CREDIT, "COP", new BigDecimal("-10000.0000"), new BigDecimal("-10000.0000"),
                null, null, null, null, true);
    }

    public static Account inactiva() {
        return new Account(UUID.fromString("20000000-0000-7000-8000-000000000006"), "Nequi",
                AccountType.DEBIT, "COP", new BigDecimal("80000.0000"), new BigDecimal("80000.0000"),
                null, null, null, null, false);
    }

    public static final UUID CAJA_CHICA_ID = UUID.fromString("20000000-0000-7000-8000-000000000008");

    /** Arranco en cero y un gasto la dejo en -30000, hasta que se corrigio el saldo inicial: en cero, con historia. */
    public static Account cajaChicaEnCero() {
        return new Account(CAJA_CHICA_ID, "Caja chica", AccountType.CASH, "COP", new BigDecimal("30000.0000"),
                new BigDecimal("0.0000"), null, null, null, null, true);
    }

    public static final UUID NOMINA_ID = UUID.fromString("20000000-0000-7000-8000-000000000010");

    public static final UUID BOLSILLO_ID = UUID.fromString("20000000-0000-7000-8000-000000000011");

    /** Una cuenta de debito con el saldo vigente dado, la del ejemplo de FA-133. */
    public static Account nomina(String saldo) {
        return new Account(NOMINA_ID, "Nómina", AccountType.DEBIT, "COP", new BigDecimal(saldo),
                new BigDecimal(saldo), null, null, null, null, true);
    }

    public static Account bolsillo(String saldo) {
        return new Account(BOLSILLO_ID, "Bolsillo", AccountType.CASH, "COP", new BigDecimal(saldo),
                new BigDecimal(saldo), null, null, null, null, true);
    }

    public static Account inactivaEnCero() {
        return new Account(UUID.fromString("20000000-0000-7000-8000-000000000009"), "Daviplata",
                AccountType.DEBIT, "COP", BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, null, false);
    }

    public static CreateAccountCommand altaEfectivo() {
        return new CreateAccountCommand("Billetera", "CASH", "COP", new BigDecimal("150000"),
                null, null, null, null, null, null);
    }

    public static CreateAccountCommand altaTarjeta() {
        return new CreateAccountCommand("Mastercard", "CREDIT", "COP", new BigDecimal("-200000"),
                new BigDecimal("3000000"), 20, 5, new BigDecimal("2.15"), null, null);
    }

    public static CreateAccountCommand altaTarjetaConInteresAUnaCuota() {
        return new CreateAccountCommand("Diners", "CREDIT", "COP", BigDecimal.ZERO,
                null, null, null, new BigDecimal("1.8"), true, null);
    }

    /** Una tarjeta que cobra interes en las compras a 1 cuota (FA-134). */
    public static Account tarjetaConInteresAUnaCuota() {
        return new Account(UUID.fromString("20000000-0000-7000-8000-000000000010"), "Diners", AccountType.CREDIT,
                "COP", BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, new BigDecimal("1.8000"), true, true,
                BigDecimal.ZERO);
    }

    /** Como la vuelve a leer el INSERT ... RETURNING: con el saldo que sembro el trigger. */
    public static Account tarjetaCreada() {
        return new Account(UUID.fromString("20000000-0000-7000-8000-000000000007"), "Mastercard",
                AccountType.CREDIT, "COP", new BigDecimal("-200000.0000"), new BigDecimal("-200000.0000"),
                new BigDecimal("3000000.0000"), 20, 5, new BigDecimal("2.1500"), true);
    }
}
