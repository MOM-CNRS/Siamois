package fr.siamois.domain.models.uiview;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * How the fiche of a record type is arranged for a whole project: its tabs (which ones, in which order,
 * under which name, and the list tabs added on top of the built-in ones). One row per organisation,
 * project, record type and record sub-type; a null project is for the types that belong to no project,
 * a null sub-type for every sub-type. Not a user's own state: that stays on the fiche itself.
 *
 * <p>The shape of {@code tabs} belongs to the front end; the server only bounds it.</p>
 */
@Entity
@Table(name = "fiche_config")
@Getter
@Setter
@NoArgsConstructor
public class FicheConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_institution", nullable = false)
    private Institution institution;

    /** The project the configuration is for, or null for a record type that belongs to no project. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_project")
    private ActionUnit project;

    /** The key of the entity type in the front registry ("recordingUnit", "project"…). */
    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    /** The sub-type (a recording unit type…), or null when the configuration is for every sub-type. */
    private Long typeId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> tabs;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_updated_by")
    private Person updatedBy;

    @UpdateTimestamp
    private Instant updatedAt;
}
