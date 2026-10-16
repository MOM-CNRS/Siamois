package fr.siamois.domain.models.relation;

import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A configurable relation between two kinds of entity: which entities it accepts on each side, how its
 * other end is mirrored and how each side is named. Its links live in {@code relation_link}.
 * <p>
 * Each side is named by a concept (the label of the field shown on the sheet, hence translated):
 * {@code sourceSideConcept} is the side of the entity that owns the link, {@code targetSideConcept}
 * the side of the linked entity. A {@link RelationMirror#SELF} or {@link RelationMirror#NONE} relation
 * has no target side concept.
 * <p>
 * No hierarchy option: a definition never makes the relation a tree (hierarchies stay structural tables).
 */
@Entity
@Table(name = "relation_definition", uniqueConstraints = {
        @UniqueConstraint(name = "uk_relation_definition_code", columnNames = "code")
})
@Getter
@Setter
public class RelationDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "relation_definition_id", nullable = false)
    private Long id;

    /** Stable code: how predefined definitions are found (and referenced from an export template). */
    @NotNull
    @Column(name = "code", nullable = false, length = 150)
    private String code;

    /** The owning institution, or {@code null} for a platform-provided definition. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_institution_id")
    private Institution institution;

    /** Platform-provided definitions cannot be deleted; only their labels and vocabularies are configurable. */
    @Column(name = "is_system", nullable = false)
    private boolean system;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "mirror", nullable = false, length = 10)
    private RelationMirror mirror = RelationMirror.NONE;

    /** Whether the mirror field can be edited too (read-only by default: it edits another scope's data). */
    @Column(name = "mirror_editable", nullable = false)
    private boolean mirrorEditable;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_source_side_concept_id", nullable = false)
    private Concept sourceSideConcept;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_target_side_concept_id")
    private Concept targetSideConcept;

    /**
     * The form of the link itself (its shared attributes, e.g. "uncertain"): an ordinary form configuration
     * whose fields' answers belong to the link ({@code form_config_answer.fk_relation_link_id}), seen identically
     * from both sides. {@code null} for a definition whose links carry no attribute.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_link_form_config_id")
    private FormConfig linkFormConfig;

    @OneToMany(mappedBy = "definition", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RelationDefinitionKind> kinds = new ArrayList<>();

    /** The accepted kinds (and entity config types) of one side. */
    public List<RelationDefinitionKind> kindsOf(RelationSide side) {
        return kinds.stream().filter(k -> k.getSide() == side).toList();
    }

    public void addKind(RelationSide side, RelationEntityKind entityKind, Concept typeConcept) {
        RelationDefinitionKind kind = new RelationDefinitionKind();
        kind.setDefinition(this);
        kind.setSide(side);
        kind.setEntityKind(entityKind);
        kind.setTypeConcept(typeConcept);
        kinds.add(kind);
    }
}
