package com.oscargabriel.financeapp.core.support;

import java.time.Instant;
import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.Affiliation;
import com.oscargabriel.financeapp.core.domain.model.AffiliationDirection;
import com.oscargabriel.financeapp.core.domain.model.AffiliationStatus;
import com.oscargabriel.financeapp.core.domain.model.AffiliationView;

/** Solicitudes de afiliacion. El remitente es el usuario de UserMother; la base esta abierta y tiene destinatario. */
public final class AffiliationMother {

    public static final UUID AFILIACION_ID = UUID.fromString("90000000-0000-7000-8000-000000000001");
    public static final UUID REMITENTE_ID = UserMother.ID;
    public static final UUID DESTINATARIO_ID = UUID.fromString("10000000-0000-7000-8000-000000000002");
    public static final UUID AJENO_ID = UUID.fromString("10000000-0000-7000-8000-000000000003");
    public static final String CORREO_DESTINATARIO = "beto@ejemplo.com";
    public static final Instant ENVIADA = Instant.parse("2026-10-13T15:00:00Z");

    private AffiliationMother() {
    }

    public static Solicitud unaSolicitud() {
        return new Solicitud();
    }

    public static Vista unaVista() {
        return new Vista();
    }

    public static final class Solicitud {

        private UUID addresseeId = DESTINATARIO_ID;
        private AffiliationStatus status = AffiliationStatus.REQUESTED;

        public Solicitud sinDestinatario() {
            this.addresseeId = null;
            return this;
        }

        public Solicitud conEstado(AffiliationStatus status) {
            this.status = status;
            return this;
        }

        public Affiliation build() {
            return new Affiliation(AFILIACION_ID, REMITENTE_ID, CORREO_DESTINATARIO, addresseeId, status, ENVIADA, ENVIADA);
        }
    }

    /** La solicitud base vista por quien la recibio. */
    public static final class Vista {

        private AffiliationStatus status = AffiliationStatus.REQUESTED;
        private AffiliationDirection direction = AffiliationDirection.RECEIVED;

        public Vista conEstado(AffiliationStatus status) {
            this.status = status;
            return this;
        }

        public Vista enviada() {
            this.direction = AffiliationDirection.SENT;
            return this;
        }

        public AffiliationView build() {
            return new AffiliationView(AFILIACION_ID, status, direction, UserMother.EMAIL, "Ana", "Gomez", ENVIADA, ENVIADA);
        }
    }
}
