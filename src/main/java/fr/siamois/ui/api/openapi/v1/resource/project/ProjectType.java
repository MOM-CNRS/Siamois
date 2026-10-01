package fr.siamois.ui.api.openapi.v1.resource.project;

import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.form.FormResource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A real, per-type entry in {@code ProjectTypeListResponse.data} (plan §5/§6) — shaped like
 * {@link ProjectDefaultType} plus the type's own concept identity, mirroring how
 * {@code RecordingUnitType} relates to {@code RecordingUnitDefaultType}. Nothing constructs this
 * yet: Project isn't plugged into the {@code ConfigurableTable}/{@code FieldFormConfig} machinery
 * this phase, so {@code data} always serializes as {@code []}. Declared now (rather than typing
 * {@code data} as raw {@code Object}) so the contract is explicit for whoever picks that up.
 */
@Schema(description = "Un type de projet réel (non utilisé tant que Project n'a pas de types configurables)")
@Data
@NoArgsConstructor
public class ProjectType {
    private ResolvedConceptResource concept;
    @Schema(description = "Identifiant du concept de type")
    private String id;
    private FormResource form;
    @Schema(description = "Configuration des champs pour ce type, référence dans le catalogue fields")
    private List<ProjectFieldConfigResource> fieldConfigs;
}
