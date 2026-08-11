package org.titiplex.persistence.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
    name = "language_follow",
    uniqueConstraints = @UniqueConstraint(columnNames = {"language_id", "user_id"})
)
public class LanguageFollow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "language_id", nullable = false)
    private Language language;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Language getLanguage() { return language; }
    public void setLanguage(Language language) { this.language = language; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Instant getCreatedAt() { return createdAt; }
}