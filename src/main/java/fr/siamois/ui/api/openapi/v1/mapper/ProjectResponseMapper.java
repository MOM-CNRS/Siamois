package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.place.PlaceLightResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourceCounts;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourceLinks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectResponseMapper {

    private final LabelService labelService;

    public ProjectResource toResource(AccessibleProjectForApi row) {
        return toResource(row, "fr");
    }

    /**
     * @param langCode code langue ISO (ex. {@code fr}, {@code en}), utilisé pour le libellé de {@code categorie}
     */
    public ProjectResource toResource(AccessibleProjectForApi row, String langCode) {
        String lang = (langCode == null || langCode.isBlank()) ? "fr" : langCode.trim().toLowerCase();
        var dto = row.actionUnit();
        ProjectResource r = new ProjectResource();
        r.setResourceType("projects");
        if (dto.getId() != null) {
            r.setId(String.valueOf(dto.getId()));
        }
        r.setName(dto.getName());
        r.setIdentifier(dto.getIdentifier());

        r.setFullIdentifier(dto.getFullIdentifier());

        r.setBeginDate(dto.getBeginDate());
        r.setEndDate(dto.getEndDate());

        if (dto.getType() != null) {
            r.setType(toConceptFieldValue(dto.getType(), lang));
        }

        if (dto.getMainLocation() != null) {
            r.setMainLocation(toPlaceLight(dto.getMainLocation()));
        }

        if (dto.getCreatedByInstitution() != null) {
            OrganizationResourceIdentifier org = new OrganizationResourceIdentifier();
            org.setResourceType("organizations");
            org.setId(String.valueOf(dto.getCreatedByInstitution().getId()));
            r.setOrganization(org);
        }

        r.setCount(new ProjectResourceCounts(row.childActionUnitCount(), row.recordingUnitCount()));
        if (r.getId() != null) {
            r.setLinks(ProjectResourceLinks.of(r.getId()));
        }


        return r;
    }



    private static PlaceLightResource toPlaceLight(SpatialUnitSummaryDTO su) {
        PlaceLightResource p = new PlaceLightResource();
        p.setResourceType("places");
        if (su.getId() != null) {
            p.setId(String.valueOf(su.getId()));
            p.setName(su.getName());
        }
        return p;
    }

    public ResolvedConceptResource toConceptFieldValue(ConceptDTO concept, String lang) {
        if (concept == null) {
            return null;
        }
        ResolvedConceptResource v = new ResolvedConceptResource();
        v.setResourceType("concepts");
        v.setId(String.valueOf(concept.getId()));
        v.setExternalUrl(concept.getExternalId());
        String effectiveLang = (lang == null || lang.isBlank()) ? "fr" : lang;
        v.setResolvedLabel(labelService.findLabelOf(concept, effectiveLang).getLabel());
        return v;
    }
}
