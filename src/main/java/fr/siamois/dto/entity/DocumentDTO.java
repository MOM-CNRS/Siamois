package fr.siamois.dto.entity;

import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * A document, as the form and the lists see it. The stored file's own columns (code, hash, stored
 * name, internal URL) are carried for reading only: they are written by the file upload, never by
 * saving the form.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class DocumentDTO extends AbstractEntityDTO {

    private String identifier;
    private Integer generatedNumber;
    private String otherIdentifiers;
    private ActionUnitSummaryDTO actionUnit;
    private ConceptDTO category;
    private ConceptDTO documentType;
    private Set<ConceptDTO> supportNatures;
    private String format;
    private String title;
    private List<PersonDTO> authors;
    private List<PersonDTO> contributors;
    private String publisher;
    private OffsetDateTime productionDate;
    private String description;
    private ConceptDTO language;
    private String rights;
    private Set<ConceptDTO> keywords;
    private Integer itemCount;
    private BigDecimal sizeMb;
    private String crs;
    private String originalPath;
    private String comments;
    private ConceptDTO scale;

    private String externalUrl;
    private String fileName;
    private String mimeType;
    private Long size;
    private String fileCode;
    private String md5Sum;
    private String url;

    private Set<RecordingUnitSummaryDTO> recordingUnits;
    private Set<SpecimenSummaryDTO> finds;
    private Set<SpatialUnitSummaryDTO> places;
    private Set<PhaseDTO> phases;
    private Set<ContainerDTO> containers;

    public static List<String> getBindableFieldNames() {
        return List.of(
                "identifier",
                "generatedNumber",
                "otherIdentifiers",
                "actionUnit",
                "category",
                "documentType",
                "supportNatures",
                "format",
                "title",
                "authors",
                "contributors",
                "publisher",
                "productionDate",
                "description",
                "language",
                "rights",
                "keywords",
                "itemCount",
                "sizeMb",
                "crs",
                "originalPath",
                "comments",
                "externalUrl",
                "recordingUnits",
                "finds",
                "places",
                "phases",
                "containers"
        );
    }
}
