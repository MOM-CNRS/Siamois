package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionUnit;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.person.CustomFieldSelectMultiplePerson;
import fr.siamois.domain.models.form.customfield.person.CustomFieldSelectOnePerson;
import fr.siamois.domain.models.form.customfield.phase.CustomFieldSelectMultiplePhase;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldSelectMultipleRecordingUnit;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectMultipleSpatialUnitTree;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneSpatialUnit;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultipleFromFieldCode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.form.EffectiveFormResolver;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.*;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.PhaseRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.mapper.PhaseMapper;
import fr.siamois.mapper.UnitDefinitionMapper;
import fr.siamois.ui.api.openapi.v1.OpenApiExecutionContext;
import fr.siamois.ui.api.openapi.v1.OpenApiParamIds;
import fr.siamois.ui.api.openapi.v1.exception.SyncRevisionConflictException;
import fr.siamois.ui.api.openapi.v1.mapper.FindOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitPatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.find.FindCreateFormData;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.api.openapi.v1.resource.form.*;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectFormData;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitCreateFormData;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.resource.type.FindDefaultType;
import fr.siamois.ui.api.openapi.v1.resource.type.RecordingUnitDefaultType;
import fr.siamois.ui.api.openapi.v1.resource.type.RecordingUnitIdentifierConfig;
import fr.siamois.ui.api.openapi.v1.resource.type.RecordingUnitType;
import fr.siamois.ui.api.openapi.v1.response.project.type.ProjectFindTypeListResponse;
import fr.siamois.ui.api.openapi.v1.response.project.type.ProjectRecordingUnitTypeListResponse;
import fr.siamois.ui.api.openapi.v1.response.sync.SyncConflictData;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.form.dto.FormUiDtoLayoutJson;
import fr.siamois.ui.form.fieldsource.FieldSource;
import fr.siamois.ui.form.fieldsource.PanelFieldSource;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
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
import java.time.ZoneOffset;
import java.util.*;

