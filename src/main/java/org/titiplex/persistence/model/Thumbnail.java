package org.titiplex.persistence.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "thumbnail")
@Getter
@Setter
public class Thumbnail {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "idx", nullable = false)
    private Integer idx;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "scenario_id", nullable = false)
    private Long scenarioId;

    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "image_sha256", nullable = false, length = 64)
    private String imageSha256;

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

    @Column(name = "grid_column")
    private Integer gridColumn;

    @Column(name = "grid_row")
    private Integer gridRow;

    @Column(name = "grid_column_span")
    private Integer gridColumnSpan;

    @Column(name = "grid_row_span")
    private Integer gridRowSpan;

    @Column(name = "image_width")
    private Integer imageWidth;

    @Column(name = "image_height")
    private Integer imageHeight;
}
