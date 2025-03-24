package fr.siamois.domain.models.specimen;


import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.FieldCode;
import fr.siamois.domain.models.document.Document;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashSet;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@Table(name = "specimen")
public class Specimen extends SpecimenParent implements ArkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "specimen_id", nullable = false)
    private Long id;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "specimen_document",
            joinColumns = @JoinColumn(name = "fk_document_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_specimen_id")
    )
    private transient Set<Document> documents = new HashSet<>();

    @FieldCode
    public static final String CATEGORY_FIELD = "SIAS.CATEGORY";

    @FieldCode
    public static final String METHOD_FIELD = "SIAS.METHOD";

}