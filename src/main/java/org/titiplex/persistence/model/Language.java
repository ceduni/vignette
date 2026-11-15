package org.titiplex.persistence.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "language")
@Getter
@Setter
public class Language {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "family_id")
    private String familyId;

    @Column(name = "parent_id")
    private String parentId;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "bookkeeping", nullable = false)
    private Boolean bookkeeping;

    @Column(name = "level", nullable = false)
    private String level;

    @Column(name = "latitude")
    private Float latitude;

    @Column(name = "longitude")
    private Float longitude;

    @Column(name = "iso639_p3code", unique = true)
    private String iso639P3code;

    @Column(name = "description", length = 10000)
    private String description;

    @Column(name = "markup_description", length = 10000)
    private String markupDescription;

    @Column(name = "child_family_count", nullable = false)
    private Integer childFamilyCount;

    @Column(name = "child_language_count", nullable = false)
    private Integer childLanguageCount;

    @Column(name = "child_dialect_count", nullable = false)
    private Integer childDialectCount;

    @Column(name = "country_ids")
    private String countryIds;

    /** True when the language was present in the latest successful Glottolog selection. */
    @Column(name = "present_in_latest_glottolog")
    private Boolean presentInLatestGlottolog;

    @Column(name = "last_seen_in_glottolog_at")
    private java.time.Instant lastSeenInGlottologAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "family_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    @NotFound(action = NotFoundAction.IGNORE)
    private Language family;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "parent_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    @NotFound(action = NotFoundAction.IGNORE)
    private Language parent;

    /**
     * Get children of Language / Family.
     */
    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    private Set<Language> children = new HashSet<>();

    @OneToMany(mappedBy = "language", fetch = FetchType.LAZY)
    private Set<Scenario> scenarios = new HashSet<>();

    @OneToMany(mappedBy = "language", fetch = FetchType.LAZY)
    private Set<Audio> audios = new HashSet<>();
}
