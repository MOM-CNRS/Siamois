package fr.siamois.ui.api.openapi.v1.response.project.type;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import fr.siamois.ui.api.openapi.v1.resource.type.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/** The project's types of document, each with its own form, plus the flat catalog of their fields. */
@EqualsAndHashCode(callSuper = true)
@Getter
public class ProjectDocumentTypeListResponse extends Response<List<DocumentType>> {

    @Schema(description = "Catalogue de champs, union des fields de chaque type, indexé par identifiant custom_field")
    private final Map<String, FieldResource> fields;

    public ProjectDocumentTypeListResponse(List<DocumentType> data, Map<String, FieldResource> fields) {
        super(data);
        this.fields = fields;
    }
}
