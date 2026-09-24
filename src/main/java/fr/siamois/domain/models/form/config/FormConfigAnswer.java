package fr.siamois.domain.models.form.config;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Set;

@Data
@Entity
// One answer set per (entity, form config): answers belong to the entity, not to whoever typed them
// (fk_person_id is only the last author). Each constraint leads with the entity column so it also
// serves the list sort/filter subqueries that correlate on it; NULLs are distinct in Postgres, so
// the rows of the other three entity kinds never collide.
@Table(name = "form_config_answer", uniqueConstraints = {
        @UniqueConstraint(name = "uk_form_config_answer_recording_unit", columnNames = {"fk_recording_unit_id", "fk_form_config_id"}),
        @UniqueConstraint(name = "uk_form_config_answer_specimen", columnNames = {"fk_specimen_id", "fk_form_config_id"}),
        @UniqueConstraint(name = "uk_form_config_answer_phase", columnNames = {"fk_phase_id", "fk_form_config_id"}),
        @UniqueConstraint(name = "uk_form_config_answer_container", columnNames = {"fk_container_id", "fk_form_config_id"})
})
@NoArgsConstructor
@AllArgsConstructor
public class FormConfigAnswer {

    @NonNull
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "form_config_answer_id", nullable = false)
    private Long id;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_person_id", nullable = false)
    private Person person;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_form_config_id", nullable = false)
    private FormConfig formConfig;

    @NonNull
    @OneToMany(mappedBy = "formConfigAnswer", fetch = FetchType.LAZY)
    private Set<CustomFieldAnswer> answers;

    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_recording_unit_id")
    private RecordingUnit recordingUnit;

    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_specimen_id")
    private Specimen specimen;

    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_phase_id")
    private Phase phase;

    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_container_id")
    private Container container;

}
