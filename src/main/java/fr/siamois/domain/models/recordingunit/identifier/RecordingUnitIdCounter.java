package fr.siamois.domain.models.recordingunit.identifier;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import jakarta.ws.rs.DefaultValue;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * Each row in this table represents a counter.
 * A counter is associated with an ActionUnit that holds its configuration.
 * <p>
 * A counter is associated with a RecordingUnit. In this case, the counter stores the numbering for its direct children of the type defined by the associated Concept.
 * The RecordingUnit can be null, in which case it refers to RecordingUnits that do not have a parent.
 * The Concept can be null, in which case it refers to units that do not have a type as it's a technical limit.
 */
@Data
@Entity
@Table(name = "identifier_ru_counter")
@NoArgsConstructor
public class RecordingUnitIdCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ru_counter_id", nullable = false)
    @NonNull
    private Long id;

    @JoinColumn(name = "fk_action_unit_id")
    @ManyToOne(fetch = FetchType.LAZY)
    @NonNull
    private ActionUnit configActionUnit;

    @JoinColumn(name = "fk_recording_unit_id")
    @ManyToOne(fetch = FetchType.LAZY)
    @Nullable
    private RecordingUnit recordingUnit;

    /**
     * When the recording unit type is NULL.
     */
    @JoinColumn(name = "fk_concept_type_id")
    @ManyToOne(fetch = FetchType.LAZY)
    @Nullable
    private Concept recordingUnitType;

    /**
     * The counter holds the next available number
     */
    @DefaultValue("1")
    private int counter = 1;

}
