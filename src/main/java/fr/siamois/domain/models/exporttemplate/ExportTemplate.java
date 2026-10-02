package fr.siamois.domain.models.exporttemplate;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Modèle d'export d'une institution. Les colonnes sont locales (institution, projet de référence,
 * auteur, dates) ; seule la colonne {@link #definition} est la partie partageable, au format de
 * {@link ExportTemplateJson}. {@code templateUuid}, {@code name} et {@code version} en sont des
 * copies pour lister sans parser : le service les resynchronise à chaque écriture.
 */
@Entity
@Table(
        name = "export_template",
        uniqueConstraints = @UniqueConstraint(columnNames = {"fk_institution_id", "template_uuid"}))
@Getter
@Setter
@NoArgsConstructor
public class ExportTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "export_template_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_institution_id", nullable = false)
    private Institution institution;

    @Column(name = "template_uuid", nullable = false, length = 100)
    private String templateUuid;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 50)
    private String version;

    /** Projet utilisé par l'éditeur (choix des champs, aperçu) — propre à cette instance, jamais partagé. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_reference_project_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private ActionUnit referenceProject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_created_by", nullable = false)
    private Person createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Partie partageable, au format {@link ExportTemplateJson}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition", columnDefinition = "jsonb", nullable = false)
    private String definition;
}