/**
 * Enrichissement OpenAPI pour le détail UE et le formulaire de création : formulaire effectif, champs et vocabulaires.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingUnitOpenApiService {

    public static final String CONCEPTS = "concepts";
    private final RecordingUnitService recordingUnitService;
    private final FormService formService;
    private final FieldConfigurationService fieldConfigurationService;
    private final RecordingUnitResponseMapper recordingUnitResponseMapper;
    private final ConversionService conversionService;
    private final EffectiveFormResolver effectiveFormResolver;
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
    private final FindOpenApiMapper findOpenApiMapper;
    private final LabelService labelService;
    private final UnitDefinitionMapper unitDefinitionMapper;
    private final PhaseRepository phaseRepository;
    private final PhaseMapper phaseMapper;
    private final TableFieldConfigService tableFieldConfigService;

    @Transactional(readOnly = true)
    public RecordingUnitResource buildMobileDetail(String recordingUnitKey, PersonDTO personDto, Set<Long> accessibleInstitutionIds,
                                                   List<String> counts, String lang) {
        return resolveMobileDetail(recordingUnitKey, personDto, accessibleInstitutionIds, counts, lang);
    }

    private RecordingUnitResource resolveMobileDetail(String recordingUnitKey, PersonDTO personDto,
                                                      Set<Long> accessibleInstitutionIds,
                                                      List<String> counts, String lang) {
        RecordingUnitService.AccessibleRecordingUnit bundle =
                recordingUnitService.findAccessibleRecordingUnitWithEntity(recordingUnitKey, accessibleInstitutionIds, counts);
        RecordingUnitDTO dto = bundle.dto();

        RecordingUnitResource resource = recordingUnitResponseMapper.convert(dto);
        if (resource.getType() != null && dto.getType() != null) {
            resource.getType().setResolvedLabel(labelService.findLabelOf(dto.getType(), lang).getLabel());
        }
        if (!profilePermissionService.canViewRecordingUnit(personDto, dto)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unité introuvable ou non accessible");
        }

        InstitutionDTO institution = dto.getCreatedByInstitution();
        if (institution == null) {
            resource.setAnswers(Map.of());
            return resource;
        }

        Long projectId = dto.getActionUnit() != null ? dto.getActionUnit().getId() : null;
        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Locale locale = langService.localeForApiLang(lang);
        Map<String, FieldAnswer> fields = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
            FormUiDto formUiDto = effectiveFormResolver.resolveEffectiveForm(
                    RecordingUnit.DETAILS_FORM, projectId, ConfigurableTable.UE,
                    dto.getType() != null ? dto.getType().getId() : null);
            FieldSource fieldSource = new PanelFieldSource(formUiDto);
            return buildFieldsWithFallback(dto, fieldSource, locale);
        });

        resource.setAnswers(fields);
        return resource;
    }

    private FieldAnswer toTypedAnswer(String answerType, FieldResource field, Object raw, String lang) {
        return switch (answerType) {
            case "TEXT" -> new TextFieldAnswer(answerType, field, raw instanceof String s ? s : null);
            case "INTEGER" -> new IntegerFieldAnswer(answerType, field, raw instanceof Integer i ? i : null);
            case "DATETIME" -> new DateFieldAnswer(answerType, field, raw instanceof OffsetDateTime dt ? dt : null);
            case "SELECT_ONE_FROM_FIELD_CODE", "SELECT_ONE_PERSON", "SELECT_ONE_ACTION_UNIT",
                 "SELECT_ONE_SPATIAL_UNIT", "SELECT_ONE_ACTION_CODE", "SELECT_ONE_RECORDING_UNIT",
                 "SELECT_ADDRESS", "SELECT_ONE" ->
                    new SelectOneFieldAnswer(answerType, field, toResourceRef(answerType, raw, lang));
            case "SELECT_MULTIPLE_PERSON", "SELECT_MULTIPLE_FROM_FIELD_CODE",
                 "SELECT_MULTIPLE_RECORDING_UNIT", "SELECT_MULTIPLE_SPATIAL_UNIT_TREE",
                 "SELECT_MULTIPLE_SPECIMEN", "SELECT_MULTIPLE_CONTAINER",
                 "SELECT_MULTIPLE_PHASE", "SELECT_MULTIPLE" ->
                    new SelectManyFieldAnswer(answerType, field, toResourceRefList(answerType, raw, lang));
            case "MEASUREMENT" -> new MeasurementFieldAnswer(answerType, field, toMeasurementRef(raw));
            default -> new TextFieldAnswer(answerType, field, raw != null ? raw.toString() : null);
        };
    }

    private ResourceRef toResourceRef(String answerType, Object raw, String lang) {
        if (raw == null) return null;
        return switch (answerType) {
            case "SELECT_ONE_FROM_FIELD_CODE" -> conceptResourceRef(raw, lang);
            case "SELECT_ONE_PERSON" -> {
                if (raw instanceof PersonDTO p)
                    yield new ResourceRef(String.valueOf(p.getId()), "persons", p.displayName());
                yield null;
            }
            case "SELECT_ONE_ACTION_UNIT" -> {
                if (raw instanceof ActionUnitDTO a)
                    yield new ResourceRef(String.valueOf(a.getId()), "action-units", a.getName());
                yield null;
            }
            case "SELECT_ONE_SPATIAL_UNIT" -> {
                if (raw instanceof SpatialUnitSummaryDTO s)
                    yield new ResourceRef(String.valueOf(s.getId()), "spatial-units", s.getName());
                yield null;
            }
            case "SELECT_ONE_ACTION_CODE" -> {
                if (raw instanceof ActionCodeDTO ac)
                    yield new ResourceRef(String.valueOf(ac.getId()), "action-codes", ac.getCode());
                yield null;
            }
            case "SELECT_ONE_RECORDING_UNIT" -> {
                if (raw instanceof RecordingUnitSummaryDTO r)
                    yield new ResourceRef(String.valueOf(r.getId()), "recording-units", r.getFullIdentifier());
                yield null;
            }
            default -> null;
        };
    }

    private ResourceRef conceptResourceRef(Object raw, String lang) {
        ConceptDTO concept = null;
        String preferredLabel = null;
        if (raw instanceof ConceptDTO c) {
            concept = c;
        } else if (raw instanceof ConceptAutocompleteDTO ac) {
            concept = ac.concept();
            if (ac.getConceptLabelToDisplay() != null) {
                preferredLabel = ac.getConceptLabelToDisplay().getLabel();
            }
            if ((preferredLabel == null || preferredLabel.isBlank()) && ac.getOriginalPrefLabel() != null) {
                preferredLabel = ac.getOriginalPrefLabel();
            }
        }
        if (concept == null) {
            return null;
        }
        String label = preferredLabel;
        if (label == null || label.isBlank()) {
            try {
                label = labelService.findLabelOf(concept, lang).getLabel();
            } catch (RuntimeException ignored) {
                label = null;
            }
        }
        if (label == null || label.isBlank()) {
            label = concept.getExternalId();
        }
        return new ResourceRef(String.valueOf(concept.getId()), CONCEPTS, label);
    }

    private List<ResourceRef> toResourceRefList(String answerType, Object raw, String lang) {
        if (raw == null) return null;
        Collection<?> col = raw instanceof Collection<?> c ? c : List.of(raw);
        return col.stream()
                .map(item -> toResourceRefFromItem(answerType, item, lang))
                .filter(Objects::nonNull)
                .toList();
    }

    private ResourceRef toResourceRefFromItem(String answerType, Object item, String lang) {
        if (item instanceof PersonDTO p)
            return new ResourceRef(String.valueOf(p.getId()), "persons", p.displayName());
        if (item instanceof ConceptDTO || item instanceof ConceptAutocompleteDTO)
            return conceptResourceRef(item, lang);
        if (item instanceof PhaseDTO p) {
            String label = p.getTitle() != null && !p.getTitle().isBlank() ? p.getTitle() : p.getIdentifier();
            return new ResourceRef(String.valueOf(p.getId()), "phases", label);
        }
        if (item instanceof SpatialUnitSummaryDTO s)
            return new ResourceRef(String.valueOf(s.getId()), "spatial-units", s.getName());
        if (item instanceof RecordingUnitSummaryDTO r)
            return new ResourceRef(String.valueOf(r.getId()), "recording-units", r.getFullIdentifier());
        if (item instanceof AbstractEntityDTO e)
            return new ResourceRef(String.valueOf(e.getId()), answerType.toLowerCase(), null);
        return null;
    }

    private MeasurementRef toMeasurementRef(Object raw) {
        if (raw instanceof MeasurementAnswerDTO m) {
            String symbol = m.getUnit() != null ? m.getUnit().getSymbol() : null;
            return new MeasurementRef(m.getNumericValue(), symbol, m.getNormalizedValue(), m.getComment());
        }
        return null;
    }

    @Transactional
    public ProjectRecordingUnitTypeListResponse buildProjectRecordingUnitTypeSettings(
            String projectKey, PersonDTO personDto, Set<Long> accessibleInstitutionIds, String lang) {
        AccessibleProjectForApi project = actionUnitService.findAccessibleProjectByKey(projectKey, accessibleInstitutionIds);
        ActionUnitDTO au = project.actionUnit();
        InstitutionDTO institution = au.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans organisation");
        }

        RecordingUnitIdentifierConfig identifierConfig = buildIdentifierConfig(au.getId(), null);
        RecordingUnitDefaultType defaultType = buildDefaultType(au.getId(), institution, personDto, lang, identifierConfig);

        List<Concept> configuredTypes = formService.findConfiguredRecordingUnitTypesByInstitution(institution);
        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Locale locale = langService.localeForApiLang(lang);
        List<RecordingUnitType> types = configuredTypes.stream()
                .map(concept -> buildRecordingUnitType(au.getId(), concept, userInfo, locale,
                        buildIdentifierConfig(au.getId(), concept.getId())))
                .toList();

        return new ProjectRecordingUnitTypeListResponse(types, defaultType);
    }

    @Transactional(readOnly = true)
    public ProjectFindTypeListResponse buildProjectFindTypeSettings(
            String projectKey, PersonDTO personDto, Set<Long> accessibleInstitutionIds, String lang) {
        AccessibleProjectForApi project = actionUnitService.findAccessibleProjectByKey(projectKey, accessibleInstitutionIds);
        ActionUnitDTO au = project.actionUnit();
        InstitutionDTO institution = au.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans organisation");
        }

        FormUiDto systemForm = Specimen.NEW_UNIT_FORM;
        FormUiDto formUiDto = conversionService.convert(systemForm, FormUiDto.class);
        FieldSource fieldSource = new PanelFieldSource(formUiDto);
        String layoutJson = FormUiDtoLayoutJson.serialize(systemForm.getLayout());
        FormResource formBundle = new FormResource(layoutJson);

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Locale locale = langService.localeForApiLang(lang);
        Map<String, FieldResource> fields = OpenApiExecutionContext.callWithUserInfo(
                userInfo, () -> buildFieldsMetadataOnly(fieldSource, locale));

        FindDefaultType defaultType = new FindDefaultType();
        defaultType.setFormBundle(formBundle);
        defaultType.setFields(fields);

        return new ProjectFindTypeListResponse(List.of(), defaultType);
    }

    private RecordingUnitIdentifierConfig buildIdentifierConfig(Long projectId, Long typeConceptId) {
        FormConfig stored = tableFieldConfigService.resolveIdentifierConfig(
                projectId, ConfigurableTable.UE, typeConceptId);
        RecordingUnitIdentifierConfig config = new RecordingUnitIdentifierConfig();
        config.setIdentifierFormat(stored.getIdentifierFormat());
        config.setMaxCode(stored.getMaxCode());
        config.setMinCode(stored.getMinCode());
        return config;
    }

    private RecordingUnitDefaultType buildDefaultType(Long projectId, InstitutionDTO institution, PersonDTO personDto, String lang,
                                                      RecordingUnitIdentifierConfig identifierConfig) {
        RecordingUnitDefaultType defaultType = new RecordingUnitDefaultType();
        defaultType.setIdentifierConfig(identifierConfig);

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Locale locale = langService.localeForApiLang(lang);
        Map<String, FieldResource> fields = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
            FormUiDto formUiDto = effectiveFormResolver.resolveEffectiveForm(
                    RecordingUnit.DETAILS_FORM, projectId, ConfigurableTable.UE, null);
            FieldSource fieldSource = new PanelFieldSource(formUiDto);
            defaultType.setFormBundle(new FormResource(FormUiDtoLayoutJson.serialize(formUiDto.getLayout())));
            return buildFieldsMetadataOnly(fieldSource, locale);
        });
        defaultType.setFields(fields);
        return defaultType;
    }

    private RecordingUnitType buildRecordingUnitType(Long projectId, Concept concept,
                                                     UserInfo userInfo, Locale locale,
                                                     RecordingUnitIdentifierConfig identifierConfig) {
        ConceptDTO typeDto = conceptMapper.convert(concept);
        RecordingUnitType type = new RecordingUnitType();
        type.setConcept(toConceptResource(typeDto, locale.getLanguage()));
        type.setId(String.valueOf(concept.getId()));
        type.setIdentifierConfig(identifierConfig);

        Map<String, FieldResource> fields = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
            FormUiDto formUiDto = effectiveFormResolver.resolveEffectiveForm(
                    RecordingUnit.DETAILS_FORM, projectId, ConfigurableTable.UE, concept.getId());
            FieldSource fieldSource = new PanelFieldSource(formUiDto);
            type.setFormBundle(new FormResource(FormUiDtoLayoutJson.serialize(formUiDto.getLayout())));
            return buildFieldsMetadataOnly(fieldSource, locale);
        });
        type.setFields(fields);
        return type;
    }

    /**
     * Gabarit UI du formulaire de création projet ({@link ActionUnit#NEW_UNIT_FORM}) : layout et métadonnées des champs.
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
     * Applique les réponses du formulaire de création projet ({@link ActionUnit#NEW_UNIT_FORM}) sur un shell avant save.
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
            FormUiDto systemForm = ActionUnit.NEW_UNIT_FORM;
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
        FormUiDto systemForm = ActionUnit.NEW_UNIT_FORM;
        FormUiDto formUiDto = conversionService.convert(systemForm, FormUiDto.class);
        FieldSource fieldSource = new PanelFieldSource(formUiDto);
        String layoutJson = FormUiDtoLayoutJson.serialize(systemForm.getLayout());
        FormResource formBundle = new FormResource(layoutJson);

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Locale locale = langService.localeForApiLang(lang);
        Map<String, FieldResource> fields = OpenApiExecutionContext.callWithUserInfo(
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
        ResolvedConceptResource typeResource = toConceptResource(typeDto, lang);

        FormUiDto formUiDto = RecordingUnit.NEW_UNIT_FORM;
        FieldSource fieldSource = new PanelFieldSource(formUiDto);
        String layoutJson = FormUiDtoLayoutJson.serialize(formUiDto.getLayout());
        FormResource formBundle = new FormResource(layoutJson);

        RecordingUnitDTO shell = new RecordingUnitDTO();
        shell.setType(typeDto);
        shell.setCreatedByInstitution(institution);

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Locale locale = langService.localeForApiLang(lang);
        Map<String, FieldResource> fields = OpenApiExecutionContext.callWithUserInfo(
                userInfo, () -> buildFieldsMetadataOnly(fieldSource, locale));

        return new RecordingUnitCreateFormData(typeResource, formBundle, fields);
    }

    /**
     * Gabarit UI pour création d'un mobilier : layout et métadonnées ({@link Specimen#NEW_UNIT_FORM}, comme le dialog web).
     */
    @Transactional(readOnly = true)
    public FindCreateFormData buildFindCreateForm(long organizationId,
                                                  long findTypeConceptId,
                                                  PersonDTO personDto,
                                                  String lang) {
        InstitutionDTO institution = institutionService.findById(organizationId);
        if (institution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found");
        }
        Concept typeConcept = conceptRepository.findById(findTypeConceptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Type de mobilier introuvable"));
        ConceptDTO typeDto = conceptMapper.convert(typeConcept);
        ResolvedConceptResource typeResource = toConceptResource(typeDto, lang);

        FormUiDto systemForm = Specimen.NEW_UNIT_FORM;
        FormUiDto formUiDto = conversionService.convert(systemForm, FormUiDto.class);
        FieldSource fieldSource = new PanelFieldSource(formUiDto);
        String layoutJson = FormUiDtoLayoutJson.serialize(systemForm.getLayout());
        FormResource formBundle = new FormResource(layoutJson);

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Locale locale = langService.localeForApiLang(lang);
        Map<String, FieldResource> fields = OpenApiExecutionContext.callWithUserInfo(
                userInfo, () -> buildFieldsMetadataOnly(fieldSource, locale));

        return new FindCreateFormData(typeResource, formBundle, fields);
    }

    /**
     * Mobilier existant : champs avec leurs valeurs ({@link Specimen#DETAILS_FORM}, comme le panel web).
     */
    @Transactional(readOnly = true)
    public FindResource buildFindMobilierForm(String idOrKey,
                                              PersonDTO personDto,
                                              Set<Long> accessibleInstitutionIds,
                                              String lang) {
        SpecimenDTO specimen = specimenService.findAccessibleByKey(idOrKey, accessibleInstitutionIds)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mobilier introuvable ou hors périmètre"));

        FindResource resource = findOpenApiMapper.toResource(specimen);

        InstitutionDTO institution = specimen.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            resource.setAnswers(Map.of());
            return resource;
        }

        FormUiDto systemForm = Specimen.DETAILS_FORM;
        FormUiDto formUiDto = conversionService.convert(systemForm, FormUiDto.class);
        FieldSource fieldSource = new PanelFieldSource(formUiDto);
        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Locale locale = langService.localeForApiLang(lang);

        Map<String, FieldAnswer> answers = OpenApiExecutionContext.callWithUserInfo(
                userInfo, () -> buildSpecimenFieldsWithFallback(specimen, fieldSource, locale));

        resource.setAnswers(answers);
        return resource;
    }

    private Map<String, FieldAnswer> buildSpecimenFieldsWithFallback(SpecimenDTO specimen,
                                                                      FieldSource fieldSource,
                                                                      Locale locale) {
        try {
            CustomFormResponseViewModel response = formService.initOrReuseResponse(null, specimen, fieldSource, true);
            return toFieldsMap(response, fieldSource, locale);
        } catch (RuntimeException ex) {
            log.warn("Impossible de construire les réponses formulaire pour le mobilier id={} (fallback null): {}",
                    specimen.getId(), ex.toString(), ex);
            return buildNullAnswersMap(fieldSource, locale);
        }
    }

    /**
     * Construit les réponses typées avec valeurs ; si le moteur de réponses échoue, retourne des réponses sans valeur.
     */
    private Map<String, FieldAnswer> buildFieldsWithFallback(RecordingUnitDTO dto,
                                                             FieldSource fieldSource,
                                                             Locale locale) {
        try {
            CustomFormResponseViewModel response = formService.initOrReuseResponse(null, dto, fieldSource, true);
            return toFieldsMap(response, fieldSource, locale);
        } catch (RuntimeException ex) {
            log.warn("Impossible de construire les réponses formulaire pour l'UE id={} (fallback métadonnées seules): {}",
                    dto.getId(), ex.toString(), ex);
            return buildNullAnswersMap(fieldSource, locale);
        }
    }

    private Map<String, FieldAnswer> toFieldsMap(CustomFormResponseViewModel response, FieldSource fallback, Locale locale) {
        if (response.getAnswers() == null) return buildNullAnswersMap(fallback, locale);
        Map<String, FieldAnswer> out = new LinkedHashMap<>();
        String lang = locale.getLanguage();
        for (Map.Entry<CustomField, CustomFieldAnswerViewModel> e : response.getAnswers().entrySet()) {
            CustomField field = e.getKey();
            FieldResource fieldResource = toFieldResource(field, locale);
            out.put(String.valueOf(field.getId()),
                    toTypedAnswer(answerTypeDiscriminator(field), fieldResource,
                            formService.readAnswerValueForApi(e.getValue()), lang));
        }
        return out;
    }

    private Map<String, FieldAnswer> buildNullAnswersMap(FieldSource fieldSource, Locale locale) {
        Map<String, FieldAnswer> out = new LinkedHashMap<>();
        String lang = locale.getLanguage();
        for (CustomField field : fieldSource.getAllFields()) {
            if (field == null || field.getId() == null) continue;
            FieldResource fieldResource = toFieldResource(field, locale);
            out.put(String.valueOf(field.getId()),
                    toTypedAnswer(answerTypeDiscriminator(field), fieldResource, null, lang));
        }
        return out;
    }

    private Map<String, FieldResource> buildFieldsMetadataOnly(FieldSource fieldSource, Locale locale) {
        Map<String, FieldResource> fields = new LinkedHashMap<>();
        for (CustomField field : fieldSource.getAllFields()) {
            if (field == null || field.getId() == null) continue;
            fields.put(String.valueOf(field.getId()), toFieldResource(field, locale));
        }
        return fields;
    }

    private FieldResource toFieldResource(CustomField field, Locale locale) {
        String label = langService.resolveMessage(field.getLabel(), locale);
        String hint = langService.resolveMessage(field.getHint(), locale);
        String fieldCode = null;
        if (field instanceof CustomFieldSelectOneFromFieldCode one) {
            fieldCode = one.getFieldCode();
        } else if (field instanceof CustomFieldSelectMultipleFromFieldCode multi) {
            fieldCode = multi.getFieldCode();
        }
        return new FieldResource(
                String.valueOf(field.getId()),
                "fields",
                label,
                answerTypeDiscriminator(field),
                hint,
                field.getIsSystemField(),
                field.getValueBinding(),
                fieldCode);
    }

    private ResolvedConceptResource toConceptResource(ConceptDTO concept, String lang) {
        ResolvedConceptResource r = new ResolvedConceptResource();
        r.setResourceType(CONCEPTS);
        r.setId(String.valueOf(concept.getId()));
        r.setExternalUrl(concept.getExternalId());
        r.setResolvedLabel(labelService.findLabelOf(concept, lang).getLabel());
        return r;
    }

    private static String answerTypeDiscriminator(CustomField field) {
        DiscriminatorValue dv = field.getClass().getAnnotation(DiscriminatorValue.class);
        return dv != null ? dv.value() : field.getClass().getSimpleName();
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
    public List<RecordingUnitResource> buildRecordingUnitChildren(String recordingUnitKey,
                                                                Set<Long> accessibleInstitutionIds) {
        List<RecordingUnitSummaryDTO> children =
                recordingUnitService.findChildrenForAccessibleRecordingUnit(recordingUnitKey, accessibleInstitutionIds);
        return children.stream()
                .map(recordingUnitResponseMapper::toResource)
                .toList();
    }

    @Transactional
    public void addExistingChild(String recordingUnitKey,
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
    }

    @Transactional
    public void addExistingParent(String recordingUnitKey,
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
    }

    @Transactional
    public void removeExistingChild(String recordingUnitKey,
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
    }

    @Transactional
    public void removeExistingParent(String recordingUnitKey,
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
    public RecordingUnitResource createRecordingUnit(RecordingUnitCreateRequest request,
                                                     PersonDTO personDto,
                                                     Set<Long> accessibleInstitutionIds,
                                                     String lang) {
        String projectKey = OpenApiParamIds.requireNonBlank(request.getProjectId(), "actionUnitId");
        if (request.getTypeId() == null) {
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
        Concept typeConcept = conceptRepository.findById(Long.parseLong(request.getTypeId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Type d'UE introuvable"));
        ConceptDTO typeDto = conceptMapper.convert(typeConcept);

        RecordingUnitDTO shell = initRecordingUnitShell(typeDto, au, institution, personDto);

        RecordingUnitDTO created = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
            FormUiDto formUiDto = effectiveFormResolver.resolveEffectiveForm(
                    RecordingUnit.DETAILS_FORM, au.getId(), ConfigurableTable.UE, typeDto.getId());
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
        if (shell.getFullIdentifier() == null || shell.getFullIdentifier().isBlank()) shell.setFullIdentifier("PENDING");
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
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Generated recording-unit identifier already exists");
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
    public RecordingUnitResource patchRecordingUnit(String recordingUnitKey,
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

        Long projectId = dto.getActionUnit() != null ? dto.getActionUnit().getId() : null;

        OpenApiExecutionContext.runWithUserInfo(userInfo, () -> {
            FormUiDto formUiDto = effectiveFormResolver.resolveEffectiveForm(
                    RecordingUnit.DETAILS_FORM, projectId, ConfigurableTable.UE,
                    dto.getType() != null ? dto.getType().getId() : null);
            FieldSource fieldSource = new PanelFieldSource(formUiDto);
            CustomFormResponseViewModel response = formService.initOrReuseResponse(null, dto, fieldSource, true);
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
        RecordingUnitResource serverState = resolveMobileDetail(
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
        s.setType(au.getType());
        s.setBeginDate(au.getBeginDate());
        s.setEndDate(au.getEndDate());
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
        if (field instanceof CustomFieldSelectMultiplePerson) return ruCoercePersonList(raw);
        if (field instanceof CustomFieldSelectMultipleFromFieldCode) return ruCoerceConceptList(raw);
        if (field instanceof CustomFieldMeasurement measurementField) {
            return ruCoerceMeasurement(raw, measurementField);
        }
        if (field instanceof CustomFieldSelectMultipleSpatialUnitTree) {
            return ruCoerceSpatialUnitList(raw);
        }
        if (field instanceof CustomFieldSelectMultiplePhase) {
            return ruCoercePhaseList(raw, ru);
        }
        if (field instanceof CustomFieldSelectMultipleRecordingUnit) {
            return ruCoerceRecordingUnitList(raw);
        }
        log.debug("Type de champ non pris en charge pour l'API v1: {}", field.getClass().getSimpleName());
        return null;
    }

    private List<PersonDTO> ruCoercePersonList(Object raw) {
        Collection<?> items = raw instanceof Collection<?> c ? c : List.of(raw);
        List<PersonDTO> out = new ArrayList<>();
        for (Object item : items) {
            out.add((PersonDTO) ruCoercePerson(item));
        }
        return out;
    }

    private List<ConceptDTO> ruCoerceConceptList(Object raw) {
        Collection<?> items = raw instanceof Collection<?> c ? c : List.of(raw);
        List<ConceptDTO> out = new ArrayList<>();
        for (Object item : items) {
            out.add((ConceptDTO) ruCoerceConcept(item));
        }
        return out;
    }

    private Set<SpatialUnitSummaryDTO> ruCoerceSpatialUnitList(Object raw) {
        Collection<?> items = raw instanceof Collection<?> c ? c : List.of(raw);
        Set<SpatialUnitSummaryDTO> out = new LinkedHashSet<>();
        for (Object item : items) {
            out.add((SpatialUnitSummaryDTO) ruCoerceSpatialUnit(item));
        }
        return out;
    }

    private Set<RecordingUnitSummaryDTO> ruCoerceRecordingUnitList(Object raw) {
        Collection<?> items = raw instanceof Collection<?> c ? c : List.of(raw);
        Set<RecordingUnitSummaryDTO> out = new LinkedHashSet<>();
        for (Object item : items) {
            long id = requireLongId(item, "unité d'enregistrement");
            RecordingUnitSummaryDTO summary = new RecordingUnitSummaryDTO();
            summary.setId(id);
            if (item instanceof Map<?, ?> map) {
                Object label = map.get("fullIdentifier");
                if (label == null) label = map.get("label");
                if (label != null) summary.setFullIdentifier(String.valueOf(label));
            }
            out.add(summary);
        }
        return out;
    }

    private Set<PhaseDTO> ruCoercePhaseList(Object raw, RecordingUnitDTO ru) {
        Collection<?> items = raw instanceof Collection<?> c ? c : List.of(raw);
        Set<PhaseDTO> out = new LinkedHashSet<>();
        Long actionUnitId = ru != null && ru.getActionUnit() != null ? ru.getActionUnit().getId() : null;
        for (Object item : items) {
            long id = requireLongId(item, "phase");
            Phase phase = phaseRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phase introuvable: " + id));
            if (actionUnitId != null && phase.getActionUnit() != null
                    && !Objects.equals(phase.getActionUnit().getId(), actionUnitId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phase hors projet: " + id);
            }
            out.add(phaseMapper.convert(phase));
        }
        return out;
    }

    private MeasurementAnswerDTO ruCoerceMeasurement(Object raw, CustomFieldMeasurement field) {
        MeasurementAnswerDTO dto = raw instanceof MeasurementAnswerDTO existing
                ? existing
                : buildMeasurementFromRaw(raw);
        return applyMeasurementFieldUnit(dto, field);
    }

    private MeasurementAnswerDTO buildMeasurementFromRaw(Object raw) {
        MeasurementAnswerDTO dto = new MeasurementAnswerDTO();
        if (raw instanceof Number n) {
            dto.setNumericValue(n.doubleValue());
            return dto;
        }
        if (raw instanceof Map<?, ?> map) {
            applyMeasurementMap(dto, map);
            return dto;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Mesure invalide : objet { numericValue, unit?, comment? } attendu");
    }

    private static void applyMeasurementMap(MeasurementAnswerDTO dto, Map<?, ?> map) {
        applyMeasurementNumericValue(dto, map.get("numericValue"));
        Object comment = map.get("comment");
        if (comment == null) {
            return;
        }
        String c = String.valueOf(comment).trim();
        if (!c.isEmpty()) {
            dto.setComment(c);
        }
    }

    private static void applyMeasurementNumericValue(MeasurementAnswerDTO dto, Object numeric) {
        if (numeric instanceof Number n) {
            dto.setNumericValue(n.doubleValue());
            return;
        }
        if (numeric != null && !String.valueOf(numeric).isBlank()) {
            dto.setNumericValue(Double.parseDouble(String.valueOf(numeric).trim().replace(',', '.')));
        }
    }

    /**
     * L'unité effective vient du champ formulaire (zInf, zSup, …). Le client mobile
     * n'envoie souvent que le symbole et parfois id=0, ce qui provoquerait un INSERT
     * incomplet sur unit_definition.
     */
    private MeasurementAnswerDTO applyMeasurementFieldUnit(MeasurementAnswerDTO dto,
                                                          CustomFieldMeasurement field) {
        if (field.getUnit() != null) {
            dto.setUnit(unitDefinitionMapper.convert(field.getUnit()));
            return dto;
        }

        UnitDefinitionDTO unit = dto.getUnit();
        if (unit != null && (unit.getId() == null || unit.getId() <= 0)) {
            unit.setId(null);
        }
        return dto;
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
            if (id == null) {
                id = m.get("resourceId");
            }
            if (id == null) {
                id = m.get("conceptId");
            }
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
