package fr.siamois.domain.models;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Entity
@Table(name = "bookmark", schema = "public")
@NoArgsConstructor
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bookmark_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Institution institution;

    @Column(name = "title_code", nullable = false)
    private String titleCode;

    @Column(name = "resource_uri", nullable = false, length = 2000)
    private String resourceUri;

    @Override
    public String toString() {
        return String.format("Bookmark n°%s to %s", id, resourceUri);
    }

    private static final Map<String, String> COLOR_MAP = Map.of(
            "/spatial-unit", "var(--context-main-color)",
            "/action-unit", "var(--context-main-color)",
            "/recording-unit", "var(--ground-main-color)",
            "/specimen", "var(--ground-main-color)"
    );

    private static final Map<String, String> ICON_MAP = Map.of(
            "/spatial-unit", "bi bi-geo-alt",
            "/action-unit", "bi bi-arrow-down-square",
            "/recording-unit", "bi bi-pencil-square",
            "/specimen", "bi bi-bucket"
    );

    public String getBookmarkColor() {
        return COLOR_MAP.entrySet().stream()
                .filter(entry -> resourceUri.startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("var(--siamois-green)");
    }

    public String getBookmarkIcon() {
        return ICON_MAP.entrySet().stream()
                .filter(entry -> resourceUri.startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("bi bi-bookmark");
    }
}
