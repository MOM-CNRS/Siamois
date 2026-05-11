package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipCountOnly;
import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipToMany;
import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipToOne;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.project.PlaceResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProjectResponseMapper {

    private final ConceptResourceIdentifierMapper conceptResourceIdentifierMapper;

    public ProjectResource toResource(AccessibleProjectForApi row) {
        var dto = row.actionUnit();
        ProjectResource r = new ProjectResource();
        r.setResourceType("projects");
        if (dto.getId() != null) {
            r.setId(String.valueOf(dto.getId()));
        }
        r.setName(dto.getName());
        r.setIdentifier(dto.getIdentifier());
        r.setRecordingUnitIdentifierFormat(dto.getRecordingUnitIdentifierFormat());
        r.setFullIdentifier(dto.getFullIdentifier());
        r.setMaxRecordingUnitCode(dto.getMaxRecordingUnitCode());
        r.setMinRecordingUnitCode(dto.getMinRecordingUnitCode());
        r.setRecordingUnitIdentifierLang(dto.getRecordingUnitIdentifierLang());
        r.setBeginDate(dto.getBeginDate());
        r.setEndDate(dto.getEndDate());

        if (dto.getType() != null) {
            r.setType(new RelationshipToOne<>(conceptResourceIdentifierMapper.convert(dto.getType())));
        }
        if (dto.getMainLocation() != null) {
            r.setMainLocation(new RelationshipToOne<>(toPlaceIdentifier(dto.getMainLocation())));
        }
        if (dto.getSpatialContext() != null && !dto.getSpatialContext().isEmpty()) {
            List<PlaceResourceIdentifier> places = new ArrayList<>();
            for (SpatialUnitSummaryDTO su : dto.getSpatialContext()) {
                places.add(toPlaceIdentifier(su));
            }
            r.setSpatialContext(new RelationshipToMany<>(places));
        }
        if (dto.getCreatedByInstitution() != null) {
            OrganizationResourceIdentifier org = new OrganizationResourceIdentifier();
            org.setResourceType("organizations");
            org.setId(String.valueOf(dto.getCreatedByInstitution().getId()));
            r.setOrganization(new RelationshipToOne<>(org));
        }

        r.setChildren(new RelationshipCountOnly(row.childActionUnitCount()));
        r.setRecordingUnitList(new RelationshipCountOnly(row.recordingUnitCount()));

        return r;
    }

    private static PlaceResourceIdentifier toPlaceIdentifier(SpatialUnitSummaryDTO su) {
        PlaceResourceIdentifier p = new PlaceResourceIdentifier();
        p.setResourceType("places");
        if (su.getId() != null) {
            p.setId(String.valueOf(su.getId()));
        }
        return p;
    }
}
