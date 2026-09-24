package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResourceCounts;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;

@Mapper(uses = ConversionServiceAdapter.class,
        componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public abstract class RecordingUnitResponseMapper implements Converter<RecordingUnitDTO, RecordingUnitResource> {

    @Autowired
    protected LabelService labelService;

    @Mapping(target = "resourceType", constant = "recording-units")
    @Mapping(target = "id", expression = "java(String.valueOf(dto.getId()))")
    @Mapping(target = "projectId", expression = "java(dto.getActionUnit() != null ? String.valueOf(dto.getActionUnit().getId()) : null)")
    @Mapping(target = "organization", expression = "java(toOrganizationIdentifier(dto.getCreatedByInstitution()))")
    @Mapping(target = "type", source = "type")
    @Mapping(target = "geom", source = "geom")
    @Mapping(target = "answers", expression = "java(java.util.Map.of())")
    @Mapping(target = "count", expression = "java(toResourceCounts(dto))")
    @Mapping(target = "links", expression = "java(fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResourceLinks.of(dto.getFullIdentifier()))")
    @Mapping(target = "resourceUri", expression = "java(dto.getId() != null ? \"/recording-unit/\" + dto.getId() : null)")
    @Mapping(target = "bookmarked", ignore = true)
    public abstract RecordingUnitResource convert(RecordingUnitDTO dto);

    @Mapping(target = "resourceType", constant = "recording-units")
    @Mapping(target = "id", expression = "java(String.valueOf(dto.getId()))")
    @Mapping(target = "projectId", ignore = true)
    @Mapping(target = "organization", ignore = true)
    @Mapping(target = "syncRevision", ignore = true)
    @Mapping(target = "type", source = "type")
    @Mapping(target = "geom", ignore = true)
    @Mapping(target = "answers", expression = "java(java.util.Map.of())")
    @Mapping(target = "count", expression = "java(new fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResourceCounts(null, null, null, null, null))")
    @Mapping(target = "links", expression = "java(fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResourceLinks.of(dto.getFullIdentifier()))")
    @Mapping(target = "resourceUri", expression = "java(dto.getId() != null ? \"/recording-unit/\" + dto.getId() : null)")
    @Mapping(target = "bookmarked", ignore = true)
    public abstract RecordingUnitResource toResource(RecordingUnitSummaryDTO dto);

    OrganizationResourceIdentifier toOrganizationIdentifier(InstitutionDTO institution) {
        if (institution == null || institution.getId() == null) return null;
        OrganizationResourceIdentifier org = new OrganizationResourceIdentifier();
        org.setResourceType("organizations");
        org.setId(String.valueOf(institution.getId()));
        return org;
    }

    ResolvedConceptResource toResolvedConcept(ConceptDTO concept) {
        if (concept == null) return null;
        ResolvedConceptResource r = new ResolvedConceptResource();
        r.setResourceType("concepts");
        r.setId(String.valueOf(concept.getId()));
        r.setExternalUrl(concept.getExternalId());
        r.setResolvedLabel(labelService.findLabelOf(concept, "fr").getLabel());
        return r;
    }

    protected RecordingUnitResourceCounts toResourceCounts(RecordingUnitDTO dto) {
        return new RecordingUnitResourceCounts(
                dto.getChildrenCount() != null ? (long) dto.getChildrenCount() : null,
                dto.getSpecimenCount(),
                dto.getParentsCount() != null ? (long) dto.getParentsCount() : null,
                null,
                dto.getRelationshipCount()
        );
    }
}
