package org.titiplex.persistence.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "audio", uniqueConstraints = {
        @UniqueConstraint(name = "uk_audio_scenario_sha256", columnNames = {"scenario_id", "audio_sha256"})
})
public class Audio {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "audio_sha256", nullable = false, length = 64)
    private String audioSha256;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "idx", nullable = false)
    private Integer idx;

    @Column(name = "mime", nullable = false, length = 64)
    private String mime; //audio/webm

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 32, columnDefinition = "varchar(32) default 'SCENE'")
    private AudioScope scope = AudioScope.SCENE;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "scenario_id", nullable = false)
    private Long scenarioId;

    @Column(name = "language_id", nullable = false)
    private String languageId;

    @Column(name = "thumbnail_id")
    private Long thumbnailId;

    @Column(name = "source_label", length = 180)
    private String sourceLabel;

    @Column(name = "source_url", length = 512)
    private String sourceUrl;

    @Column(name = "marker_x")
    private Double markerX;

    @Column(name = "marker_y")
    private Double markerY;

    @Column(name = "marker_label", length = 120)
    private String markerLabel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "author_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private Author author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "scenario_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private Scenario scenario;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "thumbnail_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private Thumbnail thumbnail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "language_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private Language language;
}
