package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.ui.api.openapi.v1.resource.sibling.SiblingsResource;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.container.Container;
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
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.*;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.mapper.ConceptMapper;
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
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectDefaultType;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import fr.siamois.ui.table.definitions.ActionUnitTableColumnDefaults;
import fr.siamois.ui.table.definitions.RecordingUnitTableColumnDefaults;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectFieldConfigResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectTableColumnResource;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitCreateFormData;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.resource.type.FindDefaultType;
import fr.siamois.ui.api.openapi.v1.resource.type.FindType;
import fr.siamois.ui.api.openapi.v1.resource.type.ContainerDefaultType;
import fr.siamois.ui.api.openapi.v1.resource.type.ContainerType;
import fr.siamois.ui.api.openapi.v1.resource.type.PhaseDefaultType;
import fr.siamois.ui.api.openapi.v1.resource.type.PhaseType;
import fr.siamois.ui.api.openapi.v1.resource.type.RecordingUnitDefaultType;
import fr.siamois.ui.api.openapi.v1.resource.type.RecordingUnitIdentifierConfig;
import fr.siamois.ui.api.openapi.v1.resource.type.RecordingUnitType;
import fr.siamois.ui.api.openapi.v1.response.project.type.ProjectContainerTypeListResponse;
import fr.siamois.ui.api.openapi.v1.response.project.type.ProjectFindTypeListResponse;
import fr.siamois.ui.api.openapi.v1.response.project.type.ProjectPhaseTypeListResponse;
import fr.siamois.ui.api.openapi.v1.response.project.type.ProjectRecordingUnitTypeListResponse;
import fr.siamois.ui.api.openapi.v1.response.project.type.ProjectTypeListResponse;
import fr.siamois.ui.api.openapi.v1.response.sync.SyncConflictData;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.form.dto.FormUiDtoLayoutJson;
import fr.siamois.ui.form.fieldsource.FieldSource;
import fr.siamois.ui.form.fieldsource.PanelFieldSource;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
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
    private final FieldAnswerPatchService fieldAnswerPatchService;
    private final FieldAnswerWireService fieldAnswerWireService;
    private final FieldQueryService fieldQueryService;
    private final CustomFieldAnswerService customFieldAnswerService;
    private final ConceptMapper conceptMapper;
    private final InstitutionService institutionService;
    private final ConceptRepository conceptRepository;
    private final SpecimenService specimenService;
    private final LangService langService;
    private final ActionUnitService actionUnitService;
    private final ProfilePermissionService profilePermissionService;
    private final SpatialUnitService spatialUnitService;
    private final FindOpenApiMapper findOpenApiMapper;
    private final LabelService labelService;
    private final TableFieldConfigService tableFieldConfigService;
    private final ResourceBookmarkService resourceBookmarkService;
    private final EntitySiblingsService entitySiblingsService;
    private final ValidationOpenApiService validationOpenApiService;

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
            resource.setPermissions(ProjectResourcePermissions.of(false));
            return resource;
        }

        Long projectId = dto.getActionUnit() != null ? dto.getActionUnit().getId() : null;
        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        // Unlike the list (permissionsFor, batched per page), the detail computes this directly —
        // one row, same as ProjectControllerApi#getById. Was previously left unset entirely here
        // (see the field's own javadoc, now stale) — the React fiche's useCanEdit only ever
        // defaults an ABSENT block to "editable", so a viewer with no write right saw edit
        // affordances that would then 403 on PATCH.
        resource.setPermissions(ProjectResourcePermissions.of(
                profilePermissionService.hasRecordingUnitWritePermission(userInfo, dto))
                .withValidate(profilePermissionService.hasValidatePermission(userInfo, projectId)));
        resourceBookmarkService.markBookmarked(userInfo, resource);
        Locale locale = langService.localeForApiLang(lang);
        Map<String, FieldAnswer> fields = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
            FormUiDto formUiDto = effectiveFormResolver.resolveEffectiveForm(
                    RecordingUnit.DETAILS_FORM, projectId, ConfigurableTable.UE,
                    dto.getType() != null ? dto.getType().getId() : null);
            FieldSource fieldSource = new PanelFieldSource(formUiDto);
            return buildFieldsWithFallback(dto, fieldSource, locale);
        });

        // RecordingUnitResource.answers is Map<String, Object> (shared with the list's raw-value
        // shape) — wrap rather than assign directly, since Map<String, FieldAnswer> isn't a
        // Map<String, Object> under Java's invariant generics even though every value already is one.
        resource.setAnswers(new LinkedHashMap<>(fields));
        return resource;
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

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        RecordingUnitIdentifierConfig identifierConfig = buildIdentifierConfig(userInfo, au.getId(), ConfigurableTable.UE, null);
        RecordingUnitDefaultType defaultType = buildDefaultType(au.getId(), institution, personDto, lang, identifierConfig);

        List<Concept> configuredTypes = OpenApiExecutionContext.callWithUserInfo(userInfo,
                () -> tableFieldConfigService.listConfiguredTypeConcepts(au.getId(), ConfigurableTable.UE));
        Locale locale = langService.localeForApiLang(lang);
        List<RecordingUnitType> types = configuredTypes.stream()
                .map(concept -> buildRecordingUnitType(au.getId(), concept, userInfo, locale,
                        buildIdentifierConfig(userInfo, au.getId(), ConfigurableTable.UE, concept.getId())))
                .toList();

        // _default first, so a per-type override of a system field's label/hint (an effective-form
        // customization scoped to that one type) wins over the type-independent default — same
        // layering the effective-form resolver itself applies.
        Map<String, FieldResource> unionFields = new LinkedHashMap<>(defaultType.getFields());
        for (RecordingUnitType type : types) {
            unionFields.putAll(type.getFields());
        }

        return new ProjectRecordingUnitTypeListResponse(types, defaultType, unionFields);
    }

    @Transactional
    public ProjectFindTypeListResponse buildProjectFindTypeSettings(
            String projectKey, PersonDTO personDto, Set<Long> accessibleInstitutionIds, String lang) {
        AccessibleProjectForApi project = actionUnitService.findAccessibleProjectByKey(projectKey, accessibleInstitutionIds);
        ActionUnitDTO au = project.actionUnit();
        InstitutionDTO institution = au.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans organisation");
        }

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        RecordingUnitIdentifierConfig identifierConfig = buildIdentifierConfig(userInfo, au.getId(), ConfigurableTable.MOBILIER, null);
        FindDefaultType defaultType = buildFindDefaultType(au.getId(), institution, personDto, lang, identifierConfig);

        List<Concept> configuredTypes = OpenApiExecutionContext.callWithUserInfo(userInfo,
                () -> tableFieldConfigService.listConfiguredTypeConcepts(au.getId(), ConfigurableTable.MOBILIER));
        Locale locale = langService.localeForApiLang(lang);
        List<FindType> types = configuredTypes.stream()
                .map(concept -> buildFindType(au.getId(), concept, userInfo, locale,
                        buildIdentifierConfig(userInfo, au.getId(), ConfigurableTable.MOBILIER, concept.getId())))
                .toList();

        return new ProjectFindTypeListResponse(types, defaultType);
    }

    private RecordingUnitIdentifierConfig buildIdentifierConfig(UserInfo userInfo, Long projectId, ConfigurableTable table, Long typeConceptId) {
        FormConfig stored = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> tableFieldConfigService.resolveIdentifierConfig(
                projectId, table, typeConceptId));
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
            return buildFieldsMetadataOnly(fieldSource, locale, RecordingUnit.class);
        });
        defaultType.setFields(fields);
        defaultType.setTableColumns(buildRecordingUnitTableColumnDefaults());
        return defaultType;
    }

    /**
     * Défauts de colonnes de la liste des unités d'enregistrement, depuis
     * {@link RecordingUnitTableColumnDefaults} — même source que la table JSF
     * ({@link fr.siamois.ui.table.definitions.RecordingUnitTableDefinitionFactory}), pour qu'aucune
     * des deux ne puisse diverger silencieusement de l'autre.
     */
    private static List<ProjectTableColumnResource> buildRecordingUnitTableColumnDefaults() {
        List<RecordingUnitTableColumnDefaults.ColumnDefault> defaults = RecordingUnitTableColumnDefaults.columns();
        List<ProjectTableColumnResource> out = new ArrayList<>(defaults.size());
        for (int i = 0; i < defaults.size(); i++) {
            RecordingUnitTableColumnDefaults.ColumnDefault d = defaults.get(i);
            out.add(new ProjectTableColumnResource(d.columnId(), d.fieldId(), d.visible(), i));
        }
        return out;
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
            return buildFieldsMetadataOnly(fieldSource, locale, RecordingUnit.class);
        });
        type.setFields(fields);
        return type;
    }

    private FindDefaultType buildFindDefaultType(Long projectId, InstitutionDTO institution, PersonDTO personDto, String lang,
                                                 RecordingUnitIdentifierConfig identifierConfig) {
        FindDefaultType defaultType = new FindDefaultType();
        defaultType.setIdentifierConfig(identifierConfig);

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Locale locale = langService.localeForApiLang(lang);
        Map<String, FieldResource> fields = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
            FormUiDto formUiDto = effectiveFormResolver.resolveEffectiveForm(
                    Specimen.DETAILS_FORM, projectId, ConfigurableTable.MOBILIER, null);
            FieldSource fieldSource = new PanelFieldSource(formUiDto);
            defaultType.setFormBundle(new FormResource(FormUiDtoLayoutJson.serialize(formUiDto.getLayout())));
            return buildFieldsMetadataOnly(fieldSource, locale, Specimen.class);
        });
        defaultType.setFields(fields);
        return defaultType;
    }

    private FindType buildFindType(Long projectId, Concept concept,
                                   UserInfo userInfo, Locale locale,
                                   RecordingUnitIdentifierConfig identifierConfig) {
        ConceptDTO typeDto = conceptMapper.convert(concept);
        FindType type = new FindType();
        type.setConcept(toConceptResource(typeDto, locale.getLanguage()));
        type.setId(String.valueOf(concept.getId()));
        type.setIdentifierConfig(identifierConfig);

        Map<String, FieldResource> fields = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
            FormUiDto formUiDto = effectiveFormResolver.resolveEffectiveForm(
                    Specimen.DETAILS_FORM, projectId, ConfigurableTable.MOBILIER, concept.getId());
            FieldSource fieldSource = new PanelFieldSource(formUiDto);
            type.setFormBundle(new FormResource(FormUiDtoLayoutJson.serialize(formUiDto.getLayout())));
            return buildFieldsMetadataOnly(fieldSource, locale, Specimen.class);
        });
        type.setFields(fields);
        return type;
    }

    /**
     * {@code GET /api/v1/projects/{projectId}/phase-types} — pendant, pour Phase, de
     * {@link #buildProjectFindTypeSettings}, même structure (types configurés de l'institution +
     * {@code _default}).
     */
    @Transactional
    public ProjectPhaseTypeListResponse buildProjectPhaseTypeSettings(
            String projectKey, PersonDTO personDto, Set<Long> accessibleInstitutionIds, String lang) {
        AccessibleProjectForApi project = actionUnitService.findAccessibleProjectByKey(projectKey, accessibleInstitutionIds);
        ActionUnitDTO au = project.actionUnit();
        InstitutionDTO institution = au.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans organisation");
        }

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        RecordingUnitIdentifierConfig identifierConfig = buildIdentifierConfig(userInfo, au.getId(), ConfigurableTable.PHASE, null);
        PhaseDefaultType defaultType = buildPhaseDefaultType(au.getId(), institution, personDto, lang, identifierConfig);

        List<Concept> configuredTypes = OpenApiExecutionContext.callWithUserInfo(userInfo,
                () -> tableFieldConfigService.listConfiguredTypeConcepts(au.getId(), ConfigurableTable.PHASE));
        Locale locale = langService.localeForApiLang(lang);
        List<PhaseType> types = configuredTypes.stream()
                .map(concept -> buildPhaseType(au.getId(), concept, userInfo, locale,
                        buildIdentifierConfig(userInfo, au.getId(), ConfigurableTable.PHASE, concept.getId())))
                .toList();

        return new ProjectPhaseTypeListResponse(types, defaultType);
    }

    private PhaseDefaultType buildPhaseDefaultType(Long projectId, InstitutionDTO institution, PersonDTO personDto, String lang,
                                                   RecordingUnitIdentifierConfig identifierConfig) {
        PhaseDefaultType defaultType = new PhaseDefaultType();
        defaultType.setIdentifierConfig(identifierConfig);

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Locale locale = langService.localeForApiLang(lang);
        Map<String, FieldResource> fields = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
            FormUiDto formUiDto = effectiveFormResolver.resolveEffectiveForm(
                    Phase.DETAILS_FORM, projectId, ConfigurableTable.PHASE, null);
            FieldSource fieldSource = new PanelFieldSource(formUiDto);
            defaultType.setFormBundle(new FormResource(FormUiDtoLayoutJson.serialize(formUiDto.getLayout())));
            return buildFieldsMetadataOnly(fieldSource, locale, Phase.class);
        });
        defaultType.setFields(fields);
        return defaultType;
    }

    private PhaseType buildPhaseType(Long projectId, Concept concept,
                                     UserInfo userInfo, Locale locale,
                                     RecordingUnitIdentifierConfig identifierConfig) {
        ConceptDTO typeDto = conceptMapper.convert(concept);
        PhaseType type = new PhaseType();
        type.setConcept(toConceptResource(typeDto, locale.getLanguage()));
        type.setId(String.valueOf(concept.getId()));
        type.setIdentifierConfig(identifierConfig);

        Map<String, FieldResource> fields = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
            FormUiDto formUiDto = effectiveFormResolver.resolveEffectiveForm(
                    Phase.DETAILS_FORM, projectId, ConfigurableTable.PHASE, concept.getId());
            FieldSource fieldSource = new PanelFieldSource(formUiDto);
            type.setFormBundle(new FormResource(FormUiDtoLayoutJson.serialize(formUiDto.getLayout())));
            return buildFieldsMetadataOnly(fieldSource, locale, Phase.class);
        });
        type.setFields(fields);
        return type;
    }

    /**
     * {@code GET /api/v1/projects/{projectId}/container-types} — pendant, pour Container, de
     * {@link #buildProjectPhaseTypeSettings}, même structure.
     */
    @Transactional
    public ProjectContainerTypeListResponse buildProjectContainerTypeSettings(
            String projectKey, PersonDTO personDto, Set<Long> accessibleInstitutionIds, String lang) {
        AccessibleProjectForApi project = actionUnitService.findAccessibleProjectByKey(projectKey, accessibleInstitutionIds);
        ActionUnitDTO au = project.actionUnit();
        InstitutionDTO institution = au.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans organisation");
        }

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        RecordingUnitIdentifierConfig identifierConfig = buildIdentifierConfig(userInfo, au.getId(), ConfigurableTable.CONTENANT, null);
        ContainerDefaultType defaultType = buildContainerDefaultType(au.getId(), institution, personDto, lang, identifierConfig);

        List<Concept> configuredTypes = OpenApiExecutionContext.callWithUserInfo(userInfo,
                () -> tableFieldConfigService.listConfiguredTypeConcepts(au.getId(), ConfigurableTable.CONTENANT));
        Locale locale = langService.localeForApiLang(lang);
        List<ContainerType> types = configuredTypes.stream()
                .map(concept -> buildContainerType(au.getId(), concept, userInfo, locale,
                        buildIdentifierConfig(userInfo, au.getId(), ConfigurableTable.CONTENANT, concept.getId())))
                .toList();

        return new ProjectContainerTypeListResponse(types, defaultType);
    }

    private ContainerDefaultType buildContainerDefaultType(Long projectId, InstitutionDTO institution, PersonDTO personDto, String lang,
                                                            RecordingUnitIdentifierConfig identifierConfig) {
        ContainerDefaultType defaultType = new ContainerDefaultType();
        defaultType.setIdentifierConfig(identifierConfig);

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Locale locale = langService.localeForApiLang(lang);
        Map<String, FieldResource> fields = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
            FormUiDto formUiDto = effectiveFormResolver.resolveEffectiveForm(
                    Container.DETAILS_FORM, projectId, ConfigurableTable.CONTENANT, null);
            FieldSource fieldSource = new PanelFieldSource(formUiDto);
            defaultType.setFormBundle(new FormResource(FormUiDtoLayoutJson.serialize(formUiDto.getLayout())));
            return buildFieldsMetadataOnly(fieldSource, locale, Container.class);
        });
        defaultType.setFields(fields);
        return defaultType;
    }

    private ContainerType buildContainerType(Long projectId, Concept concept,
                                             UserInfo userInfo, Locale locale,
                                             RecordingUnitIdentifierConfig identifierConfig) {
        ConceptDTO typeDto = conceptMapper.convert(concept);
        ContainerType type = new ContainerType();
        type.setConcept(toConceptResource(typeDto, locale.getLanguage()));
        type.setId(String.valueOf(concept.getId()));
        type.setIdentifierConfig(identifierConfig);

        Map<String, FieldResource> fields = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
            FormUiDto formUiDto = effectiveFormResolver.resolveEffectiveForm(
                    Container.DETAILS_FORM, projectId, ConfigurableTable.CONTENANT, concept.getId());
            FieldSource fieldSource = new PanelFieldSource(formUiDto);
            type.setFormBundle(new FormResource(FormUiDtoLayoutJson.serialize(formUiDto.getLayout())));
            return buildFieldsMetadataOnly(fieldSource, locale, Container.class);
        });
        type.setFields(fields);
        return type;
    }

    /**
     * Gabarit UI du formulaire de création projet ({@link ActionUnit#NEW_UNIT_FORM}) : layout et métadonnées des champs.
     * Vocabulaires : {@code GET /api/v1/vocabularies}.
     */
    /**
     * {@code GET /api/v1/organizations/{id}/project-types} (plan §5/§6) — supersedes
     * {@link #buildProjectUiForm}/{@code GET /api/v1/projects/form}: layout, field configs and the
     * shared field catalog in one call. Phase 1 populates only {@code _default}, simulated from the
     * hardcoded {@link ActionUnit#DETAILS_FORM} — the fiche's schema, not {@link ActionUnit#NEW_UNIT_FORM}
     * (the create-dialog schema {@code buildProjectUiForm} used) — since this endpoint drives the
     * Detail panel, not project creation. {@code data} stays empty: Project isn't plugged into the real
     * {@code ConfigurableTable}/{@code FieldFormConfig} machinery this phase.
     */
    @Transactional(readOnly = true)
    public ProjectTypeListResponse buildProjectTypes(long organizationId, PersonDTO personDto, String lang) {
        InstitutionDTO institution = institutionService.findById(organizationId);
        if (institution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found");
        }

        FormUiDto systemForm = ActionUnit.DETAILS_FORM;
        FormUiDto formUiDto = conversionService.convert(systemForm, FormUiDto.class);
        FieldSource fieldSource = new PanelFieldSource(formUiDto);
        String layoutJson = FormUiDtoLayoutJson.serialize(systemForm.getLayout());
        FormResource form = new FormResource(layoutJson);

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Locale locale = langService.localeForApiLang(lang);
        Map<String, FieldResource> fields = OpenApiExecutionContext.callWithUserInfo(
                userInfo, () -> buildFieldsMetadataOnly(fieldSource, locale, ActionUnit.class));

        List<ProjectFieldConfigResource> fieldConfigs = fields.keySet().stream()
                .map(fieldId -> new ProjectFieldConfigResource(fieldId, true, true))
                .toList();

        List<ProjectTableColumnResource> tableColumns = buildTableColumnDefaults();

        ProjectDefaultType defaultType = new ProjectDefaultType(form, fieldConfigs, tableColumns);
        return new ProjectTypeListResponse(List.of(), defaultType, fields);
    }

    /**
     * Défauts de colonnes de la liste des projets, depuis {@link ActionUnitTableColumnDefaults} — même
     * source que la table JSF ({@code ActionUnitTableDefinitionFactory}), pour qu'aucune des deux ne
     * puisse diverger silencieusement de l'autre.
     */
    private static List<ProjectTableColumnResource> buildTableColumnDefaults() {
        List<ActionUnitTableColumnDefaults.ColumnDefault> defaults = ActionUnitTableColumnDefaults.columns();
        List<ProjectTableColumnResource> out = new ArrayList<>(defaults.size());
        for (int i = 0; i < defaults.size(); i++) {
            ActionUnitTableColumnDefaults.ColumnDefault d = defaults.get(i);
            out.add(new ProjectTableColumnResource(d.columnId(), d.fieldId(), d.visible(), i));
        }
        return out;
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
            fieldAnswerPatchService.applyLenient(shell, formUiDto, fieldAnswers, null);
        });
    }

    /**
     * Formulaire de création d'une UE pour un type donné, résolu pour un projet précis.
     *
     * @deprecated Retourne un seul type à la fois (un appel par type) ; préférer
     * {@link #buildProjectRecordingUnitTypeSettings} qui retourne tous les types configurés du projet
     * en un seul appel. Reste correct fonctionnellement (résolution effective par projet + type via
     * {@link fr.siamois.domain.services.form.EffectiveFormResolver}, comme le fait
     * {@link #buildProjectRecordingUnitTypeSettings}).
     */
    @Deprecated(forRemoval = true)
    @Transactional(readOnly = true)
    public RecordingUnitCreateFormData buildRecordingUnitCreateForm(String projectId,
                                                                    long recordingUnitTypeConceptId,
                                                                    PersonDTO personDto,
                                                                    Set<Long> accessibleInstitutionIds,
                                                                    String lang) {
        AccessibleProjectForApi project = actionUnitService.findAccessibleProjectByKey(projectId, accessibleInstitutionIds);
        ActionUnitDTO au = project.actionUnit();
        InstitutionDTO institution = au.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans organisation");
        }
        Concept typeConcept = conceptRepository.findById(recordingUnitTypeConceptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recording unit type not found"));

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Locale locale = langService.localeForApiLang(lang);
        RecordingUnitIdentifierConfig identifierConfig =
                buildIdentifierConfig(userInfo, au.getId(), ConfigurableTable.UE, typeConcept.getId());
        RecordingUnitType type = buildRecordingUnitType(au.getId(), typeConcept, userInfo, locale, identifierConfig);

        return new RecordingUnitCreateFormData(type.getConcept(), type.getFormBundle(), type.getFields());
    }

    /**
     * Gabarit UI pour création d'un mobilier pour un type donné, résolu pour un projet précis.
     *
     * @deprecated Retourne un seul type à la fois (un appel par type) ; préférer
     * {@link #buildProjectFindTypeSettings} qui retourne tous les types configurés du projet en un seul
     * appel. Reste correct fonctionnellement (résolution effective par projet + type via
     * {@link fr.siamois.domain.services.form.EffectiveFormResolver}, comme le fait
     * {@link #buildProjectFindTypeSettings}).
     */
    @Deprecated(forRemoval = true)
    @Transactional(readOnly = true)
    public FindCreateFormData buildFindCreateForm(String projectId,
                                                  long findTypeConceptId,
                                                  PersonDTO personDto,
                                                  Set<Long> accessibleInstitutionIds,
                                                  String lang) {
        AccessibleProjectForApi project = actionUnitService.findAccessibleProjectByKey(projectId, accessibleInstitutionIds);
        ActionUnitDTO au = project.actionUnit();
        InstitutionDTO institution = au.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans organisation");
        }
        Concept typeConcept = conceptRepository.findById(findTypeConceptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Type de mobilier introuvable"));

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Locale locale = langService.localeForApiLang(lang);
        RecordingUnitIdentifierConfig identifierConfig =
                buildIdentifierConfig(userInfo, au.getId(), ConfigurableTable.MOBILIER, typeConcept.getId());
        FindType type = buildFindType(au.getId(), typeConcept, userInfo, locale, identifierConfig);

        return new FindCreateFormData(type.getConcept(), type.getFormBundle(), type.getFields());
    }

    /**
     * Mobilier existant : champs avec leurs valeurs ({@link Specimen#DETAILS_FORM}, comme le panel web).
     */
    @Transactional(readOnly = true)
    private Long specimenProjectId(SpecimenDTO specimen) {
        if (specimen.getActionUnit() != null) return specimen.getActionUnit().getId();
        if (specimen.getRecordingUnit() == null || specimen.getRecordingUnit().getId() == null) return null;
        RecordingUnitDTO unit = recordingUnitService.findById(specimen.getRecordingUnit().getId());
        return unit != null && unit.getActionUnit() != null ? unit.getActionUnit().getId() : null;
    }

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

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Locale locale = langService.localeForApiLang(lang);

        Map<String, FieldAnswer> answers = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
            // The effective form, as the find-types catalog lays it out and the PATCH writes it:
            // without the category's additional fields their answers would never be read back.
            Long projectId = specimenProjectId(specimen);
            FormUiDto formUiDto = projectId != null
                    ? effectiveFormResolver.resolveEffectiveForm(Specimen.DETAILS_FORM, projectId, ConfigurableTable.MOBILIER,
                            specimen.getCategory() != null ? specimen.getCategory().getId() : null)
                    : conversionService.convert(Specimen.DETAILS_FORM, FormUiDto.class);
            return buildSpecimenFieldsWithFallback(specimen, new PanelFieldSource(formUiDto), locale);
        });

        // FindResource.answers is Map<String, Object> (shared with the list's raw-value shape,
        // see its own javadoc) — the detail's FieldAnswer envelope values just widen here.
        resource.setAnswers(new LinkedHashMap<>(answers));
        // Same source of truth as SpecimenPanel.canUserEditUnit (JSF), not the RU write check —
        // see the migration plan's "the JSF bean is the source of truth for permissions" rule.
        boolean canEdit = profilePermissionService.hasSpecimenWritePermission(userInfo, specimen);
        boolean canValidate = profilePermissionService.hasValidatePermission(userInfo,
                specimen.getActionUnit() != null ? specimen.getActionUnit().getId() : null);
        resource.setPermissions(fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions.of(canEdit)
                .withValidate(canValidate));
        if (specimen.getId() != null) {
            resource.setResourceUri("/specimen/" + specimen.getId());
        }
        resourceBookmarkService.markBookmarked(userInfo, resource);
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
                    fieldAnswerWireService.toTypedAnswer(FieldAnswerWireService.answerTypeOf(field), fieldResource,
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
                    fieldAnswerWireService.toTypedAnswer(FieldAnswerWireService.answerTypeOf(field), fieldResource, null, lang));
        }
        return out;
    }

    /**
     * A form's field catalog, each entry saying how a list of {@code entityType} can sort and filter
     * its column ({@link FieldQueryService#capabilityOf}).
     */
    private Map<String, FieldResource> buildFieldsMetadataOnly(FieldSource fieldSource, Locale locale, Class<?> entityType) {
        Map<String, FieldResource> fields = new LinkedHashMap<>();
        for (CustomField field : fieldSource.getAllFields()) {
            if (field == null || field.getId() == null) continue;
            fields.put(String.valueOf(field.getId()),
                    fieldQueryService.withQuery(toFieldResource(field, locale), entityType, field));
        }
        return fields;
    }

    private FieldResource toFieldResource(CustomField field, Locale locale) {
        return FieldAnswerWireService.fieldResourceOf(field,
                langService.resolveMessage(field.getLabel(), locale),
                langService.resolveMessage(field.getHint(), locale));
    }

    private ResolvedConceptResource toConceptResource(ConceptDTO concept, String lang) {
        ResolvedConceptResource r = new ResolvedConceptResource();
        r.setResourceType(CONCEPTS);
        r.setId(String.valueOf(concept.getId()));
        r.setExternalUrl(concept.getExternalId());
        r.setResolvedLabel(labelService.findLabelOf(concept, lang).getLabel());
        return r;
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
        // Same instance / organisation / project rule as editing a recording unit (and as
        // GET /projects?canCreate=recordingUnit).
        if (!profilePermissionService.hasProjectPermission(userInfo, au.getId(),
                PermissionConstants.INSTANCE_EDIT_RECORDING_UNITS,
                PermissionConstants.ORGANIZATION_EDIT_RECORDING_UNITS,
                PermissionConstants.PROJECT_EDIT_RECORDING_UNITS)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Création d'unité non autorisée sur ce projet");
        }
        Concept typeConcept = conceptRepository.findById(Long.parseLong(request.getTypeId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Type d'UE introuvable"));
        ConceptDTO typeDto = conceptMapper.convert(typeConcept);
        // Checked up front so an out-of-scope link is a 404 before anything is written.
        if (request.getParentRecordingUnitId() != null) {
            recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(request.getParentRecordingUnitId(), accessibleInstitutionIds);
        }
        if (request.getChildRecordingUnitId() != null) {
            recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(request.getChildRecordingUnitId(), accessibleInstitutionIds);
        }

        RecordingUnitDTO shell = initRecordingUnitShell(typeDto, au, institution, personDto);
        shell.setGeom(request.getGeom());

        RecordingUnitDTO created = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
            FormUiDto formUiDto = effectiveFormResolver.resolveEffectiveForm(
                    RecordingUnit.DETAILS_FORM, au.getId(), ConfigurableTable.UE, typeDto.getId());
            Map<CustomField, CustomFieldAnswerViewModel> additionalAnswers =
                    fieldAnswerPatchService.applyLenient(shell, formUiDto, request.getFieldAnswers(), au.getId());
            RecordingUnitDTO saved = saveWithGeneratedIdentifier(shell);
            // Additional answers hang off the unit's id, so only once it exists.
            customFieldAnswerService.saveAdditionalFieldAnswers(saved, additionalAnswers);
            return saved;
        });

        // JSF's "nouvel enfant / nouveau parent" row actions: linked in the same transaction, so a
        // rejected link (other project, cycle) rolls the creation back too.
        if (request.getParentRecordingUnitId() != null) {
            long parentId = request.getParentRecordingUnitId();
            mutateHierarchy(() -> recordingUnitService.addHierarchyChild(parentId, created.getId()));
        }
        if (request.getChildRecordingUnitId() != null) {
            long childId = request.getChildRecordingUnitId();
            mutateHierarchy(() -> recordingUnitService.addHierarchyChild(created.getId(), childId));
        }

        String key = created.getId() != null ? String.valueOf(created.getId()) : created.getFullIdentifier();
        return resolveMobileDetail(key, personDto, accessibleInstitutionIds, null, lang);
    }

    /**
     * Duplique une UE ({@code POST /api/v1/recording-units/{id}/duplicate}) — même copie que le
     * bouton JSF ({@code RecordingUnitPanel.duplicate()}) : les champs de base via le constructeur
     * de copie de {@link RecordingUnitDTO}, sans parents, auteur/créateur = l'appelant, identifiant
     * régénéré. Même droit que l'édition de l'UE source.
     */
    @Transactional
    public RecordingUnitResource duplicateRecordingUnit(String recordingUnitKey,
                                                        PersonDTO personDto,
                                                        Set<Long> accessibleInstitutionIds,
                                                        String lang) {
        RecordingUnitDTO source = recordingUnitService
                .findAccessibleRecordingUnitWithEntity(recordingUnitKey, accessibleInstitutionIds, null)
                .dto();
        InstitutionDTO institution = source.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UE sans organisation");
        }
        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        if (!profilePermissionService.hasRecordingUnitWritePermission(userInfo, source)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Duplication non autorisée");
        }

        RecordingUnitDTO copy = new RecordingUnitDTO(source);
        copy.setParents(new HashSet<>());
        copy.setAuthor(personDto);
        copy.setCreatedBy(personDto);
        RecordingUnitDTO created = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> saveWithGeneratedIdentifier(copy));

        return resolveMobileDetail(String.valueOf(created.getId()), personDto, accessibleInstitutionIds, null, lang);
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
        Long projectId = dto.getActionUnit() != null ? dto.getActionUnit().getId() : null;
        boolean canEdit = profilePermissionService.hasRecordingUnitWritePermission(userInfo, dto);
        Map<String, Object> answers = request.getFieldAnswers() != null ? request.getFieldAnswers() : Map.of();
        boolean contentChange = !answers.isEmpty() || request.isGeomPresent();
        boolean statusChange = ValidationOpenApiService.changes(dto.getValidated(), request.getValidated());
        // A validator may change the status alone without the edit right; anything else needs it.
        if ((contentChange || !statusChange) && !canEdit) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification non autorisée");
        }
        validationOpenApiService.requireAllowed(dto.getValidated(), request.getValidated(), canEdit,
                profilePermissionService.hasValidatePermission(userInfo, projectId));

        if (!contentChange) {
            // Status only (or nothing): syncRevision is the entity's @Version, so this bumps it too.
            validationOpenApiService.apply(RecordingUnit.class, dto.getId(), request.getValidated(), personDto);
            return resolveMobileDetail(recordingUnitKey, personDto, accessibleInstitutionIds, null, lang);
        }

        OpenApiExecutionContext.runWithUserInfo(userInfo, () -> {
            if (request.isGeomPresent()) {
                dto.setGeom(request.getGeom());
            }
            Map<CustomField, CustomFieldAnswerViewModel> additionalAnswers = Map.of();
            if (!answers.isEmpty()) {
                FormUiDto formUiDto = effectiveFormResolver.resolveEffectiveForm(
                        RecordingUnit.DETAILS_FORM, projectId, ConfigurableTable.UE,
                        dto.getType() != null ? dto.getType().getId() : null);
                // Lenient: the mobile client's legacy payloads may carry fields this form lacks.
                additionalAnswers = fieldAnswerPatchService.applyLenient(dto, formUiDto, answers, projectId);
            }

            try {
                recordingUnitService.save(dto, additionalAnswers);
            } catch (FailedRecordingUnitSaveException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
            }
        });
        // After the save: it writes the DTO's (old) status back onto the entity.
        validationOpenApiService.apply(RecordingUnit.class, dto.getId(), request.getValidated(), personDto);

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

    /**
     * Previous/next recording unit in the same project ({@code GET /api/v1/recording-units/{id}/siblings}),
     * after the same access check as the detail.
     */
    @Transactional(readOnly = true)
    public SiblingsResource findSiblings(String recordingUnitKey, PersonDTO personDto, Set<Long> accessibleInstitutionIds) {
        RecordingUnitDTO dto = recordingUnitService
                .findAccessibleRecordingUnitWithEntity(recordingUnitKey, accessibleInstitutionIds, null)
                .dto();
        if (!profilePermissionService.canViewRecordingUnit(personDto, dto)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unité introuvable ou non accessible");
        }
        Long projectId = dto.getActionUnit() != null ? dto.getActionUnit().getId() : null;
        return entitySiblingsService.findSiblings(EntitySiblingsService.Kind.RECORDING_UNIT, projectId, dto.getId());
    }
}
