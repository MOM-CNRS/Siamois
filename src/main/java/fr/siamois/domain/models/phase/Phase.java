package fr.siamois.domain.models.phase;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.FieldCode;
import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.phase.form.PhaseDetailsForm;
import fr.siamois.domain.models.phase.form.PhaseNewUnitForm;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.util.HashSet;
import java.util.Set;

@Data
@Entity
@Table(name = "phase")
@Audited
@NoArgsConstructor
public class Phase extends TraceableEntity {

    @FieldCode
    public static final String TYPE_FIELD = "SIAPHASE.TYPE";

    @FieldCode
    public static final String PERIOD_FIELD = "SIAPHASE.PERIOD";

    @FieldCode
    public static final String KEYWORD_FIELD = "SIAPHASE.KEYWORD";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "phase_id", nullable = false)
    private Long id;

    @NotNull
    @Column(name = "identifier")
    private String identifier;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_type")
    private Concept type;

    @Column(name = "title")
    private String title;

    @Column(name = "description", length = 5000)
    private String description;

    @Column(name = "order_number")
    private Integer orderNumber;

    @Column(name = "lower_bound")
    private Integer lowerBound;

    @Column(name = "upper_bound")
    private Integer upperBound;

    @ManyToMany
    @JoinTable(
            name = "phase_period",
            joinColumns = @JoinColumn(name = "fk_phase_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_concept_id")
    )
    @NotAudited
    private Set<Concept> periods = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "phase_keyword",
            joinColumns = @JoinColumn(name = "fk_phase_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_concept_id")
    )
    @NotAudited
    private Set<Concept> keywords = new HashSet<>();

    @Transient
    @JsonIgnore
    public static final CustomForm DETAILS_FORM = PhaseDetailsForm.build();

    @Transient
    @JsonIgnore
    public static final CustomForm NEW_UNIT_FORM = PhaseNewUnitForm.build();
}
