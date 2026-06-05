package fr.siamois.dto.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class PhaseDTO extends AbstractEntityDTO {

    private String identifier;
    private ActionUnitSummaryDTO actionUnit;
    private ConceptDTO type;
    private String title;
    private String description;
    private Integer orderNumber;
    private Integer lowerBound;
    private Integer upperBound;
    private Set<ConceptDTO> periods;
    private Set<ConceptDTO> keywords;

    public static List<String> getBindableFieldNames() {
        return List.of(
                "identifier",
                "type",
                "title",
                "description",
                "orderNumber",
                "lowerBound",
                "upperBound",
                "periods",
                "keywords"
        );
    }
}
