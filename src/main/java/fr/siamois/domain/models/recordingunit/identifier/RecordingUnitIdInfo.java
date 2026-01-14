package fr.siamois.domain.models.recordingunit.identifier;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

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

    @Id
    @JoinColumn(name = "fk_recording_unit_id", nullable = false)
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private RecordingUnit recordingUnit;

    private int ruNumber = 0;

    @JoinColumn(name = "fk_concept_type_id")
    @ManyToOne(fetch = FetchType.EAGER)
    @Nullable
    private Concept ruType = null;

    @Nullable
    @Column(name = "parent_ru_number")
    private Integer parentRuNumber;

    @JoinColumn(name = "fk_parent_concept_type_id")
    @ManyToOne(fetch = FetchType.EAGER)
    @Nullable
    private Concept ruParentType = null;

    @Nullable
    @Column(name = "parent_su_number")
    private Integer spatialUnitNumber = null;

    @Nullable
    @Column(name = "action_unit_identifier")
    private String actionUnitIdentifier = null;

}
