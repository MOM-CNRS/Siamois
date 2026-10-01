package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.domain.services.vocabulary.ConceptLabelBatchResolver;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.place.PlaceLightResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourceCounts;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourceLinks;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;


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
        return toResource(row, langCode, null);
    }

    /**
     * @param resolvedLabels libellés de concepts déjà résolus par lot pour toute la page
     *                       ({@code ConceptLabelBatchResolver}) ; {@code null} pour une réponse unitaire,
     *                       auquel cas on retombe sur {@link LabelService} champ par champ. Sur une page
     *                       de liste, passer la carte évite 1 à 2 requêtes par concept et par ligne.
     */
    public ProjectResource toResource(AccessibleProjectForApi row, String langCode,
                                      Map<Long, String> resolvedLabels) {
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
        r.setValidated(dto.getValidated());
        r.setGeom(dto.getGeom());

        if (dto.getType() != null) {
            r.setType(toConceptFieldValue(dto.getType(), lang, resolvedLabels));
        }

        if (dto.getMainLocation() != null) {
            r.setMainLocation(toPlaceLight(dto.getMainLocation()));
        }

        if (dto.getSpatialContext() != null && !dto.getSpatialContext().isEmpty()) {
            r.setSpatialContext(
                    dto.getSpatialContext().stream()
                            .map(ProjectResponseMapper::toPlaceLight)
                            .toList());
        }

        if (dto.getCreatedByInstitution() != null) {
            OrganizationResourceIdentifier org = new OrganizationResourceIdentifier();
            org.setResourceType("organizations");
            org.setId(String.valueOf(dto.getCreatedByInstitution().getId()));
            r.setOrganization(org);
        }

        // finds/phases/containers are left null here (list rows never show them) — set explicitly
        // by the detail endpoint, which is the only caller that needs the extra queries.
        r.setCount(new ProjectResourceCounts(row.childActionUnitCount(), row.recordingUnitCount(), null, null, null));
        if (r.getId() != null) {
            r.setLinks(ProjectResourceLinks.of(r.getId()));
        }
        r.setResourceUri(ProjectApiService.actionUnitResourceUri(dto.getId()));


        return r;
    }

    /**
     * Same as {@link #toResource(AccessibleProjectForApi, String)}, plus the {@code _permissions} and
     * {@code bookmarked} fields — computed by the caller (batched across a whole list page, or a single
     * check for a detail response), never re-derived here. This mapper stays a pure DTO->resource step
     * for the fields it already handled; it doesn't call ProfilePermissionService or BookmarkService
     * itself.
     */
    public ProjectResource toResource(AccessibleProjectForApi row, String langCode,
                                      ProjectResourcePermissions permissions, boolean bookmarked) {
        return toResource(row, langCode, permissions, bookmarked, null, null);
    }

    /**
     * Variante « ligne de liste » : ajoute les libellés résolus par lot et la projection {@code answers}
     * demandée via {@code ?fields=} ({@code ProjectAnswersProjector}). {@code answers} reste {@code null}
     * — et donc absent du JSON — quand aucune projection n'a été demandée.
     */
    public ProjectResource toResource(AccessibleProjectForApi row, String langCode,
                                      ProjectResourcePermissions permissions, boolean bookmarked,
                                      Map<Long, String> resolvedLabels, Map<String, Object> answers) {
        ProjectResource r = toResource(row, langCode, resolvedLabels);
        r.setPermissions(permissions);
        r.setBookmarked(bookmarked);
        r.setAnswers(answers);
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
        return toConceptFieldValue(concept, lang, null);
    }

    public ResolvedConceptResource toConceptFieldValue(ConceptDTO concept, String lang,
                                                       Map<Long, String> resolvedLabels) {
        if (concept == null) {
            return null;
        }
        ResolvedConceptResource v = new ResolvedConceptResource();
        v.setResourceType("concepts");
        v.setId(String.valueOf(concept.getId()));
        v.setExternalUrl(concept.getExternalId());
        String effectiveLang = (lang == null || lang.isBlank()) ? "fr" : lang;
        v.setResolvedLabel(resolvedLabels != null
                ? ConceptLabelBatchResolver.labelOf(concept, resolvedLabels)
                : labelService.findLabelOf(concept, effectiveLang).getLabel());
        return v;
    }
}
