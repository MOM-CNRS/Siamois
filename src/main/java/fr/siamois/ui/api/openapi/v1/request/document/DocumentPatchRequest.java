package fr.siamois.ui.api.openapi.v1.request.document;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Mise à jour d'un document. Deux formes, jamais mélangées : les champs à plat du client mobile
 * (titre, description, concepts nature / échelle / format) ou, pour le client web, {@code answers}
 * (fusion partielle des champs du formulaire, comme les autres entités) et {@code validated}.
 * Dès que {@code answers} ou {@code validated} est présent, les champs à plat sont ignorés.
 */
@Data
@Schema(description = "Mise à jour d'un document")
public class DocumentPatchRequest {

    @Schema(description = "Titre du document (mobile)")
    private String title;

    @Schema(description = "Description (mobile)")
    private String description;

    @Schema(description = "Identifiant du concept nature (SIAD.NATURE) (mobile)")
    private Long natureConceptId;

    @Schema(description = "Identifiant du concept échelle (SIAD.SCALE) (mobile)")
    private Long scaleConceptId;

    @Schema(description = "Identifiant du concept format (SIAD.FORMAT) (mobile)")
    private Long formatConceptId;

    @Schema(description = "Nouveau statut de validation, absent = inchangé : INCOMPLETE, COMPLETE, CANCELLED avec le droit "
            + "de modification ; VALIDATED — l'atteindre ou le quitter — avec le droit validateur.")
    private ValidationStatus validated;

    @Schema(description = "Valeurs par fieldId ({ value } / { values })")
    private Map<String, AnswerInput> answers = new HashMap<>();

    /** True when the request is the web client's form (answers or status), not the mobile flat fields. */
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isFormPatch() {
        return validated != null || (answers != null && !answers.isEmpty());
    }
}
