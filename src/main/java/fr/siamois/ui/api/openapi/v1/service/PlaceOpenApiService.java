package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.spatialunit.SpatialUnitAlreadyExistsException;
import fr.siamois.domain.models.exceptions.spatialunit.SpatialUnitNotFoundException;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.authorization.PermissionService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.mapper.PlaceOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.request.place.PlaceCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.place.PlacePatchRequest;
import fr.siamois.ui.api.openapi.v1.response.spatialunit.PlaceListResponse;
import fr.siamois.ui.api.openapi.v1.response.place.PlaceCreatedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PlaceOpenApiService {

    private final ProjectApiService projectApiService;
    private final InstitutionService institutionService;
    private final SpatialUnitService spatialUnitService;
    private final ConceptService conceptService;
    private final ConceptMapper conceptMapper;
    private final PermissionService permissionService;
    private final PlaceOpenApiMapper placeOpenApiMapper;

    @Transactional(readOnly = true)
    public PlaceListResponse listByOrganization(ProjectApiCaller caller,
                                              long organizationId,
                                              int offset,
                                              int limit,
                                              String sortParam) {
        projectApiService.assertOrganizationInCallerScope(organizationId, caller.accessibleInstitutionIds());

        InstitutionDTO institution = institutionService.findById(organizationId);
        if (institution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organisation introuvable");
        }

        Sort sort = ProjectApiService.parsePlaceSort(sortParam);
        Page<SpatialUnitDTO> page = spatialUnitService.findByInstitutionId(organizationId, limit, offset, sort);

        var resources = page.getContent().stream()
                .map(placeOpenApiMapper::toResource)
                .toList();

        ListMeta meta = new ListMeta(page.getTotalElements(), limit, (long) offset);
        return new PlaceListResponse(resources, meta);
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
        if (!permissionService.isInstitutionManager(userInfo) && !permissionService.isActionManager(userInfo)) {
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
        if (request.getAddress() != null) {
            toSave.setAddress(request.getAddress());
        }

        try {
            SpatialUnitDTO saved = spatialUnitService.save(userInfo, toSave);
            return new PlaceCreatedResponse.PlaceCreatedItem(saved.getId(), saved.getName(), saved.getCode());
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
            SpatialUnitDTO saved = spatialUnitService.updatePlace(
                    userInfo, placeId, patch.getName(), category, patch.getAddress());
            return new PlaceCreatedResponse.PlaceCreatedItem(saved.getId(), saved.getName(), saved.getCode());
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
        if (!permissionService.isInstitutionManager(userInfo) && !permissionService.isActionManager(userInfo)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbiddenMessage);
        }
    }
}
