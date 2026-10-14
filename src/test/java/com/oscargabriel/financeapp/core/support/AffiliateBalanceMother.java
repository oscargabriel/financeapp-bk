package com.oscargabriel.financeapp.core.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.AffiliateBalance;
import com.oscargabriel.financeapp.core.domain.model.AffiliationStatus;
import com.oscargabriel.financeapp.core.domain.model.CurrencyBalance;
import com.oscargabriel.financeapp.core.domain.model.ShareDirection;
import com.oscargabriel.financeapp.core.domain.model.ShareStatus;
import com.oscargabriel.financeapp.core.domain.model.ShareWithAffiliate;
import com.oscargabriel.financeapp.core.domain.model.Sharing;

/**
 * Saldo y partes entre el usuario y la otra persona de AffiliationMother (FIN-67). La base del saldo es una afiliacion
 * aceptada con B que le debe 30000 COP al usuario y a la que el le debe 10000; la de la parte, una pendiente que B le
 * debe al usuario.
 */
public final class AffiliateBalanceMother {

    public static final UUID PARTE_ID = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    public static final Instant COMPARTIDA = Instant.parse("2026-10-13T17:30:00Z");
    public static final Instant RESUELTA = Instant.parse("2026-10-14T09:00:00Z");

    private AffiliateBalanceMother() {
    }

    public static Saldo unSaldo() {
        return new Saldo();
    }

    public static Parte unaParte() {
        return new Parte();
    }

    public static final class Saldo {

        private AffiliationStatus status = AffiliationStatus.ACCEPTED;
        private List<CurrencyBalance> balances = List.of(
                new CurrencyBalance("COP", new BigDecimal("30000"), new BigDecimal("10000")));

        public Saldo conEstado(AffiliationStatus status) {
            this.status = status;
            return this;
        }

        public Saldo aPazYSalvo() {
            this.balances = List.of();
            return this;
        }

        public AffiliateBalance build() {
            return new AffiliateBalance(AffiliationMother.AFILIACION_ID, status,
                    new Sharing.Person(AffiliationMother.CORREO_DESTINATARIO, "Beto", "Debe"), balances);
        }
    }

    public static final class Parte {

        private ShareStatus status = ShareStatus.PENDING;
        private Instant resolvedAt;
        private ShareDirection direction = ShareDirection.OWED_TO_ME;

        public Parte pagada() {
            this.status = ShareStatus.PAID;
            this.resolvedAt = RESUELTA;
            return this;
        }

        public Parte queDebo() {
            this.direction = ShareDirection.I_OWE;
            return this;
        }

        public ShareWithAffiliate build() {
            return new ShareWithAffiliate(PARTE_ID, "Cena", COMPARTIDA, new BigDecimal("30000"), "COP", status,
                    resolvedAt, direction);
        }
    }
}
