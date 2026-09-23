package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.ui.api.openapi.v1.resource.phase.PhaseResource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Phase OpenAPI ({@code phases}) à partir du DTO métier — pendant plat de {@link FindOpenApiMapper}
 * (pas MapStruct : Phase n'a que quelques propriétés de base à copier, l'essentiel de la ressource —
 * {@code answers}/{@code _permissions} — se construit au niveau service, en lot ou après résolution
 * de permission, pas ici).
 */
@Component
@RequiredArgsConstructor
public class PhaseOpenApiMapper {

    private final ProjectResponseMapper projectResponseMapper;

    public PhaseResource toResource(PhaseDTO phase, String lang, Map<Long, String> resolvedLabels) {
        PhaseResource r = new PhaseResource();
        r.setResourceType("phases");
        r.setId(phase.getId() != null ? String.valueOf(phase.getId()) : null);
        r.setIdentifier(phase.getIdentifier());
        r.setTitle(phase.getTitle());
        String label = phase.getTitle() != null && !phase.getTitle().isBlank() ? phase.getTitle() : phase.getIdentifier();
        r.setLabel(label);
        if (phase.getActionUnit() != null && phase.getActionUnit().getId() != null) {
            r.setProjectId(String.valueOf(phase.getActionUnit().getId()));
        }
        if (phase.getType() != null) {
            r.setType(projectResponseMapper.toConceptFieldValue(phase.getType(), lang, resolvedLabels));
        }
        if (phase.getId() != null) {
            r.setResourceUri("/phase/" + phase.getId());
        }
        return r;
    }
}
