package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.study.Study;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.FieldVocabularyConfig;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.PhaseService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.document.DocumentLinkKind;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.domain.services.form.EffectiveFormResolver;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.form.OnTheFlyFieldService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
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
import fr.siamois.ui.api.openapi.v1.exception.IdentifierConflictException;
import fr.siamois.ui.api.openapi.v1.exception.SyncRevisionConflictException;
import fr.siamois.ui.api.openapi.v1.mapper.FindOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.mapper.VocabularyResourceMapper;
import fr.siamois.ui.api.openapi.v1.request.list.ValuesLimit;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitPatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.find.FindCreateFormData;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldAnswer;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import fr.siamois.ui.api.openapi.v1.resource.form.FormResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectFieldConfigResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectTableColumnResource;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitCreateFormData;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.resource.sibling.SiblingsResource;
import fr.siamois.ui.api.openapi.v1.resource.type.*;
import fr.siamois.ui.api.openapi.v1.response.project.type.*;
import fr.siamois.ui.api.openapi.v1.response.sync.SyncConflictData;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.form.dto.FormUiDtoLayoutJson;
import fr.siamois.ui.form.fieldsource.FieldSource;
import fr.siamois.ui.form.fieldsource.PanelFieldSource;
import fr.siamois.ui.table.definitions.ActionUnitTableColumnDefaults;
import fr.siamois.ui.table.definitions.RecordingUnitTableColumnDefaults;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import fr.siamois.domain.services.vocabulary.FieldTextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
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
    private final OnTheFlyFieldService onTheFlyFieldService;
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
    private final FieldTextService fieldTextService;
    private final ActionUnitService actionUnitService;
    private final ProfilePermissionService profilePermissionService;
    private final FindOpenApiMapper findOpenApiMapper;
    private final LabelService labelService;
    private final TableFieldConfigService tableFieldConfigService;
    private final ResourceBookmarkService resourceBookmarkService;
    private final EntitySiblingsService entitySiblingsService;
    private final ValidationOpenApiService validationOpenApiService;
    private final MultiValueAnswers multiValueAnswers;
    private final DocumentService documentService;
    private final PhaseService phaseService;

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
        if (counts != null && counts.contains("documents") && resource.getCount() != null && dto.getId() != null) {
            resource.getCount().setDocuments(documentService.countLinkedTo(DocumentLinkKind.RECORDING_UNIT, dto.getId()));
        }
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
                .withValidate(profilePermissionService.hasValidatePermission(userInfo, projectId))
                .withManageSettings(projectId != null && profilePermissionService.hasProjectSettingsPermission(userInfo, projectId))
                .withDelete(profilePermissionService.hasDeleteEntitiesPermission(userInfo, projectId)));
        resourceBookmarkService.markBookmarked(userInfo, resource);
        Locale locale = langService.localeForApiLang(lang);
        Map<String, FieldAnswer> fields = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
            FormUiDto formUiDto = onTheFlyFieldService.withOnTheFlyFields(
                    effectiveFormResolver.resolveEffectiveForm(projectId, ConfigurableTable.UE,
                            dto.getType() != null ? dto.getType().getId() : null),
                    ConfigurableTable.UE, dto.getId());
            FieldSource fieldSource = new PanelFieldSource(formUiDto);
            return buildFieldsWithFallback(dto, fieldSource, locale);
        });

        // RecordingUnitResource.answers is Map<String, Object> (shared with the list's raw-value
        // shape) — wrap rather than assign directly, since Map<String, FieldAnswer> isn't a
        // Map<String, Object> under Java's invariant generics even though every value already is one.
        // Its relation fields (parents, children, finds, stratigraphy) are read in there, a preview each.
        resource.setAnswers(multiValueAnswers.shapeOne(RecordingUnit.class, dto.getId(), new LinkedHashMap<>(fields),
                fields.keySet(), ValuesLimit.forDetail(), lang));
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
        Map<String, FieldResource> baseFields = baseFieldsOf(au.getId(), ConfigurableTable.UE, RecordingUnit.class,
                institution, personDto, lang);

        List<Concept> configuredTypes = OpenApiExecutionContext.callWithUserInfo(userInfo,
                () -> tableFieldConfigService.listConfiguredTypeConcepts(au.getId(), ConfigurableTable.UE));
        Locale locale = langService.localeForApiLang(lang);
        List<RecordingUnitType> types = configuredTypes.stream()
                .map(concept -> buildRecordingUnitType(au.getId(), concept, userInfo, locale,
                        buildIdentifierConfig(userInfo, au.getId(), ConfigurableTable.UE, concept.getId())))
                .toList();

        // The table's own fields first, so a per-type override of a system field's label/hint (an
        // effective-form customization scoped to that one type) wins over it.
        Map<String, FieldResource> unionFields = new LinkedHashMap<>(baseFields);
        for (RecordingUnitType type : types) {
            putCatalogFields(unionFields, type.getFields());
        }

        return new ProjectRecordingUnitTypeListResponse(types, unionFields, buildRecordingUnitTableColumnDefaults());
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
        Map<String, FieldResource> baseFields = baseFieldsOf(au.getId(), ConfigurableTable.MOBILIER, Specimen.class,
                institution, personDto, lang);

        List<Concept> configuredTypes = OpenApiExecutionContext.callWithUserInfo(userInfo,
                () -> tableFieldConfigService.listConfiguredTypeConcepts(au.getId(), ConfigurableTable.MOBILIER));
        Locale locale = langService.localeForApiLang(lang);
        List<FindType> types = configuredTypes.stream()
                .map(concept -> buildFindType(au.getId(), concept, userInfo, locale,
                        buildIdentifierConfig(userInfo, au.getId(), ConfigurableTable.MOBILIER, concept.getId())))
                .toList();

        Map<String, FieldResource> unionFields = new LinkedHashMap<>(baseFields);
        types.forEach(type -> putCatalogFields(unionFields, type.getFields()));
        return new ProjectFindTypeListResponse(types, unionFields);
    }

    /**
     * The fields of a table's form when the entity has no type — its system fields — which is the base the
     * types' own fields are added to in a catalog of the project's fields.
     */
    private Map<String, FieldResource> baseFieldsOf(Long projectId, ConfigurableTable table, Class<?> entityClass,
                                                    InstitutionDTO institution, PersonDTO personDto, String lang) {
        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Locale locale = langService.localeForApiLang(lang);
        return OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
            FormUiDto formUiDto = effectiveFormResolver.resolveEffectiveForm(projectId, table, null);
            return buildFieldsMetadataOnly(new PanelFieldSource(formUiDto), locale, entityClass);
        });
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

    /**
     * Défauts de colonnes de la liste des unités d'enregistrement, depuis
     * {@link RecordingUnitTableColumnDefaults}.
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
            FormUiDto formUiDto = effectiveFormResolver.resolveEffectiveForm(projectId, ConfigurableTable.UE, concept.getId());
            FieldSource fieldSource = new PanelFieldSource(formUiDto);
            type.setFormBundle(new FormResource(FormUiDtoLayoutJson.serialize(formUiDto.getLayout())));
            return buildFieldsMetadataOnly(fieldSource, locale, RecordingUnit.class, projectId, concept.getId());
        });
        type.setFields(fields);
        return type;
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
            FormUiDto formUiDto = effectiveFormResolver.resolveEffectiveForm(projectId, ConfigurableTable.MOBILIER, concept.getId());
            FieldSource fieldSource = new PanelFieldSource(formUiDto);
            type.setFormBundle(new FormResource(FormUiDtoLayoutJson.serialize(formUiDto.getLayout())));
            return buildFieldsMetadataOnly(fieldSource, locale, Specimen.class, projectId, concept.getId());
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
        Map<String, FieldResource> baseFields = baseFieldsOf(au.getId(), ConfigurableTable.PHASE, Phase.class,
                institution, personDto, lang);

        List<Concept> configuredTypes = OpenApiExecutionContext.callWithUserInfo(userInfo,
                () -> tableFieldConfigService.listConfiguredTypeConcepts(au.getId(), ConfigurableTable.PHASE));
        Locale locale = langService.localeForApiLang(lang);
        List<PhaseType> types = configuredTypes.stream()
                .map(concept -> buildPhaseType(au.getId(), concept, userInfo, locale,
                        buildIdentifierConfig(userInfo, au.getId(), ConfigurableTable.PHASE, concept.getId())))
                .toList();

        Map<String, FieldResource> unionFields = new LinkedHashMap<>(baseFields);
        types.forEach(type -> putCatalogFields(unionFields, type.getFields()));
        return new ProjectPhaseTypeListResponse(types, unionFields);
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
            FormUiDto formUiDto = effectiveFormResolver.resolveEffectiveForm(projectId, ConfigurableTable.PHASE, concept.getId());
            FieldSource fieldSource = new PanelFieldSource(formUiDto);
            type.setFormBundle(new FormResource(FormUiDtoLayoutJson.serialize(formUiDto.getLayout())));
            return buildFieldsMetadataOnly(fieldSource, locale, Phase.class, projectId, concept.getId());
        });
        type.setFields(fields);
        return type;
    }

    /**
     * {@code GET /api/v1/projects/{projectId}/document-types} — pendant, pour Document (catégories), de
     * {@link #buildProjectFindTypeSettings}, même structure (types configurés de l'institution +
     * {@code _default}).
     */
    @Transactional
    public ProjectDocumentTypeListResponse buildProjectDocumentTypeSettings(
            String projectKey, PersonDTO personDto, Set<Long> accessibleInstitutionIds, String lang) {
        AccessibleProjectForApi project = actionUnitService.findAccessibleProjectByKey(projectKey, accessibleInstitutionIds);
        ActionUnitDTO au = project.actionUnit();
        InstitutionDTO institution = au.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans organisation");
        }

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        // Nothing can be configured on the table until the institution's thesaurus declares its type field:
        // the project then has no type, no stored identifier format, and the seed layout as its form.
        boolean configurable = Boolean.TRUE.equals(OpenApiExecutionContext.callWithUserInfo(userInfo,
                () -> tableFieldConfigService.isTypeFieldConfigured(au.getId(), ConfigurableTable.DOCUMENT)));
        Map<String, FieldResource> baseFields = baseFieldsOf(configurable ? au.getId() : null, ConfigurableTable.DOCUMENT,
                Document.class, institution, personDto, lang);

        List<Concept> configuredTypes = configurable ? OpenApiExecutionContext.callWithUserInfo(userInfo,
                () -> tableFieldConfigService.listConfiguredTypeConcepts(au.getId(), ConfigurableTable.DOCUMENT)) : List.<Concept>of();
        Locale locale = langService.localeForApiLang(lang);
        List<DocumentType> types = configuredTypes.stream()
                .map(concept -> buildDocumentType(au.getId(), concept, userInfo, locale,
                        buildIdentifierConfig(userInfo, au.getId(), ConfigurableTable.DOCUMENT, concept.getId())))
                .toList();

        Map<String, FieldResource> unionFields = new LinkedHashMap<>(baseFields);
        types.forEach(type -> putCatalogFields(unionFields, type.getFields()));
        return new ProjectDocumentTypeListResponse(types, unionFields);
    }

    private DocumentType buildDocumentType(Long projectId, Concept concept,
                                     UserInfo userInfo, Locale locale,
                                     RecordingUnitIdentifierConfig identifierConfig) {
        ConceptDTO typeDto = conceptMapper.convert(concept);
        DocumentType type = new DocumentType();
        type.setConcept(toConceptResource(typeDto, locale.getLanguage()));
        type.setId(String.valueOf(concept.getId()));
        type.setIdentifierConfig(identifierConfig);

        Map<String, FieldResource> fields = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
            FormUiDto formUiDto = effectiveFormResolver.resolveEffectiveForm(projectId, ConfigurableTable.DOCUMENT, concept.getId());
            FieldSource fieldSource = new PanelFieldSource(formUiDto);
            type.setFormBundle(new FormResource(FormUiDtoLayoutJson.serialize(formUiDto.getLayout())));
            return buildFieldsMetadataOnly(fieldSource, locale, Document.class, projectId, concept.getId());
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
        Map<String, FieldResource> baseFields = baseFieldsOf(au.getId(), ConfigurableTable.CONTENANT, Container.class,
                institution, personDto, lang);

        List<Concept> configuredTypes = OpenApiExecutionContext.callWithUserInfo(userInfo,
                () -> tableFieldConfigService.listConfiguredTypeConcepts(au.getId(), ConfigurableTable.CONTENANT));
        Locale locale = langService.localeForApiLang(lang);
        List<ContainerType> types = configuredTypes.stream()
                .map(concept -> buildContainerType(au.getId(), concept, userInfo, locale,
                        buildIdentifierConfig(userInfo, au.getId(), ConfigurableTable.CONTENANT, concept.getId())))
                .toList();

        Map<String, FieldResource> unionFields = new LinkedHashMap<>(baseFields);
        types.forEach(type -> putCatalogFields(unionFields, type.getFields()));
        return new ProjectContainerTypeListResponse(types, unionFields);
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
            FormUiDto formUiDto = effectiveFormResolver.resolveEffectiveForm(projectId, ConfigurableTable.CONTENANT, concept.getId());
            FieldSource fieldSource = new PanelFieldSource(formUiDto);
            type.setFormBundle(new FormResource(FormUiDtoLayoutJson.serialize(formUiDto.getLayout())));
            return buildFieldsMetadataOnly(fieldSource, locale, Container.class, projectId, concept.getId());
        });
        type.setFields(fields);
        return type;
    }

    /**
     * {@code GET /api/v1/projects/{projectId}/study-types} — pendant, pour Study, de
     * {@link #buildProjectPhaseTypeSettings}, même structure.
     */
    @Transactional
    public ProjectStudyTypeListResponse buildProjectStudyTypeSettings(
            String projectKey, PersonDTO personDto, Set<Long> accessibleInstitutionIds, String lang) {
        AccessibleProjectForApi project = actionUnitService.findAccessibleProjectByKey(projectKey, accessibleInstitutionIds);
        ActionUnitDTO au = project.actionUnit();
        InstitutionDTO institution = au.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans organisation");
        }

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Map<String, FieldResource> baseFields = baseFieldsOf(au.getId(), ConfigurableTable.STUDY, Study.class,
                institution, personDto, lang);

        List<Concept> configuredTypes = OpenApiExecutionContext.callWithUserInfo(userInfo,
                () -> tableFieldConfigService.listConfiguredTypeConcepts(au.getId(), ConfigurableTable.STUDY));
        Locale locale = langService.localeForApiLang(lang);
        List<StudyType> types = configuredTypes.stream()
                .map(concept -> buildStudyType(au.getId(), concept, userInfo, locale,
                        buildIdentifierConfig(userInfo, au.getId(), ConfigurableTable.STUDY, concept.getId())))
                .toList();

        Map<String, FieldResource> unionFields = new LinkedHashMap<>(baseFields);
        types.forEach(type -> putCatalogFields(unionFields, type.getFields()));
        return new ProjectStudyTypeListResponse(types, unionFields);
    }

    private StudyType buildStudyType(Long projectId, Concept concept,
                                             UserInfo userInfo, Locale locale,
                                             RecordingUnitIdentifierConfig identifierConfig) {
        ConceptDTO typeDto = conceptMapper.convert(concept);
        StudyType type = new StudyType();
        type.setConcept(toConceptResource(typeDto, locale.getLanguage()));
        type.setId(String.valueOf(concept.getId()));
        type.setIdentifierConfig(identifierConfig);

        Map<String, FieldResource> fields = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
            FormUiDto formUiDto = effectiveFormResolver.resolveEffectiveForm(projectId, ConfigurableTable.STUDY, concept.getId());
            FieldSource fieldSource = new PanelFieldSource(formUiDto);
            type.setFormBundle(new FormResource(FormUiDtoLayoutJson.serialize(formUiDto.getLayout())));
            return buildFieldsMetadataOnly(fieldSource, locale, Study.class, projectId, concept.getId());
        });
        type.setFields(fields);
        return type;
    }

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

        return new ProjectTypeListResponse(List.of(), form, fieldConfigs, tableColumns, fields);
    }

    /**
     * Défauts de colonnes de la liste des projets, depuis {@link ActionUnitTableColumnDefaults}.
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
     * Formulaire de création d'une UE pour un type donné, résolu pour un projet précis (résolution effective
     * par projet + type via {@link fr.siamois.domain.services.form.EffectiveFormResolver}). Pour tous les types
     * configurés du projet en un seul appel : {@link #buildProjectRecordingUnitTypeSettings}.
     */
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
     * <p>
     * Préférez {@link #buildProjectFindTypeSettings} qui retourne tous les types configurés du projet en un seul
     * appel. Reste correct fonctionnellement (résolution effective par projet + type via
     * {@link fr.siamois.domain.services.form.EffectiveFormResolver}, comme le fait
     * {@link #buildProjectFindTypeSettings}).
     */
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
        if (specimen.getId() != null) {
            resource.setCount(new fr.siamois.ui.api.openapi.v1.resource.document.EntityDocumentCounts(
                    documentService.countLinkedTo(DocumentLinkKind.FIND, specimen.getId())));
        }

        InstitutionDTO institution = specimen.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            resource.setAnswers(Map.of());
            return resource;
        }

        UserInfo userInfo = new UserInfo(institution, personDto, lang);
        Locale locale = langService.localeForApiLang(lang);

        Map<String, FieldAnswer> answers = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
            // The effective form, as the find-types catalog lays it out and the PATCH writes it:
            // without the type's additional fields their answers would never be read back.
            Long projectId = specimenProjectId(specimen);
            FormUiDto formUiDto = onTheFlyFieldService.withOnTheFlyFields(
                    effectiveFormResolver.resolveEffectiveForm(projectId, ConfigurableTable.MOBILIER,
                            specimen.getType() != null ? specimen.getType().getId() : null),
                    ConfigurableTable.MOBILIER, specimen.getId());
            return buildSpecimenFieldsWithFallback(specimen, new PanelFieldSource(formUiDto), locale);
        });

        // FindResource.answers is Map<String, Object> (shared with the list's raw-value shape,
        // see its own javadoc) — the detail's FieldAnswer envelope values just widen here.
        resource.setAnswers(multiValueAnswers.shapeOne(Specimen.class, specimen.getId(), new LinkedHashMap<>(answers),
                answers.keySet(), ValuesLimit.forDetail(), lang));
        // Same source of truth as SpecimenPanel.canUserEditUnit (JSF), not the RU write check —
        // see the migration plan's "the JSF bean is the source of truth for permissions" rule.
        boolean canEdit = profilePermissionService.hasSpecimenWritePermission(userInfo, specimen);
        boolean canValidate = profilePermissionService.hasValidatePermission(userInfo,
                specimen.getActionUnit() != null ? specimen.getActionUnit().getId() : null);
        resource.setPermissions(fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions.of(canEdit)
                .withValidate(canValidate)
                .withManageSettings(specimen.getActionUnit() != null
                        && profilePermissionService.hasProjectSettingsPermission(userInfo, specimen.getActionUnit().getId()))
                .withDelete(profilePermissionService.hasDeleteEntitiesPermission(userInfo,
                        specimen.getActionUnit() != null ? specimen.getActionUnit().getId() : null)));
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

    /**
     * The fields of one type's form, each vocabulary field saying where its list comes from for that
     * project and type ({@link FieldConfigurationService#describeVocabulary}).
     */
    private Map<String, FieldResource> buildFieldsMetadataOnly(FieldSource fieldSource, Locale locale, Class<?> entityType,
                                                               Long projectId, Long typeConceptId) {
        Map<String, FieldResource> fields = buildFieldsMetadataOnly(fieldSource, locale, entityType);
        List<CustomFieldConcept> conceptFields = new ArrayList<>();
        for (CustomField field : fieldSource.getAllFields()) {
            if (field == null || field.getId() == null) continue;
            if (Hibernate.unproxy(field) instanceof CustomFieldConcept conceptField) {
                conceptFields.add(conceptField);
            }
        }
        Map<Long, FieldVocabularyConfig> vocabularies =
                fieldConfigurationService.describeVocabularies(conceptFields, projectId, typeConceptId);
        for (CustomFieldConcept conceptField : conceptFields) {
            FieldVocabularyConfig vocabulary = vocabularies.get(conceptField.getId());
            fields.computeIfPresent(String.valueOf(conceptField.getId()),
                    (id, resource) -> resource.withVocabulary(VocabularyResourceMapper.toResource(vocabulary)));
        }
        return fields;
    }

    /**
     * Adds one type's fields to the project's flat catalog. The catalog is the union of the types' fields,
     * the last type winning on a shared field: a vocabulary can differ from one type to the next, so it is
     * left out here and only read from each type's own {@code fields}.
     */
    private static void putCatalogFields(Map<String, FieldResource> catalog, Map<String, FieldResource> typeFields) {
        typeFields.forEach((id, resource) -> catalog.put(id, resource.withVocabulary(null)));
    }

    private FieldResource toFieldResource(CustomField field, Locale locale) {
        FieldTextService.FieldTexts texts = fieldTextService.resolve(field, locale.getLanguage(),
                langService.resolveMessage(field.getLabel(), locale),
                langService.resolveMessage(field.getHint(), locale));
        return FieldAnswerWireService.fieldResourceOf(field, texts.label(), texts.definition());
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
        RecordingUnitSummaryDTO parent = request.getParentRecordingUnitId() == null ? null
                : requireParentInProject(request.getParentRecordingUnitId(), au.getId(), accessibleInstitutionIds);
        if (request.getChildRecordingUnitId() != null) {
            recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(request.getChildRecordingUnitId(), accessibleInstitutionIds);
        }

        RecordingUnitDTO shell = initRecordingUnitShell(typeDto, au, institution, personDto);
        shell.setGeom(request.getGeom());
        // The parent is linked by the first save, before the identifier is generated: NUM_PARENT /
        // ID_PARENT and the PARENT_RU counter read it, as for the suggestion.
        attachParent(shell, parent);
        if (request.getPhaseId() != null) {
            shell.setPhases(new HashSet<>(Set.of(requirePhaseInProject(request.getPhaseId(), au.getId()))));
        }

        RecordingUnitDTO created = OpenApiExecutionContext.callWithUserInfo(userInfo, () -> {
            FormUiDto formUiDto = effectiveFormResolver.resolveEffectiveForm(au.getId(), ConfigurableTable.UE, typeDto.getId());
            Map<CustomField, CustomFieldAnswerViewModel> additionalAnswers =
                    fieldAnswerPatchService.applyOnCreate(shell, formUiDto, request.getFieldAnswers(), au.getId(), typeDto.getId());
            RecordingUnitDTO saved = saveWithGeneratedIdentifier(shell, request.getIdentifier());
            // Additional answers hang off the unit's id, so only once it exists.
            customFieldAnswerService.saveAdditionalFieldAnswers(saved, additionalAnswers);
            return saved;
        });

        // JSF's "nouveau parent" row action: linked in the same transaction, so a rejected link (other
        // project, cycle) rolls the creation back too. Its parent, if any, is already linked by the save.
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

    private PhaseDTO requirePhaseInProject(long phaseId, Long projectId) {
        PhaseDTO phase = phaseService.findById(phaseId);
        if (phase == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Phase introuvable : " + phaseId);
        }
        if (phase.getActionUnit() == null || !Objects.equals(phase.getActionUnit().getId(), projectId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phase hors projet : " + phaseId);
        }
        return phase;
    }

    private RecordingUnitDTO initRecordingUnitShell(ConceptDTO typeDto, ActionUnitDTO au,
                                                     InstitutionDTO institution, PersonDTO personDto) {
        RecordingUnitDTO shell = new RecordingUnitDTO();
        shell.setType(typeDto);
        shell.setCreatedByInstitution(institution);
        shell.setActionUnit(ProjectTypedCreationService.summaryOf(au));
        shell.setAuthor(personDto);
        shell.setCreatedBy(personDto);
        shell.setContributors(new ArrayList<>(List.of(personDto)));
        shell.setOpeningDate(OffsetDateTime.now(ZoneOffset.UTC));
        shell.setValidationStatus(ValidationStatus.INCOMPLETE);
        shell.setParents(new HashSet<>());
        shell.setChildren(new HashSet<>());
        // Left empty: the first save gives it a random temporary code, unique in the project, until the real
        // identifier is set — the project's own identifier would collide between concurrent creations.
        shell.setFullIdentifier(null);
        shell.setIdentifier("0");
        return shell;
    }

    /** 404 when the parent is out of the caller's scope, 400 when it is in another project. */
    private RecordingUnitSummaryDTO requireParentInProject(long parentId, Long projectId,
                                                           Set<Long> accessibleInstitutionIds) {
        RecordingUnitDTO parent = recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(
                parentId, accessibleInstitutionIds);
        if (parent.getActionUnit() == null || !Objects.equals(parent.getActionUnit().getId(), projectId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UE parente hors projet : " + parentId);
        }
        return new RecordingUnitSummaryDTO(parent);
    }

    private static void attachParent(RecordingUnitDTO unit, RecordingUnitSummaryDTO parent) {
        if (parent != null) {
            unit.setParents(new HashSet<>(Set.of(parent)));
        }
    }

    /**
     * The identifier {@link #createRecordingUnit} would generate for {@code unit} (the creation form's
     * suggestion, {@code GET /api/v1/identifier-suggestions}): the unit first gets the same parent as
     * at creation, so that every token of the format — not only the counter — renders as it will. The parent
     * is only set on the unsaved {@code unit}: nothing is linked nor written, the creation form may still
     * be cancelled.
     *
     * @param parentRecordingUnitId the unit the new one will be created under (its "nouvel enfant" row action), or null
     */
    @Transactional(readOnly = true)
    public String suggestFullIdentifier(RecordingUnitDTO unit, Long parentRecordingUnitId,
                                        Set<Long> accessibleInstitutionIds) {
        if (parentRecordingUnitId != null) {
            attachParent(unit, requireParentInProject(parentRecordingUnitId, unit.getActionUnit().getId(),
                    accessibleInstitutionIds));
        }
        return recordingUnitService.suggestFullIdentifier(unit.getActionUnit(), unit);
    }

    private RecordingUnitDTO saveWithGeneratedIdentifier(RecordingUnitDTO dto) {
        return saveWithGeneratedIdentifier(dto, null);
    }

    /**
     * Saves the unit, then sets its identifier: the caller's {@code customIdentifier} when one was
     * given (uniqueness checked), otherwise the generated one. The two-step save is unchanged — the
     * identifier hangs off the unit's id, so only once it exists.
     */
    private RecordingUnitDTO saveWithGeneratedIdentifier(RecordingUnitDTO dto, String customIdentifier) {
        RecordingUnitDTO saved;
        try {
            saved = recordingUnitService.save(dto);
        } catch (FailedRecordingUnitSaveException e) {
            throw IdentifierPatch.saveFailed(e);
        }
        if (!IdentifierPatch.applyChosen(customIdentifier, saved::setFullIdentifier,
                () -> recordingUnitService.fullIdentifierAlreadyExistInAction(saved))) {
            String generated = recordingUnitService.generateFullIdentifier(saved.getActionUnit(), saved);
            saved.setFullIdentifier(generated);
            if (recordingUnitService.fullIdentifierAlreadyExistInAction(saved)) {
                throw new IdentifierConflictException(generated, null);
            }
        }
        try {
            return recordingUnitService.save(saved);
        } catch (FailedRecordingUnitSaveException e) {
            throw IdentifierPatch.saveFailed(e);
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

        assertSyncRevisionIfRequested(
                recordingUnitKey,
                request.getExpectedRevision(),
                bundle.entity().getSyncRevision(),
                personDto,
                accessibleInstitutionIds,
                lang);

        UserInfo userInfo = new UserInfo(requireOrganization(dto), personDto, lang);
        Long projectId = dto.getActionUnit() != null ? dto.getActionUnit().getId() : null;
        Map<String, Object> answers = request.getFieldAnswers() != null ? request.getFieldAnswers() : Map.of();
        String newIdentifier = IdentifierPatch.requested(request.getIdentifier());
        String changedIdentifier = IdentifierPatch.changes(newIdentifier, dto.getFullIdentifier()) ? newIdentifier : null;
        boolean contentChange = !answers.isEmpty() || request.isGeomPresent() || changedIdentifier != null;
        requirePatchAllowed(dto, request.getValidationStatus(), contentChange, userInfo, projectId);

        if (contentChange) {
            saveContentPatch(dto, request, answers, changedIdentifier, userInfo, projectId);
        }
        // Status only (or nothing): syncRevision is the entity's @Version, so this bumps it too. After a
        // content save: it writes the DTO's (old) status back onto the entity, so it comes after.
        validationOpenApiService.apply(RecordingUnit.class, dto.getId(), request.getValidationStatus(), personDto);

        return resolveMobileDetail(recordingUnitKey, personDto, accessibleInstitutionIds, null, lang);
    }

    /** 400 when the unit has no organization: the caller's context is read in it. */
    private static InstitutionDTO requireOrganization(RecordingUnitDTO dto) {
        InstitutionDTO institution = dto.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unité sans organisation");
        }
        return institution;
    }

    /**
     * 403 unless the caller may make this change: a validator may change the status alone without the edit
     * right; anything else needs it, and the status transition its own rights.
     */
    private void requirePatchAllowed(RecordingUnitDTO dto, ValidationStatus requestedStatus, boolean contentChange,
                                     UserInfo userInfo, Long projectId) {
        boolean canEdit = profilePermissionService.hasRecordingUnitWritePermission(userInfo, dto);
        boolean statusChange = ValidationOpenApiService.changes(dto.getValidationStatus(), requestedStatus);
        if ((contentChange || !statusChange) && !canEdit) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification non autorisée");
        }
        validationOpenApiService.requireAllowed(dto.getValidationStatus(), requestedStatus, canEdit,
                profilePermissionService.hasValidatePermission(userInfo, projectId));
    }

    /**
     * Applies the identifier (409 when already used in the project), the geometry and the field answers
     * of a PATCH, then saves the unit.
     *
     * @param changedIdentifier the new identifier, or {@code null} when the PATCH leaves it unchanged
     */
    private void saveContentPatch(RecordingUnitDTO dto, RecordingUnitPatchRequest request, Map<String, Object> answers,
                                  String changedIdentifier, UserInfo userInfo, Long projectId) {
        if (changedIdentifier != null) {
            dto.setFullIdentifier(changedIdentifier);
            IdentifierPatch.requireFree(recordingUnitService.fullIdentifierAlreadyExistInAction(dto));
        }
        OpenApiExecutionContext.runWithUserInfo(userInfo, () -> {
            if (request.isGeomPresent()) {
                dto.setGeom(request.getGeom());
            }
            Map<CustomField, CustomFieldAnswerViewModel> additionalAnswers =
                    answers.isEmpty() ? Map.of() : applyFieldAnswers(dto, answers, projectId);
            try {
                recordingUnitService.save(dto, additionalAnswers);
            } catch (FailedRecordingUnitSaveException e) {
                throw IdentifierPatch.saveFailed(e);
            }
        });
    }

    /** The PATCH's field answers, applied against the unit's effective form; returns the additional ones. */
    private Map<CustomField, CustomFieldAnswerViewModel> applyFieldAnswers(RecordingUnitDTO dto,
                                                                          Map<String, Object> answers,
                                                                          Long projectId) {
        FormUiDto formUiDto = effectiveFormResolver.resolveEffectiveForm(projectId, ConfigurableTable.UE,
                dto.getType() != null ? dto.getType().getId() : null);
        // Lenient: the mobile client's legacy payloads may carry fields this form lacks.
        return fieldAnswerPatchService.applyLenient(dto, formUiDto, answers, projectId);
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
