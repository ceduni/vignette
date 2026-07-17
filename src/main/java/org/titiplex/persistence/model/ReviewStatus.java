package org.titiplex.persistence.model;

public enum ReviewStatus {
    NONE,      // scénario normal, pas un fork
    PENDING,   // fork en attente de review
    APPROVED,
    REJECTED
}