package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.ui.api.openapi.v1.resource.sibling.SiblingsResource;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.spatialunit.SpatialUnitAlreadyExistsException;
import fr.siamois.domain.models.exceptions.spatialunit.SpatialUnitNotFoundException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.specs.SpatialUnitSpec;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.mapper.PlaceOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.request.place.PlaceCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.place.PlacePatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import fr.siamois.ui.api.openapi.v1.resource.form.FormResource;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import fr.siamois.ui.api.openapi.v1.resource.place.PlaceResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import fr.siamois.ui.api.openapi.v1.response.place.PlaceCreatedResponse;
import fr.siamois.ui.api.openapi.v1.response.spatialunit.PlaceListResponse;
import fr.siamois.ui.form.dto.FormUiDtoLayoutJson;
import fr.siamois.ui.form.fieldsource.FieldSource;
import fr.siamois.ui.form.fieldsource.PanelFieldSource;
import jakarta.persistence.DiscriminatorValue;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlaceOpenApiService {

    private final ProjectApiService projectApiService;
    private final InstitutionService institutionService;
    private final SpatialUnitService spatialUnitService;
    private final ConceptService conceptService;
    private final ConceptMapper conceptMapper;
    private final ProfilePermissionService profilePermissionService;
    private final PlaceOpenApiMapper placeOpenApiMapper;
    private final LangService langService;
    private final ResourceBookmarkService resourceBookmarkService;
    private final EntitySiblingsService entitySiblingsService;

    /**
     * {@code GET /api/v1/places?organizationId=…} — the React counterpart of JSF's
     * SpatialUnitListPanel. Places have no project, so unlike the other organization-wide lists
     * there is no per-project visibility rule and {@code _permissions} is one boolean for the page
     * ({@code ORGANIZATION_MANAGE_PLACES}, same as {@code SpatialUnitPanel#canUserEditUnit}).
     */
    @Transactional(readOnly = true)
    public PlaceListResponse listByOrganization(ProjectApiCaller caller,
                                              Long organizationId,
                                              int offset,
                                              int limit,
                                              String sortParam,
                                              String search,
                                              String lang) {
        if (organizationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "organizationId est obligatoire");
        }
        projectApiService.assertOrganizationInCallerScope(organizationId, caller.accessibleInstitutionIds());

        InstitutionDTO institution = institutionService.findById(organizationId);
        if (institution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organisation introuvable");
        }

        Sort sort = ProjectApiService.parsePlaceSort(sortParam);
        FilterDTO filter = new FilterDTO();
        if (search != null && !search.isBlank()) {
            filter.add(SpatialUnitSpec.NAME_FILTER, search, FilterDTO.FilterType.CONTAINS);
        }
        Page<SpatialUnitDTO> page = spatialUnitService.searchSpatialUnits(
                institution, filter, PageRequest.of(limit > 0 ? offset / limit : 0, limit, sort));

        UserInfo userInfo = new UserInfo(institution, caller.person(), lang);
        ProjectResourcePermissions permissions = ProjectResourcePermissions.of(
                profilePermissionService.hasOrganizationPermission(userInfo, PermissionConstants.ORGANIZATION_MANAGE_PLACES));

        var resources = page.getContent().stream()
                .map(dto -> {
                    PlaceResource resource = placeOpenApiMapper.toResource(dto, lang);
                    resource.setPermissions(permissions);
                    if (dto.getId() != null) {
                        resource.setResourceUri("/spatial-unit/" + dto.getId());
                    }
                    return resource;
                })
                .toList();

        resourceBookmarkService.markBookmarked(caller.person(), institution, resources, lang);
        ListMeta meta = new ListMeta(page.getTotalElements(), limit, (long) offset);
        return new PlaceListResponse(resources, meta);
    }

    /**
     * Détail d'un lieu ({@code GET /api/v1/places/{id}}), pour la fiche React du lot "Lieux" —
     * jusqu'ici cet endpoint était un stub 501 (rien d'autre ne l'appelait ; la liste/le formulaire
     * de création passent par {@link #listByOrganization}/{@link #createPlace}, qui n'en ont pas
     * besoin). {@code SpatialUnit} n'a pas de catalogue de types configurable (absent de
     * {@code ConfigurableTable}) : pas de {@code GET .../place-types} séparé — le layout et le
     * catalogue de champs de {@link SpatialUnit#DETAILS_FORM} (statique, jamais résolu par type)
     * sont attachés directement à cette réponse.
     *
     * <p>{@code answers} ne couvre pas le champ adresse ({@code CustomFieldSelectOneAddress}) :
     * {@code FullAddress} est un objet composite sans équivalent {@link ResourceRef} simple, et
     * rien ne l'édite encore côté React (la fiche saute ce champ — voir PlaceFicheTab.tsx). Un
     * gap connu, pas un oubli.</p>
     */
    @Transactional(readOnly = true)
    public PlaceResource getPlaceById(ProjectApiCaller caller, long placeId, String lang) {
        SpatialUnitDTO dto = requireAccessiblePlace(caller, placeId);
        PlaceResource resource = placeOpenApiMapper.toResource(dto, lang);

        Locale locale = langService.localeForApiLang(lang);
        FieldSource fieldSource = new PanelFieldSource(SpatialUnit.DETAILS_FORM);
        resource.setFormBundle(new FormResource(FormUiDtoLayoutJson.serialize(SpatialUnit.DETAILS_FORM.getLayout())));
        resource.setFields(buildPlaceFieldsMetadataOnly(fieldSource, locale));
        // resource.getType() is already the resolved concept ref (placeOpenApiMapper.toResource
        // above) — reused here rather than re-resolving the same label a second time.
        resource.setAnswers(buildPlaceAnswers(dto, resource.getType()));

        InstitutionDTO institution = dto.getCreatedByInstitution();
        UserInfo userInfo = new UserInfo(institution, caller.person(), lang);
        boolean canEdit = profilePermissionService.hasOrganizationPermission(userInfo, PermissionConstants.ORGANIZATION_MANAGE_PLACES);
        resource.setPermissions(ProjectResourcePermissions.of(canEdit));
        if (dto.getId() != null) {
            resource.setResourceUri("/spatial-unit/" + dto.getId());
        }
        resourceBookmarkService.markBookmarked(caller.person(), institution, resource, lang);
        return resource;
    }

    /**
     * Valeurs brutes des champs système de {@link SpatialUnit#DETAILS_FORM}, indexées par id de
     * champ — même convention que {@code PhaseResource}/{@code ContainerResource}.answers (aucune
     * ligne {@code CustomFieldAnswer} pour un lieu). Le champ adresse est délibérément absent (voir
     * {@link #getPlaceById}'s own javadoc).
     */
    private Map<String, Object> buildPlaceAnswers(SpatialUnitDTO dto,
                                                   fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource resolvedType) {
        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put(String.valueOf(SpatialUnit.NAME_FIELD.getId()), dto.getName());
        answers.put(String.valueOf(SpatialUnit.CODE_FIELD.getId()), dto.getCode());
        answers.put(String.valueOf(SpatialUnit.PLACE_NUMBER_FIELD.getId()), dto.getPlaceNumber());
        if (dto.getCategory() != null && resolvedType != null) {
            answers.put(String.valueOf(SpatialUnit.SPATIAL_UNIT_TYPE_FIELD.getId()),
                    new ResourceRef(resolvedType.getId(), "concepts", resolvedType.getResolvedLabel()));
        }
        return answers;
    }

    private Map<String, FieldResource> buildPlaceFieldsMetadataOnly(FieldSource fieldSource, Locale locale) {
        Map<String, FieldResource> fields = new LinkedHashMap<>();
        for (CustomField field : fieldSource.getAllFields()) {
            if (field == null || field.getId() == null) continue;
            fields.put(String.valueOf(field.getId()), toPlaceFieldResource(field, locale));
        }
        return fields;
    }

    private FieldResource toPlaceFieldResource(CustomField field, Locale locale) {
        String label = langService.resolveMessage(field.getLabel(), locale);
        String hint = langService.resolveMessage(field.getHint(), locale);
        String fieldCode = field instanceof CustomFieldSelectOneFromFieldCode one ? one.getFieldCode() : null;
        DiscriminatorValue dv = field.getClass().getAnnotation(DiscriminatorValue.class);
        String answerType = dv != null ? dv.value() : field.getClass().getSimpleName();
        return new FieldResource(
                String.valueOf(field.getId()),
                "fields",
                label,
                answerType,
                hint,
                field.getIsSystemField(),
                field.getValueBinding(),
                fieldCode,
                field instanceof CustomFieldText text ? text.getIsTextArea() : null,
                field.getIcon(),
                field.getConceptUri());
    }

    @Transactional
    public PlaceCreatedResponse.PlaceCreatedItem createPlace(ProjectApiCaller caller,
                                                             PlaceCreateRequest request,
                                                             String lang) {
        if (request == null || request.getOrganizationId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "organizationId est obligatoire");
        }
        projectApiService.assertOrganizationInCallerScope(
                request.getOrganizationId(), caller.accessibleInstitutionIds());

        InstitutionDTO institution = institutionService.findById(request.getOrganizationId());
        if (institution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organisation introuvable");
        }

        UserInfo userInfo = new UserInfo(institution, caller.person(), lang);
        if (!profilePermissionService.hasOrganizationPermission(userInfo, PermissionConstants.ORGANIZATION_MANAGE_PLACES)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Création de lieu non autorisée");
        }

        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name est obligatoire");
        }
        if (request.getTypeConceptId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "typeConceptId est obligatoire");
        }

        Concept typeConcept = conceptService.findById(request.getTypeConceptId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Type de lieu introuvable"));
        ConceptDTO category = conceptMapper.convert(typeConcept);

        SpatialUnitDTO toSave = new SpatialUnitDTO();
        toSave.setName(name);
        toSave.setCategory(category);
        toSave.setPlaceNumber(request.getPlaceNumber());
        toSave.setGeom(request.getGeom());
        if (request.getAddress() != null) {
            toSave.setAddress(request.getAddress());
        }

        try {
            SpatialUnitDTO saved = spatialUnitService.save(userInfo, toSave);
            return new PlaceCreatedResponse.PlaceCreatedItem(
                    saved.getId(), saved.getName(), saved.getCode(), saved.getPlaceNumber());
        } catch (SpatialUnitAlreadyExistsException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
    }

    @Transactional
    public PlaceCreatedResponse.PlaceCreatedItem updatePlace(ProjectApiCaller caller,
                                                             long placeId,
                                                             PlacePatchRequest patch,
                                                             String lang) {
        if (patch == null) {
            patch = new PlacePatchRequest();
        }
        SpatialUnitDTO dto = requireAccessiblePlace(caller, placeId);
        requirePlaceWritePermission(caller, dto, lang, "Modification de lieu non autorisée");

        InstitutionDTO institution = dto.getCreatedByInstitution();
        UserInfo userInfo = new UserInfo(institution, caller.person(), lang);

        ConceptDTO category = null;
        if (patch.getTypeConceptId() != null) {
            Concept typeConcept = conceptService.findById(patch.getTypeConceptId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Type de lieu introuvable"));
            category = conceptMapper.convert(typeConcept);
        }

        if (patch.getName() != null && patch.getName().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name ne peut pas être vide");
        }

        try {
            SpatialUnitDTO saved = (patch.isPlaceNumberPresent() || patch.isGeomPresent())
                    ? spatialUnitService.updatePlace(userInfo, placeId, patch.getName(), category, patch.getAddress(),
                            patch.getPlaceNumber(), patch.isPlaceNumberPresent(),
                            patch.getGeom(), patch.isGeomPresent())
                    : spatialUnitService.updatePlace(userInfo, placeId, patch.getName(), category, patch.getAddress());
            return new PlaceCreatedResponse.PlaceCreatedItem(
                    saved.getId(), saved.getName(), saved.getCode(), saved.getPlaceNumber());
        } catch (SpatialUnitAlreadyExistsException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
    }

    @Transactional
    public void deletePlace(ProjectApiCaller caller, long placeId, String lang) {
        SpatialUnitDTO dto = requireAccessiblePlace(caller, placeId);
        requirePlaceWritePermission(caller, dto, lang, "Suppression de lieu non autorisée");
        try {
            spatialUnitService.deleteIfUnused(placeId);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        } catch (SpatialUnitNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lieu introuvable");
        }
    }

    private SpatialUnitDTO requireAccessiblePlace(ProjectApiCaller caller, long placeId) {
        SpatialUnitDTO dto;
        try {
            dto = spatialUnitService.findById(placeId);
        } catch (SpatialUnitNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lieu introuvable");
        }
        InstitutionDTO institution = dto.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lieu sans organisation de rattachement");
        }
        projectApiService.assertOrganizationInCallerScope(institution.getId(), caller.accessibleInstitutionIds());
        return dto;
    }

    private void requirePlaceWritePermission(ProjectApiCaller caller,
                                             SpatialUnitDTO dto,
                                             String lang,
                                             String forbiddenMessage) {
        InstitutionDTO institution = dto.getCreatedByInstitution();
        UserInfo userInfo = new UserInfo(institution, caller.person(), lang);
        if (!profilePermissionService.hasOrganizationPermission(userInfo, PermissionConstants.ORGANIZATION_MANAGE_PLACES)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbiddenMessage);
        }
    }

    /** Previous/next place in the same organization ({@code GET /api/v1/places/{id}/siblings}). */
    @Transactional(readOnly = true)
    public SiblingsResource findSiblings(ProjectApiCaller caller, long placeId) {
        SpatialUnitDTO dto = requireAccessiblePlace(caller, placeId);
        return entitySiblingsService.findSiblings(EntitySiblingsService.Kind.PLACE,
                dto.getCreatedByInstitution().getId(), dto.getId());
    }
}
