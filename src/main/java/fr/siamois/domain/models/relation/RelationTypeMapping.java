package fr.siamois.domain.models.relation;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * The correspondence between the type chosen on the source side and the one on the target side
 * ("cuts" ↔ "is cut by"), suggested or imposed. Carried by the definition, not deduced from the
 * thesaurus (a thesaurus edit must not break the mirror).
 */
@Entity
@Table(name = "relation_type_mapping", uniqueConstraints = {
        @UniqueConstraint(name = "uk_relation_type_mapping",
                columnNames = {"fk_relation_definition_id", "fk_source_type_concept_id", "fk_target_type_concept_id"})
})
@Getter
@Setter
public class RelationTypeMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "relation_type_mapping_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "fk_relation_definition_id", nullable = false)
    private RelationDefinition definition;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_source_type_concept_id", nullable = false)
    private Concept sourceType;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_target_type_concept_id", nullable = false)
    private Concept targetType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 10)
    private RelationMappingMode mode = RelationMappingMode.IMPOSED;
}
