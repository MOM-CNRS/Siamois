package fr.siamois.ui.api.openapi.v1.response.project.type;

import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.type.DocumentDefaultType;
import fr.siamois.ui.api.openapi.v1.resource.type.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Getter
public class ProjectDocumentTypeListResponse extends Response<List<DocumentType>> {

    @JsonProperty("_default")
    @Schema(name = "_default", description = "Configuration du type de document (catégorie) par défaut (formulaire et identifiant) sans concept associé.")
    private final DocumentDefaultType defaultType;

    public ProjectDocumentTypeListResponse(List<DocumentType> data, DocumentDefaultType defaultType) {
        super(data);
        this.defaultType = defaultType;
    }
}
