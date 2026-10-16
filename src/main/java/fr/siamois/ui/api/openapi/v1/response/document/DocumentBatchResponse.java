package fr.siamois.ui.api.openapi.v1.response.document;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Réponse d'upload massif de documents")
public class DocumentBatchResponse {

    @Schema(description = "Résultats par item du manifeste")
    private List<DocumentBatchItemResult> results = new ArrayList<>();
}
