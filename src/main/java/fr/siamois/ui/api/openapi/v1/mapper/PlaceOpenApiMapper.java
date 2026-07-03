package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipToOne;
import fr.siamois.ui.api.openapi.v1.resource.concept.ConceptResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.place.PlaceResource;
import org.springframework.stereotype.Component;

@Component
public class PlaceOpenApiMapper {

    private final ConceptResourceIdentifierMapper conceptResourceIdentifierMapper;

    public PlaceOpenApiMapper(ConceptResourceIdentifierMapper conceptResourceIdentifierMapper) {
        this.conceptResourceIdentifierMapper = conceptResourceIdentifierMapper;
    }

    public PlaceResource toResource(SpatialUnitDTO dto) {
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
            ConceptResourceIdentifier typeRef = conceptResourceIdentifierMapper.convert(category);
            resource.setType(new RelationshipToOne<>(typeRef));
        }

        InstitutionDTO institution = dto.getCreatedByInstitution();
        if (institution != null && institution.getId() != null) {
            OrganizationResourceIdentifier orgRef = new OrganizationResourceIdentifier();
            orgRef.setResourceType("organizations");
            orgRef.setId(String.valueOf(institution.getId()));
            resource.setOrganization(new RelationshipToOne<>(orgRef));
        }

        return resource;
    }
}
