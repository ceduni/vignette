package org.titiplex.persistence.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "scenario")
@Getter
@Setter
public class Scenario {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(nullable = false, unique = true)
    private String title;

    @Column
    private String description;

    @Column
    private Instant createdAt;

    @Column(name = "author_id")
    private Long author_id;

    @Column(name = "language_id")
    private String language_id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "author_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT),
            nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "language_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT),
            nullable = false)
    private Language language;

    @OneToMany(mappedBy = "scenario", fetch = FetchType.EAGER)
    private Set<Thumbnail> thumbnails;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility_status", nullable = false)
    private ScenarioVisibilityStatus visibilityStatus = ScenarioVisibilityStatus.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "storyboard_layout_mode", nullable = false)
    private StoryboardLayoutMode storyboardLayoutMode = StoryboardLayoutMode.PRESET;

    @Column(name = "storyboard_preset", nullable = false, length = 64)
    private String storyboardPreset = "GRID_3";

    @Column(name = "storyboard_columns", nullable = false)
    private Integer storyboardColumns = 3;

    @Column(name = "parent_scenario_id")
    private Long parentScenarioId;

    @ManyToMany
    @JoinTable(
            name = "scenario_tag_link",
            joinColumns = @JoinColumn(name = "scenario_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<ScenarioTag> tags = new LinkedHashSet<>();
}
