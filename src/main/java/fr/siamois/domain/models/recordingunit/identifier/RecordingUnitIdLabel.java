package fr.siamois.domain.models.recordingunit.identifier;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import static jakarta.persistence.FetchType.LAZY;

@Data
@Entity
@Table(name = "identifier_ru_label")
@NoArgsConstructor
public class RecordingUnitIdLabel {

    public RecordingUnitIdLabel(Concept type, ActionUnit actionUnit, String existing) {
        this.type = type;
        this.conceptTypeId = type.getId();
        this.actionUnit = actionUnit;
        this.existing = existing;
    }

    @Id
    private Long conceptTypeId;

    @OneToOne(fetch = LAZY)
    @MapsId("conceptTypeId")
    @JoinColumn(name = "fk_concept_type_id")
    private Concept type;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "fk_action_unit_id")
    private ActionUnit actionUnit;

    private String existing;

}
