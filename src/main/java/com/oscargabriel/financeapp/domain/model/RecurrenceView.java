package com.oscargabriel.financeapp.domain.model;

import java.time.Instant;

/**
 * Una serie con su proxima ocurrencia: la existente mas proxima con fecha posterior al momento
 * actual. Null en una serie con fin a la que no le queda ninguna.
 */
public record RecurrenceView(Recurrence recurrence, Instant nextOccurrenceAt) {
}
