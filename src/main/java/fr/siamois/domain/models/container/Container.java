package fr.siamois.domain.models.container;


import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.specimen.form.SpecimenDetailsForm;
import fr.siamois.domain.models.specimen.form.SpecimenNewUnitForm;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;

import java.util.HashSet;
import java.util.Set;

@Data
@Entity
@Table(name = "container")
@Audited
@NoArgsConstructor
public class Container extends TraceableEntity {

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<Container> children = new HashSet<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_type")
    protected Concept type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_spatial_unit_id")
    protected SpatialUnit spatialUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_parent_id", referencedColumnName = "container_id")
    @JsonIgnore
    private Container parent;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "container_id", nullable = false)
    private Long id;

    @NotNull
    @Column(name = "identifier")
    protected String identifier;

    @Transient
    @JsonIgnore
    public static final CustomForm DETAILS_FORM = SpecimenDetailsForm.build();

    @Transient
    @JsonIgnore
    public static final CustomForm NEW_UNIT_FORM = SpecimenNewUnitForm.build();

}

