package fr.siamois.domain.models.recordingunit.identifier;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import jakarta.ws.rs.DefaultValue;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

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

    @JoinColumn(name = "fk_recording_unit_id")
    @ManyToOne(fetch = FetchType.EAGER)
    @Nullable
    private RecordingUnit recordingUnit;

    @JoinColumn(name = "fk_concept_type_id", nullable = false)
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @NonNull
    private Concept recordingUnitType;

    /**
     * The counter holds the next available number
     */
    @DefaultValue("1")
    private int counter = 1;

    @Nullable
    @Column(name = "format_length")
    private Integer formatLength = null;

}
