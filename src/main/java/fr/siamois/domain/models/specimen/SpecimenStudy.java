package fr.siamois.domain.models.specimen;


import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.document.Document;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.envers.Audited;

import java.util.HashSet;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@Table(name = "specimen_study")
@Audited
public class SpecimenStudy extends SpecimenStudyParent implements ArkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "specimen_study_id", nullable = false)
    private Long id;


    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "specimen_study_document",
            joinColumns = { @JoinColumn(name = "fk_specimen_study_id")},
            inverseJoinColumns = { @JoinColumn(name = "fk_document_id") }
    )
    private Set<Document> documents = new HashSet<>();


}