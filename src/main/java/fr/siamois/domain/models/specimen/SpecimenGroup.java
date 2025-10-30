package fr.siamois.domain.models.specimen;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Data
@Entity
@Table(name = "specimen_group")
@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
public class SpecimenGroup implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "specimen_group_id", nullable = false)
    private Long id;

    @NotNull
    @Column(name = "name", nullable = false, length = Integer.MAX_VALUE)
    private String name;

    @ManyToMany
    @JoinTable(
            name="specimen_group_attribution",
            joinColumns = { @JoinColumn(name = "fk_specimen_group_id") },
            inverseJoinColumns = { @JoinColumn(name = "fk_specimen_id") }
    )
    private Set<Specimen> specimens = new HashSet<>();


}