package fr.siamois.ui.api.openapi.v1.resource.document;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The {@code _counts} of an entity that has nothing to count but its documents (find, phase, container). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EntityDocumentCounts {

    @Schema(description = "Nombre de documents liés")
    private Long documents;
}
