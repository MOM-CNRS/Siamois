package fr.siamois.domain.models.specimen;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.util.Objects;

@Getter
@Setter
@Entity
@Table(name = "specimen_study_typology", schema = "public")
@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
public class SpecimenStudyTypology {
    @EmbeddedId
    private SpecimenStudyTypologyId id;

    @MapsId("fkSpecimenStudyId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_specimen_study_id", nullable = false)
    private SpecimenStudy fkSpecimenStudy;

    @MapsId("fkTypologyConceptId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_typology_concept_id", nullable = false)
    private Concept fkTypologyConcept;

    @MapsId("fkQuestionConceptId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_question_concept_id", nullable = false)
    private Concept fkQuestionConcept;

    @Embeddable
    @Getter
    public static class SpecimenStudyTypologyId {
        @NotNull
        @Column(name = "fk_specimen_study_id", nullable = false)
        private Long fkSpecimenStudyId;

        @NotNull
        @Column(name = "fk_typology_concept_id", nullable = false)
        private Long fkTypologyConceptId;

        @NotNull
        @Column(name = "fk_question_concept_id", nullable = false)
        private Long fkQuestionConceptId;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            SpecimenStudyTypologyId that = (SpecimenStudyTypologyId) o;
            return Objects.equals(getFkSpecimenStudyId(), that.getFkSpecimenStudyId()) && Objects.equals(getFkTypologyConceptId(), that.getFkTypologyConceptId()) && Objects.equals(getFkQuestionConceptId(), that.getFkQuestionConceptId());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getFkSpecimenStudyId(), getFkTypologyConceptId(), getFkQuestionConceptId());
        }
    }

}