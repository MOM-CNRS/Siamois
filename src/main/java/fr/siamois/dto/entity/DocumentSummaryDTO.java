package fr.siamois.dto.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

/** A document as another entity refers to it: enough to show it and to open it. */
@EqualsAndHashCode(callSuper = true)
@Data
@RequiredArgsConstructor
public class DocumentSummaryDTO extends AbstractEntityDTO {

    private String identifier;
    private String title;

    public DocumentSummaryDTO(DocumentDTO dto) {
        super(dto);
        this.identifier = dto.getIdentifier();
        this.title = dto.getTitle();
    }

}
