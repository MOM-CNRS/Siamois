package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneActionUnit;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOnePerson;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneSpatialUnit;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.form.customfield.CustomFieldText;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerDateTime;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerInteger;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerSelectOne;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerText;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customformresponse.CustomFormResponse;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.authorization.PermissionService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.attributeconverter.CustomFormLayoutConverter;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.response.find.FindFormData;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitChildrenData;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitCreateFormData;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitPatchRequest;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitFormBundle;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitFormFieldApi;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitMobileDetailData;
import fr.siamois.ui.api.openapi.v1.response.project.ProjectFormData;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitRelationsData;
import fr.siamois.ui.form.fieldsource.FieldSource;
import fr.siamois.ui.form.fieldsource.PanelFieldSource;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerDateTimeViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerIntegerViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerSelectOneFromFieldCodeViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerTextViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import jakarta.persistence.DiscriminatorValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
    private final PermissionService permissionService;
    private final PersonService personService;
    private final SpatialUnitService spatialUnitService;
    private final PersonMapper personMapper;

    @Transactional(readOnly = true)
    public RecordingUnitMobileDetailData buildMobileDetail(String recordingUnitKey, PersonDTO personDto, Set<Long> accessibleInstitutionIds,
                                                           List<String> counts, String lang) {
        RecordingUnitService.AccessibleRecordingUnit bundle =
                recordingUnitService.findAccessibleRecordingUnitWithEntity(recordingUnitKey, accessibleInstitutionIds, counts);
        RecordingUnitDTO dto = bundle.dto();
        RecordingUnit entity = bundle.entity();

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

        Locale locale = langService.localeForApiLang(lang);
        Map<String, RecordingUnitFormFieldApi> fields = buildFieldsWithFallback(entity, dto, customForm, fieldSource, locale);

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Map<String, List<ConceptAutocompleteDTO>> vocabs = loadVocabularies(fieldSource, userInfo);

        return new RecordingUnitMobileDetailData(recordingUnit, formBundle, fields, vocabs);
    }

    /**
     * Formulaire système de fiche projet (unité d'action) : même schéma que le web ({@link ActionUnit#DETAILS_FORM}),
     * avec listes de concepts pour les champs configurés par field_code (ex. type d'opération).
     */
    @Transactional(readOnly = true)
    public ProjectFormData buildProjectDetailForm(ActionUnitDTO dto, PersonDTO personDto, String lang) {
        InstitutionDTO institution = dto.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans organisation de rattachement");
        }
        CustomForm systemForm = ActionUnit.DETAILS_FORM;
        FormUiDto formUiDto = conversionService.convert(systemForm, FormUiDto.class);
        FieldSource fieldSource = new PanelFieldSource(formUiDto);
        String layoutJson = customFormLayoutConverter.convertToDatabaseColumn(systemForm.getLayout());
        RecordingUnitFormBundle formBundle = new RecordingUnitFormBundle(
                systemForm.getId(),
                systemForm.getName(),
                systemForm.getDescription() != null ? systemForm.getDescription() : "",
                layoutJson);

        Locale locale = langService.localeForApiLang(lang);
        Map<String, RecordingUnitFormFieldApi> fields;
        try {
            CustomFormResponseViewModel response = formService.initOrReuseResponse(null, dto, fieldSource, true);
            fields = toFieldsMap(response, fieldSource, locale);
        } catch (RuntimeException ex) {
            log.warn("Impossible d'initialiser le formulaire projet id={}: {}", dto.getId(), ex.toString(), ex);
            fields = buildFieldsMetadataOnly(fieldSource, locale);
        }

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Map<String, List<ConceptAutocompleteDTO>> vocabs = loadVocabularies(fieldSource, userInfo);
        return new ProjectFormData(formBundle, fields, vocabs);
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

        Locale locale = langService.localeForApiLang(lang);
        Map<String, RecordingUnitFormFieldApi> fields = buildCustomFormFieldsForBindTarget(
                shell, fieldSource, "création UE", typeDto.getId(), locale);

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Map<String, List<ConceptAutocompleteDTO>> vocabs = loadVocabularies(fieldSource, userInfo);

        return new RecordingUnitCreateFormData(typeDto, formBundle, fields, vocabs);
    }

    /**
     * Formulaire d'édition d'un mobilier (spécimen) : résolution du formulaire custom par type de spécimen
     * et institution de rattachement (même mécanisme que pour une UE).
     */
    @Transactional(readOnly = true)
    public FindFormData buildFindForm(long findId,
                                      PersonDTO personDto,
                                      Set<Long> accessibleInstitutionIds,
                                      String lang) {
        SpecimenDTO specimen = specimenService.findAccessibleById(findId, accessibleInstitutionIds)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Find not found"));
        InstitutionDTO institution = specimen.getCreatedByInstitution();
        ConceptDTO specimenType = specimen.getType();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Find has no owning institution");
        }
        CustomForm customForm = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(specimenType, institution);
        if (customForm == null) {
            return new FindFormData(specimenType, null, Map.of(), Map.of());
        }
        FormUiDto formUiDto = conversionService.convert(customForm, FormUiDto.class);
        FieldSource fieldSource = new PanelFieldSource(formUiDto);
        String layoutJson = customFormLayoutConverter.convertToDatabaseColumn(customForm.getLayout());
        RecordingUnitFormBundle formBundle = new RecordingUnitFormBundle(
                customForm.getId(), customForm.getName(), customForm.getDescription(), layoutJson);
        Long typeIdForLog = specimenType != null ? specimenType.getId() : null;
        Locale locale = langService.localeForApiLang(lang);
        Map<String, RecordingUnitFormFieldApi> fields = buildCustomFormFieldsForBindTarget(
                specimen, fieldSource, "mobilité id=" + findId, typeIdForLog, locale);
        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Map<String, List<ConceptAutocompleteDTO>> vocabs = loadVocabularies(fieldSource, userInfo);
        return new FindFormData(specimenType, formBundle, fields, vocabs);
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
            if (vm == null) {
                continue;
            }
            CustomFieldAnswer jpa = e.getValue();
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

    /**
     * Création d'une UE : formulaire résolu par type + institution du projet ; valeurs dans {@code fieldAnswers}
     * (clés = id de champ). Champs non reconnus ou non supportés en v1 sont ignorés (log).
     */
    @Transactional
    public RecordingUnitMobileDetailData createRecordingUnit(RecordingUnitCreateRequest request,
                                                             PersonDTO personDto,
                                                             Set<Long> accessibleInstitutionIds,
                                                             String lang) {
        if (request.getActionUnitId() == null || request.getRecordingUnitTypeConceptId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "actionUnitId et recordingUnitTypeConceptId sont obligatoires");
        }
        ActionUnitDTO au = actionUnitService.findById(request.getActionUnitId());
        if (au == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Projet introuvable");
        }
        InstitutionDTO institution = au.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans organisation");
        }
        if (accessibleInstitutionIds == null || !accessibleInstitutionIds.contains(institution.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organisation hors périmètre");
        }
        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        if (!permissionService.hasWritePermission(userInfo, au)) {
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

        RecordingUnitDTO shell = new RecordingUnitDTO();
        shell.setType(typeDto);
        shell.setCreatedByInstitution(institution);
        shell.setActionUnit(actionUnitSummaryFromFull(au));
        shell.setAuthor(personDto);
        shell.setCreatedBy(personDto);
        shell.setContributors(new ArrayList<>(List.of(personDto)));
        shell.setOpeningDate(OffsetDateTime.now());
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

        FormUiDto formUiDto = conversionService.convert(customForm, FormUiDto.class);
        FieldSource fieldSource = new PanelFieldSource(formUiDto);
        CustomFormResponseViewModel response = formService.initOrReuseResponse(null, shell, fieldSource, true);
        mergeFieldAnswers(shell, response, fieldSource, request.getFieldAnswers(), lang);
        formService.updateJpaEntityFromResponse(response, shell);

        RecordingUnitDTO created;
        try {
            created = recordingUnitService.save(shell);
        } catch (FailedRecordingUnitSaveException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }

        String generated = recordingUnitService.generateFullIdentifier(created.getActionUnit(), created);
        created.setFullIdentifier(generated);
        if (recordingUnitService.fullIdentifierAlreadyExistInAction(created)) {
            created.setFullIdentifier(created.getActionUnit().getRecordingUnitIdentifierFormat());
        }
        try {
            created = recordingUnitService.save(created);
        } catch (FailedRecordingUnitSaveException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }

        String key = created.getId() != null ? String.valueOf(created.getId()) : created.getFullIdentifier();
        return buildMobileDetail(key, personDto, accessibleInstitutionIds, null, lang);
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

        InstitutionDTO institution = dto.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unité sans organisation");
        }
        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        if (!permissionService.hasWritePermission(userInfo, dto)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification non autorisée");
        }

        Map<String, Object> answers = request.getFieldAnswers() != null ? request.getFieldAnswers() : Map.of();
        if (answers.isEmpty()) {
            return buildMobileDetail(recordingUnitKey, personDto, accessibleInstitutionIds, null, lang);
        }

        CustomForm customForm = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(dto.getType(), institution);
        if (customForm == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Aucun formulaire personnalisé pour ce type d'UE : impossible d'appliquer fieldAnswers");
        }

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

        return buildMobileDetail(recordingUnitKey, personDto, accessibleInstitutionIds, null, lang);
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
        if (fieldAnswers == null || fieldAnswers.isEmpty() || response.getAnswers() == null) {
            return;
        }
        for (Map.Entry<String, Object> e : fieldAnswers.entrySet()) {
            Long fieldId;
            try {
                fieldId = Long.parseLong(e.getKey());
            } catch (NumberFormatException ex) {
                log.debug("Clé de champ ignorée (non numérique): {}", e.getKey());
                continue;
            }
            CustomField field = fieldSource.findFieldById(fieldId);
            if (field == null) {
                log.debug("Champ id={} absent du formulaire effectif", fieldId);
                continue;
            }
            CustomFieldAnswerViewModel vm = findViewModelForField(response.getAnswers(), field);
            if (vm == null) {
                continue;
            }
            Object typed;
            try {
                typed = coerceAnswerValue(field, e.getValue(), bindTarget);
            } catch (ResponseStatusException rex) {
                throw rex;
            } catch (RuntimeException ex) {
                log.warn("Valeur ignorée pour champ id={} ({}): {}", fieldId, field.getClass().getSimpleName(), ex.toString());
                continue;
            }
            if (typed == null && e.getValue() != null) {
                log.debug("Valeur non convertible pour champ id={} type {}", fieldId, field.getClass().getSimpleName());
                continue;
            }
            formService.applyTypedValueToAnswer(vm, typed);
        }
    }

    private Object coerceAnswerValue(CustomField field, Object raw, RecordingUnitDTO ru) {
        if (raw == null) {
            return null;
        }
        if (field instanceof CustomFieldInteger) {
            if (raw instanceof Number n) {
                return n.intValue();
            }
            return Integer.parseInt(String.valueOf(raw));
        }
        if (field instanceof CustomFieldText) {
            return String.valueOf(raw);
        }
        if (field instanceof CustomFieldDateTime) {
            if (raw instanceof OffsetDateTime odt) {
                return odt;
            }
            if (raw instanceof String s) {
                return OffsetDateTime.parse(s);
            }
            throw new IllegalArgumentException("Format datetime attendu (chaîne ISO-8601 ou OffsetDateTime)");
        }
        if (field instanceof CustomFieldSelectOneFromFieldCode) {
            long conceptId = requireLongId(raw, "concept");
            Concept c = conceptRepository.findById(conceptId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Concept introuvable: " + conceptId));
            return conceptMapper.convert(c);
        }
        if (field instanceof CustomFieldSelectOnePerson) {
            long personId = requireLongId(raw, "personne");
            Person p = personService.findById(personId);
            if (p == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Personne introuvable: " + personId);
            }
            return personMapper.convert(p);
        }
        if (field instanceof CustomFieldSelectOneActionUnit) {
            long actionId = requireLongId(raw, "unité d'action");
            ActionUnitDTO au = actionUnitService.findById(actionId);
            if (au == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet introuvable: " + actionId);
            }
            return actionUnitSummaryFromFull(au);
        }
        if (field instanceof CustomFieldSelectOneSpatialUnit) {
            long suId = requireLongId(raw, "unité spatiale");
            try {
                return new SpatialUnitSummaryDTO(spatialUnitService.findById(suId));
            } catch (RuntimeException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unité spatiale introuvable: " + suId);
            }
        }
        log.debug("Type de champ non pris en charge pour l'API v1: {}", field.getClass().getSimpleName());
        return null;
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
