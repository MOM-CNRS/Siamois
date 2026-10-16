package fr.siamois.domain.models.relation;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * One accepted (kind of entity, config type) of one side of a {@link RelationDefinition}. A side accepts
 * a set of rows: with several rows of the same kind, any of their types is accepted ("a Fact or a Sounding",
 * two types of recording unit). A row without {@code typeConcept} accepts every type of that kind.
 * <p>
 * Rows, not columns, so that widening a side (more types, later more kinds) never changes the model.
 */
@Entity
@Table(name = "relation_definition_kind", uniqueConstraints = {
        @UniqueConstraint(name = "uk_relation_definition_kind",
                columnNames = {"fk_relation_definition_id", "side", "entity_kind", "fk_type_concept_id"})
})
@Getter
@Setter
public class RelationDefinitionKind {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "relation_definition_kind_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_relation_definition_id", nullable = false)
    private RelationDefinition definition;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 10)
    private RelationSide side;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_kind", nullable = false, length = 30)
    private RelationEntityKind entityKind;

    /** The accepted config type (e.g. the "Fact" recording unit type), or {@code null} for any type. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_type_concept_id")
    private Concept typeConcept;
}
