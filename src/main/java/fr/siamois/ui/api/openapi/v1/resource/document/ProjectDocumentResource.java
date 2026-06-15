package fr.siamois.ui.api.openapi.v1.resource.document;

import fr.siamois.ui.api.openapi.v1.resource.concept.ConceptResourceIdentifier;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ProjectDocumentResource extends DocumentResourceIdentifier {

    private String title;
    private String description;
    private String fileName;
    private String mimeType;
    private String url;
    private String fileCode;
    private Long size;
    @Schema(description = "Empreinte MD5 du fichier")
    private String md5Sum;

    private ConceptResourceIdentifier nature;
    private ConceptResourceIdentifier scale;
    private ConceptResourceIdentifier format;
}
