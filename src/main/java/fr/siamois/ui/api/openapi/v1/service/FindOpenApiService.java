package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.services.form.EffectiveFormResolver;
import fr.siamois.ui.api.openapi.v1.resource.sibling.SiblingsResource;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.entity.*;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.api.openapi.v1.OpenApiExecutionContext;
import fr.siamois.ui.api.openapi.v1.OpenApiParamIds;
import fr.siamois.ui.api.openapi.v1.mapper.FindOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.request.find.FindCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.find.FindPatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Création et mise à jour OpenAPI des mobiliers :
 * création via {@link Specimen#NEW_UNIT_FORM}, édition via {@link Specimen#DETAILS_FORM} (comme le web).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FindOpenApiService {

    private final SpecimenService specimenService;
    private final RecordingUnitService recordingUnitService;
    private final ConceptRepository conceptRepository;
    private final ConceptMapper conceptMapper;
    private final ConversionService conversionService;
    private final ProfilePermissionService profilePermissionService;
    private final FindOpenApiMapper findOpenApiMapper;
    private final ResourceBookmarkService resourceBookmarkService;
    private final EntitySiblingsService entitySiblingsService;
    private final ValidationOpenApiService validationOpenApiService;
    private final FieldAnswerPatchService fieldAnswerPatchService;
    private final EffectiveFormResolver effectiveFormResolver;

    @Transactional
    public FindResource createFind(FindCreateRequest request,
                                   PersonDTO personDto,
                                   Set<Long> accessibleInstitutionIds,
                                   String lang) {
        String recordingUnitKey = OpenApiParamIds.requireNonBlank(
                request.getRecordingUnitId(), "recordingUnitId");
        long typeConceptId = OpenApiParamIds.parseRequiredConceptId(
                request.getTypeId(), "specimenTypeConceptId");

        RecordingUnitDTO ru = recordingUnitService.findAccessibleRecordingUnitByKey(
                recordingUnitKey, accessibleInstitutionIds, null);
        InstitutionDTO institution = ru.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UE sans organisation");
        }
        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        if (!profilePermissionService.hasRecordingUnitWritePermission(userInfo, ru)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Création de mobilier non autorisée sur cette UE");
        }

        Concept typeConcept = conceptRepository.findById(typeConceptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Type de mobilier introuvable"));
        ConceptDTO typeDto = conceptMapper.convert(typeConcept);

        SpecimenDTO shell = new SpecimenDTO();
        shell.setRecordingUnit(new RecordingUnitSummaryDTO(ru));
        shell.setCreatedByInstitution(institution);
        shell.setType(typeDto);
        shell.setCreatedBy(personDto);
        shell.setAuthors(new ArrayList<>(List.of(personDto)));
        shell.setCollectors(new ArrayList<>(List.of(personDto)));
        shell.setCollectionDate(OffsetDateTime.now(ZoneOffset.UTC));
        shell.setValidated(ValidationStatus.INCOMPLETE);

        Map<String, Object> fieldAnswers = request.getFieldAnswers() != null ? request.getFieldAnswers() : Map.of();
        SpecimenDTO created = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
            FormUiDto systemForm = Specimen.NEW_UNIT_FORM;
            FormUiDto formUiDto = conversionService.convert(systemForm, FormUiDto.class);
            fieldAnswerPatchService.applyLenient(shell, formUiDto, fieldAnswers,
                    ru.getActionUnit() != null ? ru.getActionUnit().getId() : null);
            return specimenService.save(shell);
        });
        return withPermissionsAndUri(findOpenApiMapper.toResource(created), userInfo, created);
    }

    @Transactional
    public FindResource patchFind(long specimenId,
                                  FindPatchRequest request,
                                  PersonDTO personDto,
                                  Set<Long> accessibleInstitutionIds,
                                  String lang) {
        SpecimenDTO dto = specimenService.findAccessibleById(specimenId, accessibleInstitutionIds)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mobilier introuvable ou hors périmètre"));

        InstitutionDTO institution = dto.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mobilier sans organisation");
        }
        RecordingUnitSummaryDTO ruSum = dto.getRecordingUnit();
        if (ruSum == null || ruSum.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mobilier sans unité d'enregistrement");
        }
        RecordingUnitDTO ru = recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(
                ruSum.getId(), accessibleInstitutionIds);

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        boolean canEdit = profilePermissionService.hasRecordingUnitWritePermission(userInfo, ru);
        boolean canValidate = profilePermissionService.hasValidatePermission(userInfo,
                dto.getActionUnit() != null ? dto.getActionUnit().getId() : null);
        Map<String, Object> answers = request.getFieldAnswers() != null ? request.getFieldAnswers() : Map.of();
        boolean statusChange = ValidationOpenApiService.changes(dto.getValidated(), request.getValidated());
        // A validator may change the status alone without the edit right; anything else needs it.
        if ((!answers.isEmpty() || !statusChange) && !canEdit) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification non autorisée");
        }
        validationOpenApiService.requireAllowed(dto.getValidated(), request.getValidated(), canEdit, canValidate);

        if (answers.isEmpty() && !statusChange) {
            return findOpenApiMapper.toResource(dto);
        }

        if (!answers.isEmpty()) {
            Long projectId = ru.getActionUnit() != null ? ru.getActionUnit().getId() : null;
            OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
                // The same effective form the find-types catalog lays out: system fields minus the
                // inactive ones, plus the category's additional fields.
                FormUiDto formUiDto = effectiveFormResolver.resolveEffectiveForm(Specimen.DETAILS_FORM, projectId,
                        ConfigurableTable.MOBILIER, dto.getCategory() != null ? dto.getCategory().getId() : null);
                Map<CustomField, CustomFieldAnswerViewModel> additionalAnswers =
                        fieldAnswerPatchService.apply(dto, formUiDto, answers, projectId);
                return specimenService.save(dto, additionalAnswers);
            });
        }
        // After the save: save() writes the DTO's (old) status back onto the entity.
        validationOpenApiService.apply(Specimen.class, specimenId, request.getValidated(), personDto);
        SpecimenDTO fresh = specimenService.findAccessibleById(specimenId, accessibleInstitutionIds).orElse(dto);
        return withPermissionsAndUri(findOpenApiMapper.toResource(fresh), userInfo, fresh);
    }

    /**
     * Same source of truth as {@code SpecimenPanel.canUserEditUnit} (JSF) — not the RU write
     * check {@link #patchFind}/{@link #createFind} use to gate the mutation itself, which mirrors
     * the parent recording unit's own permission (a mobilier's own write check on the specimen
     * class is a stricter/different rule reserved for read-side {@code _permissions}).
     */
    private FindResource withPermissionsAndUri(FindResource resource, UserInfo userInfo, SpecimenDTO dto) {
        boolean canEdit = profilePermissionService.hasSpecimenWritePermission(userInfo, dto);
        boolean canValidate = profilePermissionService.hasValidatePermission(userInfo,
                dto.getActionUnit() != null ? dto.getActionUnit().getId() : null);
        resource.setPermissions(fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions.of(canEdit)
                .withValidate(canValidate));
        if (dto.getId() != null) {
            resource.setResourceUri("/specimen/" + dto.getId());
        }
        resourceBookmarkService.markBookmarked(userInfo, resource);
        return resource;
    }

    @Transactional
    public void deleteFind(long specimenId,
                           PersonDTO personDto,
                           Set<Long> accessibleInstitutionIds,
                           String lang) {
        SpecimenDTO dto = specimenService.findAccessibleById(specimenId, accessibleInstitutionIds)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mobilier introuvable ou hors périmètre"));

        InstitutionDTO institution = dto.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mobilier sans organisation");
        }
        RecordingUnitSummaryDTO ruSum = dto.getRecordingUnit();
        if (ruSum == null || ruSum.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mobilier sans unité d'enregistrement");
        }
        RecordingUnitDTO ru = recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(
                ruSum.getId(), accessibleInstitutionIds);

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        if (!profilePermissionService.hasRecordingUnitWritePermission(userInfo, ru)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Suppression non autorisée");
        }

        try {
            specimenService.deleteSpecimenById(specimenId);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    /** Previous/next mobilier in the same project ({@code GET /api/v1/finds/{id}/siblings}). */
    @Transactional(readOnly = true)
    public SiblingsResource findSiblings(long specimenId, Set<Long> accessibleInstitutionIds) {
        SpecimenDTO dto = specimenService.findAccessibleById(specimenId, accessibleInstitutionIds)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mobilier introuvable ou hors périmètre"));
        Long projectId = dto.getActionUnit() != null ? dto.getActionUnit().getId() : null;
        return entitySiblingsService.findSiblings(EntitySiblingsService.Kind.FIND, projectId, dto.getId());
    }
}
