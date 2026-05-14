package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.dto.entity.VocabularyDTO;
import fr.siamois.ui.api.openapi.v1.resource.project.ConceptFieldValue;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipCountOnly;
import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipToMany;
import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipToOne;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.project.PlaceResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectLocalisation;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectResponseMapper {

    private final ConceptResourceIdentifierMapper conceptResourceIdentifierMapper;
    private final ConceptMapper conceptMapper;
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
        r.setRecordingUnitIdentifierFormat(dto.getRecordingUnitIdentifierFormat());
        r.setFullIdentifier(dto.getFullIdentifier());
        r.setMaxRecordingUnitCode(dto.getMaxRecordingUnitCode());
        r.setMinRecordingUnitCode(dto.getMinRecordingUnitCode());
        r.setRecordingUnitIdentifierLang(dto.getRecordingUnitIdentifierLang());
        r.setBeginDate(dto.getBeginDate());
        r.setEndDate(dto.getEndDate());

        if (dto.getType() != null) {
            r.setType(new RelationshipToOne<>(conceptResourceIdentifierMapper.convert(dto.getType())));
            r.setCategorie(resolveCategoryLabel(dto.getType(), lang));
            r.setTypeConcept(toConceptFieldValue(dto.getType(), lang));
        }
        if (dto.getPrimaryActionCode() != null) {
            r.setCodeOperationArcheologique(dto.getPrimaryActionCode().getCode());
            if (dto.getPrimaryActionCode().getType() != null) {
                r.setActionCodeTypeConcept(toConceptFieldValue(dto.getPrimaryActionCode().getType(), lang));
            }
        }
        if (dto.getMainLocation() != null) {
            r.setMainLocation(new RelationshipToOne<>(toPlaceIdentifier(dto.getMainLocation())));
            if (dto.getMainLocation().getCategory() != null) {
                r.setMainLocationCategoryConcept(toConceptFieldValue(dto.getMainLocation().getCategory(), lang));
            }
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

        r.setLocalisation(buildLocalisation(dto));

        return r;
    }

    /**
     * Utilise l'entité {@link Concept} (via {@link ConceptMapper}) : le chemin DTO→Concept du {@code ConversionService}
     * utilisé par {@link LabelService#findLabelOf(fr.siamois.dto.entity.ConceptDTO, String)} n'est pas toujours enregistré,
     * ce qui provoquait des 500 sur l'API REST.
     */
    private String resolveCategoryLabel(ConceptDTO type, String lang) {
        try {
            Concept concept = conceptMapper.invertConvert(type);
            return labelService.findLabelOf(concept, lang).getLabel();
        } catch (RuntimeException e) {
            log.warn("Impossible de résoudre le libellé de catégorie pour le concept id={}", type.getId(), e);
            String ext = type.getExternalId();
            return ext != null ? "[" + ext + "]" : null;
        }
    }

    private static ProjectLocalisation buildLocalisation(ActionUnitDTO dto) {
        ProjectLocalisation loc = new ProjectLocalisation();
        String principal = dto.getMainLocation() != null
                ? formatSpatialUnitLabel(dto.getMainLocation())
                : null;
        loc.setCommuneOuLocalisation(principal);

        TreeSet<String> ordered = new TreeSet<>();
        if (dto.getSpatialContext() != null) {
            for (SpatialUnitSummaryDTO su : dto.getSpatialContext()) {
                String label = formatSpatialUnitLabel(su);
                if (label != null && !label.isBlank()) {
                    ordered.add(label);
                }
            }
        }
        if (principal != null && !principal.isBlank()) {
            ordered.remove(principal);
        }
        loc.setLocalisationsPrecises(new ArrayList<>(ordered));
        return loc;
    }

    /**
     * Libellé lisible pour un lieu (nom + code métier si distinct).
     */
    private static String formatSpatialUnitLabel(SpatialUnitSummaryDTO su) {
        if (su == null) {
            return null;
        }
        String name = su.getName();
        String code = su.getCode();
        boolean hasName = name != null && !name.isBlank();
        boolean hasCode = code != null && !code.isBlank();
        if (!hasName) {
            return hasCode ? code.trim() : null;
        }
        if (hasCode && !code.trim().equalsIgnoreCase(name.trim())) {
            return name.trim() + " (" + code.trim() + ")";
        }
        return name.trim();
    }

    private static PlaceResourceIdentifier toPlaceIdentifier(SpatialUnitSummaryDTO su) {
        PlaceResourceIdentifier p = new PlaceResourceIdentifier();
        p.setResourceType("places");
        if (su.getId() != null) {
            p.setId(String.valueOf(su.getId()));
        }
        return p;
    }

    private ConceptFieldValue toConceptFieldValue(ConceptDTO concept, String lang) {
        if (concept == null) {
            return null;
        }
        ConceptFieldValue v = new ConceptFieldValue();
        VocabularyDTO voc = concept.getVocabulary();
        if (voc != null) {
            v.setVocabularyId(voc.getId());
            v.setVocabularyExternalId(voc.getExternalVocabularyId());
            v.setVocabularyBaseUri(voc.getBaseUri());
            if (voc.getType() != null) {
                v.setVocabularyTypeLabel(voc.getType().getLabel());
            }
        }
        v.setConceptId(concept.getId());
        v.setConceptExternalId(concept.getExternalId());
        v.setDisplayLabel(resolveCategoryLabel(concept, lang));
        return v;
    }
}
