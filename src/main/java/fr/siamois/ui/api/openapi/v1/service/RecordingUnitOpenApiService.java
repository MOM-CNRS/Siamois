package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.models.form.customfieldanswer.*;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customformresponse.CustomFormResponse;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.attributeconverter.CustomFormLayoutConverter;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.api.openapi.v1.OpenApiExecutionContext;
import fr.siamois.ui.api.openapi.v1.OpenApiParamIds;
import fr.siamois.ui.api.openapi.v1.exception.SyncRevisionConflictException;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitPatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.response.find.FindMobilierFormData;
import fr.siamois.ui.api.openapi.v1.response.project.ProjectFormData;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.*;
import fr.siamois.ui.api.openapi.v1.response.sync.SyncConflictData;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.form.fieldsource.FieldSource;
import fr.siamois.ui.form.fieldsource.PanelFieldSource;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.*;
import jakarta.persistence.DiscriminatorValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Enrichissement OpenAPI pour le détail UE et le formulaire de création : formulaire effectif, champs et vocabulaires.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingUnitOpenApiService {

    private final RecordingUnitService recordingUnitService;
    private final FormService formService;
    private final FieldConfigurationService fieldConfigurationService;
    private final RecordingUnitResponseMapper recordingUnitResponseMapper;
    private final ConversionService conversionService;
    private final CustomFormLayoutConverter customFormLayoutConverter;
    private final ConceptMapper conceptMapper;
    private final InstitutionService institutionService;
    private final ConceptRepository conceptRepository;
    private final SpecimenService specimenService;
    private final LangService langService;
    private final ActionUnitService actionUnitService;
    private final ProfilePermissionService profilePermissionService;
    private final PersonService personService;
    private final SpatialUnitService spatialUnitService;
    private final PersonMapper personMapper;

    @Transactional(readOnly = true)
    public RecordingUnitMobileDetailData buildMobileDetail(String recordingUnitKey, PersonDTO personDto, Set<Long> accessibleInstitutionIds,
                                                           List<String> counts, String lang) {
        return resolveMobileDetail(recordingUnitKey, personDto, accessibleInstitutionIds, counts, lang);
    }

    private RecordingUnitMobileDetailData resolveMobileDetail(String recordingUnitKey, PersonDTO personDto,
                                                               Set<Long> accessibleInstitutionIds,
                                                               List<String> counts, String lang) {
        RecordingUnitService.AccessibleRecordingUnit bundle =
                recordingUnitService.findAccessibleRecordingUnitWithEntity(recordingUnitKey, accessibleInstitutionIds, counts);
        RecordingUnitDTO dto = bundle.dto();
        RecordingUnit entity = bundle.entity();

        if (!profilePermissionService.canViewRecordingUnit(personDto, dto)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unité introuvable ou non accessible");
        }

        RecordingUnitResource recordingUnit = recordingUnitResponseMapper.convert(dto);

        InstitutionDTO institution = dto.getCreatedByInstitution();
        if (institution == null) {
            return new RecordingUnitMobileDetailData(recordingUnit, null, Map.of(), Map.of());
        }

        CustomForm customForm = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(dto.getType(), institution);
        if (customForm == null) {
            return new RecordingUnitMobileDetailData(recordingUnit, null, Map.of(), Map.of());
        }

        FormUiDto formUiDto = conversionService.convert(customForm, FormUiDto.class);
        FieldSource fieldSource = new PanelFieldSource(formUiDto);

        String layoutJson = customFormLayoutConverter.convertToDatabaseColumn(customForm.getLayout());

        RecordingUnitFormBundle formBundle = new RecordingUnitFormBundle(customForm.getId(), customForm.getName(), customForm.getDescription(), layoutJson);

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Locale locale = langService.localeForApiLang(lang);
        Map<String, RecordingUnitFormFieldApi> fields = OpenApiExecutionContext.callWithUserInfo(
                userInfo, () -> buildFieldsWithFallback(entity, dto, customForm, fieldSource, locale));
        Map<String, List<ConceptAutocompleteDTO>> vocabs = loadVocabularies(fieldSource, userInfo);

        return new RecordingUnitMobileDetailData(recordingUnit, formBundle, fields, vocabs);
    }

    /**
     * Gabarit UI du formulaire système projet ({@link ActionUnit#DETAILS_FORM}) : layout et métadonnées des champs.
     * Vocabulaires : {@code GET /api/v1/vocabularies}.
     */
    @Transactional(readOnly = true)
    public ProjectFormData buildProjectUiForm(long organizationId, PersonDTO personDto, String lang) {
        InstitutionDTO institution = institutionService.findById(organizationId);
        if (institution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found");
        }
        return buildProjectFormBundle(institution, personDto, lang);
    }

    /**
     * Applique les réponses du formulaire système projet ({@link ActionUnit#DETAILS_FORM}) sur un shell avant save.
     */
    public void applySystemProjectFormFieldAnswers(ActionUnitDTO shell,
                                                   Map<String, Object> fieldAnswers,
                                                   PersonDTO personDto,
                                                   String lang) {
        if (fieldAnswers == null || fieldAnswers.isEmpty()) {
            return;
        }
        InstitutionDTO institution = shell.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans organisation");
        }
        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        OpenApiExecutionContext.runWithUserInfo(userInfo, () -> {
            CustomForm systemForm = ActionUnit.DETAILS_FORM;
            FormUiDto formUiDto = conversionService.convert(systemForm, FormUiDto.class);
            FieldSource fieldSource = new PanelFieldSource(formUiDto);
            CustomFormResponseViewModel response = formService.initOrReuseResponse(null, shell, fieldSource, true);
            mergeFieldAnswers(shell, response, fieldSource, fieldAnswers, lang);
            formService.updateJpaEntityFromResponse(response, shell);
        });
    }

    private ProjectFormData buildProjectFormBundle(InstitutionDTO institution,
                                                   PersonDTO personDto,
                                                   String lang) {
        CustomForm systemForm = ActionUnit.DETAILS_FORM;
        FormUiDto formUiDto = conversionService.convert(systemForm, FormUiDto.class);
        FieldSource fieldSource = new PanelFieldSource(formUiDto);
        String layoutJson = customFormLayoutConverter.convertToDatabaseColumn(systemForm.getLayout());
        RecordingUnitFormBundle formBundle = new RecordingUnitFormBundle(
                systemForm.getId(),
                systemForm.getName(),
                systemForm.getDescription() != null ? systemForm.getDescription() : "",
                layoutJson);

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Locale locale = langService.localeForApiLang(lang);
        Map<String, RecordingUnitFormFieldApi> fields = OpenApiExecutionContext.callWithUserInfo(
                userInfo, () -> buildFieldsMetadataOnly(fieldSource, locale));
        return new ProjectFormData(formBundle, fields);
    }

    /**
     * Formulaire de création d'une UE : même résolution que le détail (type + institution), sans entité persistée.
     */
    @Transactional(readOnly = true)
    public RecordingUnitCreateFormData buildRecordingUnitCreateForm(long organizationId,
                                                                  long recordingUnitTypeConceptId,
                                                                  PersonDTO personDto,
                                                                  String lang) {
        InstitutionDTO institution = institutionService.findById(organizationId);
        if (institution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found");
        }
        Concept typeConcept = conceptRepository.findById(recordingUnitTypeConceptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recording unit type not found"));
        ConceptDTO typeDto = conceptMapper.convert(typeConcept);

        CustomForm customForm = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, institution);
        if (customForm == null) {
            return new RecordingUnitCreateFormData(typeDto, null, Map.of(), Map.of());
        }

        FormUiDto formUiDto = conversionService.convert(customForm, FormUiDto.class);
        FieldSource fieldSource = new PanelFieldSource(formUiDto);
        String layoutJson = customFormLayoutConverter.convertToDatabaseColumn(customForm.getLayout());
        RecordingUnitFormBundle formBundle = new RecordingUnitFormBundle(
                customForm.getId(), customForm.getName(), customForm.getDescription(), layoutJson);

        RecordingUnitDTO shell = new RecordingUnitDTO();
        shell.setType(typeDto);
        shell.setCreatedByInstitution(institution);

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Locale locale = langService.localeForApiLang(lang);
        Map<String, RecordingUnitFormFieldApi> fields = OpenApiExecutionContext.callWithUserInfo(
                userInfo, () -> buildCustomFormFieldsForBindTarget(
                        shell, fieldSource, "création UE", typeDto.getId(), locale));
        Map<String, List<ConceptAutocompleteDTO>> vocabs = loadVocabularies(fieldSource, userInfo);

        return new RecordingUnitCreateFormData(typeDto, formBundle, fields, vocabs);
    }

    /**
     * Gabarit UI mobilier pour une organisation : layout et métadonnées des champs (sans valeurs saisies).
     * Utilise le formulaire personnalisé « générique » de l'institution (sans type de spécimen).
     * Vocabulaires : {@code GET /api/v1/vocabularies}. Valeurs d'un mobilier existant : {@link #buildFindMobilierForm}.
     */
    @Transactional(readOnly = true)
    public FindMobilierFormData buildFindUiForm(long organizationId, PersonDTO personDto, String lang) {
        InstitutionDTO institution = institutionService.findById(organizationId);
        if (institution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found");
        }

        CustomForm customForm = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(null, institution);
        if (customForm == null) {
            return new FindMobilierFormData(null, Map.of());
        }

        FormUiDto formUiDto = conversionService.convert(customForm, FormUiDto.class);
        FieldSource fieldSource = new PanelFieldSource(formUiDto);
        String layoutJson = customFormLayoutConverter.convertToDatabaseColumn(customForm.getLayout());
        RecordingUnitFormBundle formBundle = new RecordingUnitFormBundle(
                customForm.getId(), customForm.getName(), customForm.getDescription(), layoutJson);

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Locale locale = langService.localeForApiLang(lang);
        Map<String, RecordingUnitFormFieldApi> fields = OpenApiExecutionContext.callWithUserInfo(
                userInfo, () -> buildFieldsMetadataOnly(fieldSource, locale));

        return new FindMobilierFormData(formBundle, fields);
    }

    /**
     * Mobilier existant : layout, champs et valeurs persistées (sans vocabulaires).
     */
    @Transactional(readOnly = true)
    public FindMobilierFormData buildFindMobilierForm(String idOrKey,
                                                      PersonDTO personDto,
                                                      Set<Long> accessibleInstitutionIds,
                                                      String lang) {
        SpecimenDTO specimen = specimenService.findAccessibleByKey(idOrKey, accessibleInstitutionIds)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mobilier introuvable ou hors périmètre"));
        InstitutionDTO institution = specimen.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mobilier sans organisation");
        }
        ConceptDTO specimenType = specimen.getType();
        if (specimenType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mobilier sans type");
        }

        CustomForm customForm = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(specimenType, institution);
        if (customForm == null) {
            return new FindMobilierFormData(null, Map.of());
        }

        FormUiDto formUiDto = conversionService.convert(customForm, FormUiDto.class);
        FieldSource fieldSource = new PanelFieldSource(formUiDto);
        String layoutJson = customFormLayoutConverter.convertToDatabaseColumn(customForm.getLayout());
        RecordingUnitFormBundle formBundle = new RecordingUnitFormBundle(
                customForm.getId(), customForm.getName(), customForm.getDescription(), layoutJson);

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Locale locale = langService.localeForApiLang(lang);
        Map<String, RecordingUnitFormFieldApi> fields = OpenApiExecutionContext.callWithUserInfo(
                userInfo, () -> buildCustomFormFieldsForBindTarget(
                        specimen, fieldSource, "mobilier key=" + idOrKey, specimenType.getId(), locale));

        return new FindMobilierFormData(formBundle, fields);
    }

    private Map<String, RecordingUnitFormFieldApi> buildCustomFormFieldsForBindTarget(
            RecordingUnitDTO shell,
            FieldSource fieldSource,
            String logContext,
            Long typeConceptIdForLog,
            Locale locale) {
        return buildCustomFormFieldsForEntity(shell, fieldSource, logContext, typeConceptIdForLog, locale);
    }

    private Map<String, RecordingUnitFormFieldApi> buildCustomFormFieldsForBindTarget(
            SpecimenDTO shell,
            FieldSource fieldSource,
            String logContext,
            Long typeConceptIdForLog,
            Locale locale) {
        return buildCustomFormFieldsForEntity(shell, fieldSource, logContext, typeConceptIdForLog, locale);
    }

    private Map<String, RecordingUnitFormFieldApi> buildCustomFormFieldsForEntity(
            Object entity,
            FieldSource fieldSource,
            String logContext,
            Long typeConceptIdForLog,
            Locale locale) {
        try {
            CustomFormResponseViewModel response = formService.initOrReuseResponse(null, entity, fieldSource, true);
            return toFieldsMap(response, fieldSource, locale);
        } catch (RuntimeException ex) {
            log.warn("Impossible d'initialiser les réponses formulaire pour {} (type concept id={}): {}",
                    logContext, typeConceptIdForLog, ex.toString(), ex);
            return buildFieldsMetadataOnly(fieldSource, locale);
        }
    }

    /**
     * Construit les champs avec valeurs ; si le moteur de réponses échoue (type de champ non géré, données incohérentes),
     * retourne au moins les métadonnées des champs du layout pour le mobile (sans currentValue).
     */
    private Map<String, RecordingUnitFormFieldApi> buildFieldsWithFallback(RecordingUnit entity,
                                                                           RecordingUnitDTO dto,
                                                                           CustomForm customForm,
                                                                           FieldSource fieldSource,
                                                                           Locale locale) {
        try {
            CustomFormResponseViewModel response = formService.initOrReuseResponse(null, dto, fieldSource, true);
            applyPersistedCustomAnswers(entity, customForm, response, locale.getLanguage());
            return toFieldsMap(response, fieldSource, locale);
        } catch (RuntimeException ex) {
            log.warn("Impossible de construire les réponses formulaire pour l'UE id={} (fallback métadonnées seules): {}",
                    dto.getId(), ex.toString(), ex);
            return buildFieldsMetadataOnly(fieldSource, locale);
        }
    }

    private Map<String, RecordingUnitFormFieldApi> toFieldsMap(CustomFormResponseViewModel response, FieldSource fallback, Locale locale) {
        if (response.getAnswers() == null) return buildFieldsMetadataOnly(fallback, locale);
        Map<String, RecordingUnitFormFieldApi> fields = new LinkedHashMap<>();
        for (Map.Entry<CustomField, CustomFieldAnswerViewModel> e : response.getAnswers().entrySet()) {
            CustomField field = e.getKey();
            fields.put(String.valueOf(field.getId()), toFieldApi(field, e.getValue(), locale));
        }
        return fields;
    }

    private Map<String, RecordingUnitFormFieldApi> buildFieldsMetadataOnly(FieldSource fieldSource, Locale locale) {
        Map<String, RecordingUnitFormFieldApi> fields = new LinkedHashMap<>();
        for (CustomField field : fieldSource.getAllFields()) {
            if (field == null || field.getId() == null) continue;
            fields.put(String.valueOf(field.getId()), toFieldApi(field, null, locale));
        }
        return fields;
    }

    private RecordingUnitFormFieldApi toFieldApi(CustomField field, CustomFieldAnswerViewModel answer, Locale locale) {
        String fieldCode = field instanceof CustomFieldSelectOneFromFieldCode f ? f.getFieldCode() : null;
        String label = langService.resolveMessage(field.getLabel(), locale);
        String hint = langService.resolveMessage(field.getHint(), locale);
        return new RecordingUnitFormFieldApi(field.getId(), answerTypeDiscriminator(field), label, hint, field.getValueBinding(),
                field.getIsSystemField(), fieldCode, formService.readAnswerValueForApi(answer));
    }

    private static String answerTypeDiscriminator(CustomField field) {
        DiscriminatorValue dv = field.getClass().getAnnotation(DiscriminatorValue.class);
        return dv != null ? dv.value() : field.getClass().getSimpleName();
    }

    private Map<String, List<ConceptAutocompleteDTO>> loadVocabularies(FieldSource fieldSource, UserInfo userInfo) {
        Set<String> codes = new LinkedHashSet<>();
        for (CustomField f : fieldSource.getAllFields()) {
            if (f instanceof CustomFieldSelectOneFromFieldCode ff
                    && ff.getFieldCode() != null
                    && !ff.getFieldCode().isBlank()) {
                codes.add(ff.getFieldCode());
            }
        }
        Map<String, List<ConceptAutocompleteDTO>> out = new LinkedHashMap<>();
        for (String code : codes) {
            try {
                out.put(code, fieldConfigurationService.fetchAutocomplete(userInfo, code, null));
            } catch (NoConfigForFieldException e) {
                out.put(code, List.of());
            }
        }
        return out;
    }

    private void applyPersistedCustomAnswers(RecordingUnit entity,
                                             CustomForm effectiveForm,
                                             CustomFormResponseViewModel response,
                                             String lang) {
        CustomFormResponse persisted = entity.getFormResponse();
        if (persisted == null || persisted.getForm() == null || response.getAnswers() == null) {
            return;
        }
        if (!Objects.equals(persisted.getForm().getId(), effectiveForm.getId())) {
            return;
        }
        for (Map.Entry<CustomField, CustomFieldAnswer> e : persisted.getAnswers().entrySet()) {
            CustomField field = e.getKey();
            CustomFieldAnswerViewModel vm = findViewModelForField(response.getAnswers(), field);
            if (vm != null) {
                applyOnePersistedAnswer(e.getValue(), vm, lang);
            }
        }
    }

    private void applyOnePersistedAnswer(CustomFieldAnswer jpa, CustomFieldAnswerViewModel vm, String lang) {
        if (jpa instanceof CustomFieldAnswerInteger jpaInt && vm instanceof CustomFieldAnswerIntegerViewModel vmInt) {
            vmInt.setValue(jpaInt.getValue());
        } else if (jpa instanceof CustomFieldAnswerText jpaText && vm instanceof CustomFieldAnswerTextViewModel vmText) {
            vmText.setValue(jpaText.getValue());
        } else if (jpa instanceof CustomFieldAnswerDateTime jpaDt && vm instanceof CustomFieldAnswerDateTimeViewModel vmDt) {
            vmDt.setValue(jpaDt.getValue());
        } else if (jpa instanceof CustomFieldAnswerSelectOne jpaSel
                && vm instanceof CustomFieldAnswerSelectOneFromFieldCodeViewModel vmFc
                && jpaSel.getValue() != null) {
            ConceptDTO conceptDto = conceptMapper.convert(jpaSel.getValue());
            String label = conceptDto.getExternalId() != null ? conceptDto.getExternalId() : "";
            vmFc.setValue(new ConceptAutocompleteDTO(conceptDto, label, lang));
        }
    }

    /**
     * Les clés {@link CustomField} du layout et celles de la réponse persistée peuvent être des instances différentes ;
     * on aligne sur {@link CustomField#getId()}.
     */
    private static CustomFieldAnswerViewModel findViewModelForField(Map<CustomField, CustomFieldAnswerViewModel> answers,
                                                                    CustomField field) {
        CustomFieldAnswerViewModel direct = answers.get(field);
        if (direct != null) {
            return direct;
        }
        if (field == null || field.getId() == null) {
            return null;
        }
        for (Map.Entry<CustomField, CustomFieldAnswerViewModel> en : answers.entrySet()) {
            CustomField k = en.getKey();
            if (k != null && field.getId().equals(k.getId())) {
                return en.getValue();
            }
        }
        return null;
    }

    @Transactional(readOnly = true)
    public RecordingUnitRelationsData buildRecordingUnitRelations(String recordingUnitKey,
                                                                  Set<Long> accessibleInstitutionIds) {
        return resolveRecordingUnitRelations(recordingUnitKey, accessibleInstitutionIds);
    }

    private RecordingUnitRelationsData resolveRecordingUnitRelations(String recordingUnitKey,
                                                                      Set<Long> accessibleInstitutionIds) {
        RecordingUnitService.RecordingUnitRelationsBundle bundle =
                recordingUnitService.findRelationsForAccessibleRecordingUnit(recordingUnitKey, accessibleInstitutionIds);
        return new RecordingUnitRelationsData(
                bundle.stratigraphicRelationships(),
                bundle.parents(),
                bundle.children());
    }

    @Transactional(readOnly = true)
    public RecordingUnitChildrenData buildRecordingUnitChildren(String recordingUnitKey,
                                                                Set<Long> accessibleInstitutionIds) {
        List<RecordingUnitSummaryDTO> children =
                recordingUnitService.findChildrenForAccessibleRecordingUnit(recordingUnitKey, accessibleInstitutionIds);
        return new RecordingUnitChildrenData(children);
    }

    @Transactional
    public RecordingUnitRelationsData addExistingChild(String recordingUnitKey,
                                                       long relatedRecordingUnitId,
                                                       PersonDTO personDto,
                                                       Set<Long> accessibleInstitutionIds) {
        RecordingUnitService.AccessibleRecordingUnit parent =
                recordingUnitService.findAccessibleRecordingUnitWithEntity(
                        recordingUnitKey, accessibleInstitutionIds, null);
        assertWritePermission(parent.dto(), personDto);
        recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(
                relatedRecordingUnitId, accessibleInstitutionIds);
        mutateHierarchy(() -> recordingUnitService.addHierarchyChild(
                parent.entity().getId(), relatedRecordingUnitId));
        return resolveRecordingUnitRelations(recordingUnitKey, accessibleInstitutionIds);
    }

    @Transactional
    public RecordingUnitRelationsData addExistingParent(String recordingUnitKey,
                                                          long relatedRecordingUnitId,
                                                          PersonDTO personDto,
                                                          Set<Long> accessibleInstitutionIds) {
        RecordingUnitService.AccessibleRecordingUnit child =
                recordingUnitService.findAccessibleRecordingUnitWithEntity(
                        recordingUnitKey, accessibleInstitutionIds, null);
        assertWritePermission(child.dto(), personDto);
        recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(
                relatedRecordingUnitId, accessibleInstitutionIds);
        mutateHierarchy(() -> recordingUnitService.addHierarchyChild(
                relatedRecordingUnitId, child.entity().getId()));
        return resolveRecordingUnitRelations(recordingUnitKey, accessibleInstitutionIds);
    }

    @Transactional
    public RecordingUnitRelationsData removeExistingChild(String recordingUnitKey,
                                                            long relatedRecordingUnitId,
                                                            PersonDTO personDto,
                                                            Set<Long> accessibleInstitutionIds) {
        RecordingUnitService.AccessibleRecordingUnit parent =
                recordingUnitService.findAccessibleRecordingUnitWithEntity(
                        recordingUnitKey, accessibleInstitutionIds, null);
        assertWritePermission(parent.dto(), personDto);
        recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(
                relatedRecordingUnitId, accessibleInstitutionIds);
        mutateHierarchy(() -> recordingUnitService.removeHierarchyChild(
                parent.entity().getId(), relatedRecordingUnitId));
        return resolveRecordingUnitRelations(recordingUnitKey, accessibleInstitutionIds);
    }

    @Transactional
    public RecordingUnitRelationsData removeExistingParent(String recordingUnitKey,
                                                           long relatedRecordingUnitId,
                                                           PersonDTO personDto,
                                                           Set<Long> accessibleInstitutionIds) {
        RecordingUnitService.AccessibleRecordingUnit child =
                recordingUnitService.findAccessibleRecordingUnitWithEntity(
                        recordingUnitKey, accessibleInstitutionIds, null);
        assertWritePermission(child.dto(), personDto);
        recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(
                relatedRecordingUnitId, accessibleInstitutionIds);
        mutateHierarchy(() -> recordingUnitService.removeHierarchyChild(
                relatedRecordingUnitId, child.entity().getId()));
        return resolveRecordingUnitRelations(recordingUnitKey, accessibleInstitutionIds);
    }

    private void assertWritePermission(RecordingUnitDTO dto, PersonDTO personDto) {
        InstitutionDTO institution = dto.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unité sans organisation");
        }
        UserInfo userInfo = new UserInfo(institution, personDto, null);
        if (!profilePermissionService.hasRecordingUnitWritePermission(userInfo, dto)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification non autorisée");
        }
    }

    private void mutateHierarchy(Runnable mutation) {
        try {
            mutation.run();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
    }

    /**
     * Création d'une UE : formulaire résolu par type + institution du projet ; valeurs dans {@code fieldAnswers}
     * (clés = id de champ). Champs non reconnus ou non supportés en v1 sont ignorés (log).
     */
    @Transactional
    public RecordingUnitMobileDetailData createRecordingUnit(RecordingUnitCreateRequest request,
                                                             PersonDTO personDto,
                                                             Set<Long> accessibleInstitutionIds,
                                                             String lang) {
        String projectKey = OpenApiParamIds.requireNonBlank(request.getActionUnitId(), "actionUnitId");
        if (request.getRecordingUnitTypeConceptId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "recordingUnitTypeConceptId est obligatoire");
        }
        AccessibleProjectForApi project = actionUnitService.findAccessibleProjectByKey(
                projectKey, accessibleInstitutionIds);
        ActionUnitDTO au = project.actionUnit();
        InstitutionDTO institution = au.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans organisation");
        }
        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        if (!profilePermissionService.hasProjectPermission(userInfo, au.getId(), PermissionConstants.PROJECT_EDIT_RECORDING_UNITS)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Création d'unité non autorisée sur ce projet");
        }
        Concept typeConcept = conceptRepository.findById(request.getRecordingUnitTypeConceptId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Type d'UE introuvable"));
        ConceptDTO typeDto = conceptMapper.convert(typeConcept);

        CustomForm customForm = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(typeDto, institution);
        if (customForm == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Aucun formulaire personnalisé pour ce type d'UE et cette organisation");
        }

        RecordingUnitDTO shell = initRecordingUnitShell(typeDto, au, institution, personDto);

        RecordingUnitDTO created = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
            FormUiDto formUiDto = conversionService.convert(customForm, FormUiDto.class);
            FieldSource fieldSource = new PanelFieldSource(formUiDto);
            CustomFormResponseViewModel response = formService.initOrReuseResponse(null, shell, fieldSource, true);
            mergeFieldAnswers(shell, response, fieldSource, request.getFieldAnswers(), lang);
            formService.updateJpaEntityFromResponse(response, shell);
            return saveWithGeneratedIdentifier(shell);
        });

        String key = created.getId() != null ? String.valueOf(created.getId()) : created.getFullIdentifier();
        return resolveMobileDetail(key, personDto, accessibleInstitutionIds, null, lang);
    }

    private RecordingUnitDTO initRecordingUnitShell(ConceptDTO typeDto, ActionUnitDTO au,
                                                     InstitutionDTO institution, PersonDTO personDto) {
        RecordingUnitDTO shell = new RecordingUnitDTO();
        shell.setType(typeDto);
        shell.setCreatedByInstitution(institution);
        shell.setActionUnit(actionUnitSummaryFromFull(au));
        shell.setAuthor(personDto);
        shell.setCreatedBy(personDto);
        shell.setContributors(new ArrayList<>(List.of(personDto)));
        shell.setOpeningDate(OffsetDateTime.now(ZoneOffset.UTC));
        shell.setValidated(ValidationStatus.INCOMPLETE);
        shell.setParents(new HashSet<>());
        shell.setChildren(new HashSet<>());
        List<SpatialUnitSummaryDTO> suOptions = spatialUnitService.getSpatialUnitOptionsFor(shell);
        if (!suOptions.isEmpty()) {
            shell.setSpatialUnit(suOptions.get(0));
        }
        shell.resetFullIdentifier();
        if (shell.getFullIdentifier() == null || shell.getFullIdentifier().isBlank()) {
            String fmt = au.getRecordingUnitIdentifierFormat();
            shell.setFullIdentifier(fmt != null && !fmt.isBlank() ? fmt : "PENDING");
        }
        shell.setIdentifier("0");
        return shell;
    }

    private RecordingUnitDTO saveWithGeneratedIdentifier(RecordingUnitDTO dto) {
        RecordingUnitDTO saved;
        try {
            saved = recordingUnitService.save(dto);
        } catch (FailedRecordingUnitSaveException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }
        String generated = recordingUnitService.generateFullIdentifier(saved.getActionUnit(), saved);
        saved.setFullIdentifier(generated);
        if (recordingUnitService.fullIdentifierAlreadyExistInAction(saved)) {
            saved.setFullIdentifier(saved.getActionUnit().getRecordingUnitIdentifierFormat());
        }
        try {
            return recordingUnitService.save(saved);
        } catch (FailedRecordingUnitSaveException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }
    }

    /**
     * Mise à jour partielle des réponses formulaire (même résolution de formulaire que le détail GET).
     */
    @Transactional
    public RecordingUnitMobileDetailData patchRecordingUnit(String recordingUnitKey,
                                                            RecordingUnitPatchRequest request,
                                                            PersonDTO personDto,
                                                            Set<Long> accessibleInstitutionIds,
                                                            String lang) {
        RecordingUnitService.AccessibleRecordingUnit bundle =
                recordingUnitService.findAccessibleRecordingUnitWithEntity(recordingUnitKey, accessibleInstitutionIds, null);
        RecordingUnitDTO dto = bundle.dto();
        RecordingUnit entity = bundle.entity();

        assertSyncRevisionIfRequested(
                recordingUnitKey,
                request.getExpectedRevision(),
                entity.getSyncRevision(),
                personDto,
                accessibleInstitutionIds,
                lang);

        InstitutionDTO institution = dto.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unité sans organisation");
        }
        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        if (!profilePermissionService.hasRecordingUnitWritePermission(userInfo, dto)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification non autorisée");
        }

        Map<String, Object> answers = request.getFieldAnswers() != null ? request.getFieldAnswers() : Map.of();
        if (answers.isEmpty()) {
            return resolveMobileDetail(recordingUnitKey, personDto, accessibleInstitutionIds, null, lang);
        }

        CustomForm customForm = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(dto.getType(), institution);
        if (customForm == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Aucun formulaire personnalisé pour ce type d'UE : impossible d'appliquer fieldAnswers");
        }

        OpenApiExecutionContext.runWithUserInfo(userInfo, () -> {
            FormUiDto formUiDto = conversionService.convert(customForm, FormUiDto.class);
            FieldSource fieldSource = new PanelFieldSource(formUiDto);
            CustomFormResponseViewModel response = formService.initOrReuseResponse(null, dto, fieldSource, true);
            applyPersistedCustomAnswers(entity, customForm, response, langService.localeForApiLang(lang).getLanguage());
            mergeFieldAnswers(dto, response, fieldSource, answers, lang);
            formService.updateJpaEntityFromResponse(response, dto);

            try {
                recordingUnitService.save(dto);
            } catch (FailedRecordingUnitSaveException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
            }
        });

        return resolveMobileDetail(recordingUnitKey, personDto, accessibleInstitutionIds, null, lang);
    }

    private void assertSyncRevisionIfRequested(String recordingUnitKey,
                                               Long expectedRevision,
                                               Long currentRevision,
                                               PersonDTO personDto,
                                               Set<Long> accessibleInstitutionIds,
                                               String lang) {
        if (expectedRevision == null) {
            return;
        }
        long current = currentRevision != null ? currentRevision : 0L;
        if (expectedRevision == current) {
            return;
        }
        RecordingUnitMobileDetailData serverState = resolveMobileDetail(
                recordingUnitKey, personDto, accessibleInstitutionIds, null, lang);
        throw new SyncRevisionConflictException(new SyncConflictData(
                "recording_unit",
                recordingUnitKey,
                expectedRevision,
                current,
                serverState));
    }

    private static ActionUnitSummaryDTO actionUnitSummaryFromFull(ActionUnitDTO au) {
        ActionUnitSummaryDTO s = new ActionUnitSummaryDTO(au);
        s.setId(au.getId());
        s.setName(au.getName());
        s.setFullIdentifier(au.getFullIdentifier());
        s.setIdentifier(au.getIdentifier());
        s.setRecordingUnitIdentifierFormat(au.getRecordingUnitIdentifierFormat());
        s.setType(au.getType());
        s.setBeginDate(au.getBeginDate());
        s.setEndDate(au.getEndDate());
        s.setMinRecordingUnitCode(au.getMinRecordingUnitCode());
        s.setMaxRecordingUnitCode(au.getMaxRecordingUnitCode());
        return s;
    }

    private void mergeFieldAnswers(RecordingUnitDTO bindTarget,
                                   CustomFormResponseViewModel response,
                                   FieldSource fieldSource,
                                   Map<String, Object> fieldAnswers,
                                   String lang) {
        mergeFieldAnswersInternal(response, fieldSource, fieldAnswers, bindTarget);
    }

    private void mergeFieldAnswers(ActionUnitDTO bindTarget,
                                   CustomFormResponseViewModel response,
                                   FieldSource fieldSource,
                                   Map<String, Object> fieldAnswers,
                                   String lang) {
        mergeFieldAnswersInternal(response, fieldSource, fieldAnswers, null);
    }

    private void mergeFieldAnswersInternal(CustomFormResponseViewModel response,
                                           FieldSource fieldSource,
                                           Map<String, Object> fieldAnswers,
                                           RecordingUnitDTO recordingUnitForCoercion) {
        if (fieldAnswers == null || fieldAnswers.isEmpty() || response.getAnswers() == null) {
            return;
        }
        for (Map.Entry<String, Object> e : fieldAnswers.entrySet()) {
            mergeOneFieldAnswerInternal(response, fieldSource, e.getKey(), e.getValue(), recordingUnitForCoercion);
        }
    }

    private void mergeOneFieldAnswerInternal(CustomFormResponseViewModel response,
                                              FieldSource fieldSource,
                                              String key,
                                              Object value,
                                              RecordingUnitDTO recordingUnitForCoercion) {
        Long fieldId;
        try {
            fieldId = Long.parseLong(key);
        } catch (NumberFormatException ex) {
            log.debug("Clé de champ ignorée (non numérique): {}", key);
            return;
        }
        CustomField field = fieldSource.findFieldById(fieldId);
        if (field == null) {
            log.debug("Champ id={} absent du formulaire effectif", fieldId);
            return;
        }
        CustomFieldAnswerViewModel vm = findViewModelForField(response.getAnswers(), field);
        if (vm == null) {
            return;
        }
        Object typed;
        try {
            typed = coerceAnswerValue(field, value, recordingUnitForCoercion);
        } catch (ResponseStatusException rex) {
            throw rex;
        } catch (RuntimeException ex) {
            log.warn("Valeur ignorée pour champ id={} ({}): {}", fieldId, field.getClass().getSimpleName(), ex.toString());
            return;
        }
        if (typed == null && value != null) {
            log.debug("Valeur non convertible pour champ id={} type {}", fieldId, field.getClass().getSimpleName());
            return;
        }
        formService.applyTypedValueToAnswer(vm, typed);
    }

    private Object coerceAnswerValue(CustomField field, Object raw, RecordingUnitDTO ru) {
        if (raw == null) return null;
        if (field instanceof CustomFieldInteger) return ruCoerceInteger(raw);
        if (field instanceof CustomFieldText) return String.valueOf(raw);
        if (field instanceof CustomFieldDateTime) return ruCoerceDateTime(raw);
        if (field instanceof CustomFieldSelectOneFromFieldCode) return ruCoerceConcept(raw);
        if (field instanceof CustomFieldSelectOnePerson) return ruCoercePerson(raw);
        if (field instanceof CustomFieldSelectOneActionUnit) return ruCoerceActionUnit(raw);
        if (field instanceof CustomFieldSelectOneSpatialUnit) return ruCoerceSpatialUnit(raw);
        log.debug("Type de champ non pris en charge pour l'API v1: {}", field.getClass().getSimpleName());
        return null;
    }

    private static Object ruCoerceInteger(Object raw) {
        if (raw instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(raw));
    }

    private static Object ruCoerceDateTime(Object raw) {
        if (raw instanceof OffsetDateTime odt) return odt;
        if (raw instanceof String s) return OffsetDateTime.parse(s);
        throw new IllegalArgumentException("Format datetime attendu (chaîne ISO-8601 ou OffsetDateTime)");
    }

    private Object ruCoerceConcept(Object raw) {
        long conceptId = requireLongId(raw, "concept");
        Concept c = conceptRepository.findById(conceptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Concept introuvable: " + conceptId));
        return conceptMapper.convert(c);
    }

    private Object ruCoercePerson(Object raw) {
        long personId = requireLongId(raw, "personne");
        Person p = personService.findById(personId);
        if (p == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Personne introuvable: " + personId);
        }
        return personMapper.convert(p);
    }

    private Object ruCoerceActionUnit(Object raw) {
        long actionId = requireLongId(raw, "unité d'action");
        ActionUnitDTO au = actionUnitService.findById(actionId);
        if (au == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet introuvable: " + actionId);
        }
        return actionUnitSummaryFromFull(au);
    }

    private Object ruCoerceSpatialUnit(Object raw) {
        long suId = requireLongId(raw, "unité spatiale");
        try {
            return new SpatialUnitSummaryDTO(spatialUnitService.findById(suId));
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unité spatiale introuvable: " + suId);
        }
    }

    private static long requireLongId(Object raw, String label) {
        Long id = extractLongId(raw);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identifiant numérique attendu pour " + label);
        }
        return id;
    }

    private static Long extractLongId(Object raw) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        if (raw instanceof Map<?, ?> m) {
            Object id = m.get("id");
            if (id instanceof Number n) {
                return n.longValue();
            }
            if (id instanceof String s && !s.isBlank()) {
                return Long.parseLong(s);
            }
        }
        if (raw instanceof String s && !s.isBlank()) {
            return Long.parseLong(s);
        }
        return null;
    }
}
