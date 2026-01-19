package fr.siamois.domain.models.recordingunit.identifier;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.FetchType.LAZY;

/**
 * This class stores the raw information pertaining to the identifier format of a {@link RecordingUnit}.
 * Not all fields are necessarily displayed in the final identifier, but they are preserved
 * to ensure no data loss occurs if the identifier format is modified in the future.
 */
@Data
@Entity
@Table(name = "identifier_ru_info")
@NoArgsConstructor
public class RecordingUnitIdInfo {

    // L'annotation @Id est déplacée sur un champ dédié.
    // Sa valeur sera copiée depuis l'ID de l'entité RecordingUnit associée.
    // J'ai supposé que l'ID de RecordingUnit est de type Long. Adaptez ce type si nécessaire (ex: UUID, Integer...).
    @Id
    private Long recordingUnitId;

    @OneToOne(fetch = LAZY)
    @MapsId("recordingUnitId") // Indique à JPA que le champ 'id' est mappé par la relation 'recordingUnit'.
    @JoinColumn(name = "fk_recording_unit_id")
    private RecordingUnit recordingUnit;

    private int ruNumber = 0;

    @JoinColumn(name = "fk_concept_type_id")
    @ManyToOne(fetch = LAZY)
    @Nullable
    private Concept ruType = null;

    @Nullable
    @ManyToOne(fetch = LAZY)
    private RecordingUnit parent = null;

    @JoinColumn(name = "fk_parent_concept_type_id")
    @ManyToOne(fetch = LAZY)
    @Nullable
    private Concept ruParentType = null;

    @Nullable
    @Column(name = "parent_su_number")
    private Integer spatialUnitNumber = null;

    @Nullable
    @JoinColumn(name = "fk_action_unit_id")
    @ManyToOne(fetch =  LAZY)
    private ActionUnit actionUnit;

}
