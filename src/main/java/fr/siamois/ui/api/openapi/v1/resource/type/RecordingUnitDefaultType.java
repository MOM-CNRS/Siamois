package fr.siamois.ui.api.openapi.v1.resource.type;

import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import fr.siamois.ui.api.openapi.v1.resource.form.FormResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectTableColumnResource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class RecordingUnitDefaultType {
    private FormResource formBundle;
    private RecordingUnitIdentifierConfig identifierConfig;
    @Schema(description = "Champs indexés par identifiant custom_field (chaîne numérique)")
    Map<String, FieldResource> fields;
    @Schema(description = "Défauts d'affichage des colonnes de la liste des unités d'enregistrement " +
            "(RecordingUnitTableColumnDefaults, source unique de vérité partagée avec la table JSF)")
    List<ProjectTableColumnResource> tableColumns;
}
