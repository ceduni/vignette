package org.titiplex.persistence.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "scenario_collaborator",
        uniqueConstraints = @UniqueConstraint(columnNames = {"scenario_id", "user_id"})
)
@Getter
@Setter
public class ScenarioCollaborator {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "scenario_id", nullable = false)
    private Long scenarioId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "scenario_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT),
            nullable = false)
    private Scenario scenario;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT),
            nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private CollaboratorRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CollaborationStatus status = CollaborationStatus.PENDING;

    @Column(name = "invited_by_id")
    private Long invitedById;

    @Column(name = "invited_at", nullable = false)
    private Instant invitedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;
}
