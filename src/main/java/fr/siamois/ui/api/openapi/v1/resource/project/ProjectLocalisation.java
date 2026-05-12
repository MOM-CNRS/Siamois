package fr.siamois.ui.api.openapi.v1.resource.project;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Résumé localisation pour l'API projet : lieu principal (souvent commune) et lieux de contexte spatial.
 */
@Data
@NoArgsConstructor
public class ProjectLocalisation {

    @Schema(description = "Lieu principal du projet (ex. commune), à partir du lieu principal métier")
    private String communeOuLocalisation;

    @Schema(description = "Lieux de contexte spatial (libellés), hors doublon du lieu principal")
    private List<String> localisationsPrecises = new ArrayList<>();
}
