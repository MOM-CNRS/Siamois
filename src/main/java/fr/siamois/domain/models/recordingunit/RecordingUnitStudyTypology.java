package fr.siamois.domain.models.recordingunit;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "ru_study_typology")
@Getter
@Audited
public class RecordingUnitStudyTypology {

    @Data
    @Embeddable
    public static class RecordingUnitStudyTypologyId {
        @Column(name = "fk_ru_study_id")
        private Long recordingUnitStudyId;

        @Column(name = "fk_typology_concept_id")
        private Long typologyConceptId;

        @Column(name = "fk_question_concept_id")
        private Long questionConceptId;
    }

    @EmbeddedId
    private RecordingUnitStudyTypologyId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("recordingUnitStudyId")
    @JoinColumn(name = "fk_ru_study_id")
    private RecordingUnitStudy recordingUnitStudy;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("typologyConceptId")
    @JoinColumn(name = "fk_typology_concept_id")
    private Concept typologyConcept;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("questionConceptId")
    @JoinColumn(name = "fk_question_concept_id")
    private Concept questionConcept;
}
