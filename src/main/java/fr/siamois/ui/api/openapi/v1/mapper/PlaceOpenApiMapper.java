package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.place.PlaceResource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlaceOpenApiMapper {

    private final LabelService labelService;

    public PlaceResource toResource(SpatialUnitDTO dto, String lang) {
        if (dto == null) {
            return null;
        }
        PlaceResource resource = new PlaceResource();
        resource.setResourceType("places");
        if (dto.getId() != null) {
            resource.setId(String.valueOf(dto.getId()));
        }
        resource.setName(dto.getName());

        ConceptDTO category = dto.getCategory();
        if (category != null) {
            ResolvedConceptResource typeRef = new ResolvedConceptResource();
            typeRef.setResourceType("concepts");
            typeRef.setId(String.valueOf(category.getId()));
            typeRef.setExternalUrl(category.getExternalId());
            String effectiveLang = (lang == null || lang.isBlank()) ? "fr" : lang;
            typeRef.setResolvedLabel(labelService.findLabelOf(category, effectiveLang).getLabel());
            resource.setType(typeRef);
        }

        InstitutionDTO institution = dto.getCreatedByInstitution();
        if (institution != null && institution.getId() != null) {
            OrganizationResourceIdentifier orgRef = new OrganizationResourceIdentifier();
            orgRef.setResourceType("organizations");
            orgRef.setId(String.valueOf(institution.getId()));
            resource.setOrganization(orgRef);
        }

        return resource;
    }
}
