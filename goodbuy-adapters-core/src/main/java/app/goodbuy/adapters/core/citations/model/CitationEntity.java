package app.goodbuy.adapters.core.citations.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "citations")
public class CitationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private SourceEntity source;

    @Column(name = "url", nullable = false, unique = true, columnDefinition = "text")
    private String url;

    @Column(name = "title", columnDefinition = "text")
    private String title;

    @Column(name = "accessed_at", nullable = false)
    private OffsetDateTime accessedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (accessedAt == null) accessedAt = now;
    }

    public Long getId() { return id; }

    public SourceEntity getSource() { return source; }
    public void setSource(SourceEntity source) { this.source = source; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public OffsetDateTime getAccessedAt() { return accessedAt; }
    public void setAccessedAt(OffsetDateTime accessedAt) { this.accessedAt = accessedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
