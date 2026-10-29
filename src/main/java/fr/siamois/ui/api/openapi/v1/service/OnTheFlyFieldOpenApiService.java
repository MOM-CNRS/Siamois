package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldOnTheFly;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.LangService;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.domain.services.form.OnTheFlyFieldService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.ConceptLabelRepository;
import fr.siamois.domain.services.measurement.UnitDefinitionService;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.ui.api.openapi.v1.OpenApiExecutionContext;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.infrastructure.database.repositories.measurement.UnitDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.ui.api.openapi.v1.request.OnTheFlyMeasurementRequest;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import fr.siamois.ui.api.openapi.v1.resource.form.OnTheFlyFieldsResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * "Ajouter une mesure": the fields a user adds on the fly to one record's fiche, for every kind of
 * record. Reading needs to see the record, writing needs the right to edit it.
 */
@Service
@RequiredArgsConstructor
public class OnTheFlyFieldOpenApiService {

    private final OnTheFlyFieldService onTheFlyFieldService;
    private final RecordingUnitService recordingUnitService;
    private final SpecimenService specimenService;
    private final PhaseOpenApiService phaseOpenApiService;
    private final ContainerOpenApiService containerOpenApiService;
    private final DocumentOpenApiService documentOpenApiService;
    private final StudyOpenApiService studyOpenApiService;
    private final ProfilePermissionService profilePermissionService;
    private final ConceptRepository conceptRepository;
    private final UnitDefinitionRepository unitDefinitionRepository;
    private final LabelService labelService;
    private final LangService langService;
    private final TableFieldConfigService tableFieldConfigService;
    private final FieldConfigurationService fieldConfigurationService;
    private final ConceptLabelRepository conceptLabelRepository;
    private final UnitDefinitionService unitDefinitionService;

    @PersistenceContext
    private EntityManager entityManager;

    /** The record a request is about: where it sits and whether the caller may edit it. */
    private record Target(UserInfo userInfo, boolean canEdit, Long projectId) {
    }

    @Transactional(readOnly = true)
    public OnTheFlyFieldsResponse list(ConfigurableTable table, long id, PersonDTO person,
                                       Set<Long> accessibleInstitutionIds, String lang) {
        resolve(table, id, person, accessibleInstitutionIds, lang);
        return response(table, id, lang);
    }

    @Transactional
    public OnTheFlyFieldsResponse addMeasurement(ConfigurableTable table, long id, OnTheFlyMeasurementRequest body,
                                                 PersonDTO person, Set<Long> accessibleInstitutionIds, String lang) {
        Target target = resolve(table, id, person, accessibleInstitutionIds, lang);
        requireEdit(target);
        if (body != null && body.copyFieldId() != null) {
            return copyExisting(table, id, target, body, person, lang);
        }
        if (body == null || (body.typeConceptId() == null && isBlank(body.newTypeLabel()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le type de la mesure est obligatoire");
        }
        Concept type = body.typeConceptId() != null ? requireConcept(body.typeConceptId())
                : localConcept(target, CustomFieldMeasurement.MEASUREMENT_TYPE_FIELD_CODE, body.newTypeLabel(), lang);
        Concept nature = body.natureConceptId() != null ? requireConcept(body.natureConceptId())
                : isBlank(body.newNatureLabel()) ? null
                : localConcept(target, CustomFieldMeasurement.MEASUREMENT_NATURE_FIELD_CODE, body.newNatureLabel(), lang);
        List<UnitDefinition> units = requireUnits(body);

        String label = labelOf(type, lang) + (nature != null ? " " + labelOf(nature, lang) : "");
        CustomFieldMeasurement field = CustomFieldMeasurement.builder().build();
        field.setLabel(label);
        field.setIsSystemField(false);
        field.setConcept(type);
        field.setMeasurementType(type);
        field.setMeasurementNature(nature);
        field.setUnit(units.get(0));
        field.setAllowedUnits(new LinkedHashSet<>(units));
        field.setAuthor(entityManager.getReference(Person.class, person.getId()));

        onTheFlyFieldService.add(table, id, body.panel(), field);
        return response(table, id, lang);
    }

    @Transactional
    public OnTheFlyFieldsResponse remove(ConfigurableTable table, long id, long fieldId, PersonDTO person,
                                         Set<Long> accessibleInstitutionIds, String lang) {
        requireEdit(resolve(table, id, person, accessibleInstitutionIds, lang));
        if (!onTheFlyFieldService.remove(table, id, fieldId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Champ introuvable sur cet enregistrement");
        }
        return response(table, id, lang);
    }

    /** A measure already used on another record of the table, offered for reuse. */
    public record ExistingMeasure(Long fieldId, String label, String unit) {
    }

    @Transactional(readOnly = true)
    public List<ExistingMeasure> existingMeasurements(ConfigurableTable table, long id, String query, PersonDTO person,
                                                      Set<Long> accessibleInstitutionIds, String lang) {
        Target target = resolve(table, id, person, accessibleInstitutionIds, lang);
        Set<Long> already = onTheFlyFieldService.fieldsOf(table, id).stream().map(f -> f.getId())
                .collect(java.util.stream.Collectors.toSet());
        return onTheFlyFieldService.findReusableMeasurements(table, target.projectId(), query, 30).stream()
                .filter(m -> !already.contains(m.getId()))
                .map(m -> new ExistingMeasure(m.getId(), m.getLabel(), m.getUnit() == null ? null : m.getUnit().getSymbol()))
                .toList();
    }

    private OnTheFlyFieldsResponse copyExisting(ConfigurableTable table, long id, Target target,
                                                OnTheFlyMeasurementRequest body, PersonDTO person, String lang) {
        // Only a measure offered for reuse (already on a record of this table in the project) can be copied.
        CustomFieldMeasurement source = onTheFlyFieldService.findReusableMeasurements(table, target.projectId(), "", Integer.MAX_VALUE)
                .stream().filter(m -> body.copyFieldId().equals(m.getId())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mesure introuvable"));
        CustomFieldMeasurement copy = onTheFlyFieldService.copyOf(source);
        copy.setAuthor(entityManager.getReference(Person.class, person.getId()));
        onTheFlyFieldService.add(table, id, body.panel(), copy);
        return response(table, id, lang);
    }

    /** A choice offered by the "Ajouter une mesure" dialog. */
    public record Option(Long id, String label) {
    }

    /** A unit a measurement can be entered in. */
    public record UnitOption(Long id, String label, String symbol, String dimension, boolean systemBase) {
    }

    /**
     * The concepts a measurement can be about ({@code TYPE}: SIAMD.TYPE) or qualified with
     * ({@code NATURE}: SIAMD.NATURE) that match the typed text.
     */
    @Transactional(readOnly = true)
    public List<Option> measurementConcepts(ConfigurableTable table, long id, String kind, String query,
                                            PersonDTO person, Set<Long> accessibleInstitutionIds, String lang) {
        Target target = resolve(table, id, person, accessibleInstitutionIds, lang);
        String fieldCode = "NATURE".equalsIgnoreCase(kind)
                ? CustomFieldMeasurement.MEASUREMENT_NATURE_FIELD_CODE : CustomFieldMeasurement.MEASUREMENT_TYPE_FIELD_CODE;
        return OpenApiExecutionContext.callWithUserInfo(target.userInfo(),
                () -> tableFieldConfigService.measureConcepts(target.projectId(), fieldCode, query)).stream()
                .map(c -> new Option(c.concept().getId(), c.getConceptLabelToDisplay() != null
                        ? c.getConceptLabelToDisplay().getLabel() : c.getOriginalPrefLabel()))
                .toList();
    }

    /** The units the dialog offers. */
    @Transactional(readOnly = true)
    public List<UnitOption> measurementUnits(ConfigurableTable table, long id, PersonDTO person,
                                             Set<Long> accessibleInstitutionIds, String lang) {
        resolve(table, id, person, accessibleInstitutionIds, lang);
        return unitDefinitionService.findOptions().stream()
                .map(u -> new UnitOption(u.getId(), u.getLabel(), u.getSymbol(),
                        u.getDimension() == null ? null : u.getDimension().name(), u.isSystemBase()))
                .toList();
    }

    private OnTheFlyFieldsResponse response(ConfigurableTable table, long id, String lang) {
        Locale locale = langService.localeForApiLang(lang);
        Map<String, FieldResource> fields = new LinkedHashMap<>();
        List<OnTheFlyFieldsResponse.Placement> placements = new ArrayList<>();
        for (CustomFieldOnTheFly field : onTheFlyFieldService.fieldsOf(table, id)) {
            String key = String.valueOf(field.getId());
            fields.put(key, FieldAnswerWireService.fieldResourceOf(field,
                    langService.resolveMessage(field.getLabel(), locale),
                    langService.resolveMessage(field.getHint(), locale)));
            placements.add(new OnTheFlyFieldsResponse.Placement(key, field.getPanelName()));
        }
        return new OnTheFlyFieldsResponse(fields, placements);
    }

    private static void requireEdit(Target target) {
        if (!target.canEdit()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification non autorisée");
        }
    }

    private Target resolve(ConfigurableTable table, long id, PersonDTO person, Set<Long> accessibleInstitutionIds,
                           String lang) {
        return switch (table) {
            case UE -> {
                RecordingUnitDTO dto = recordingUnitService.requireAccessibleRecordingUnitByPrimaryKey(
                        id, accessibleInstitutionIds);
                UserInfo user = userInfo(dto.getCreatedByInstitution(), person, lang);
                yield new Target(user, profilePermissionService.hasRecordingUnitWritePermission(user, dto),
                        dto.getActionUnit() != null ? dto.getActionUnit().getId() : null);
            }
            case MOBILIER -> {
                SpecimenDTO dto = specimenService.findAccessibleById(id, accessibleInstitutionIds)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mobilier introuvable"));
                UserInfo user = userInfo(dto.getCreatedByInstitution(), person, lang);
                yield new Target(user, profilePermissionService.hasSpecimenWritePermission(user, dto),
                        dto.getActionUnit() != null ? dto.getActionUnit().getId() : null);
            }
            case PHASE -> {
                var dto = phaseOpenApiService.requireAccessible(id, person, accessibleInstitutionIds);
                yield projectTarget(dto.getActionUnit(), person, lang,
                        PermissionConstants.INSTANCE_EDIT_PHASES, PermissionConstants.ORGANIZATION_EDIT_PHASES,
                        PermissionConstants.PROJECT_EDIT_PHASES);
            }
            case CONTENANT -> {
                var dto = containerOpenApiService.requireAccessible(id, person, accessibleInstitutionIds);
                yield projectTarget(dto.getActionUnit(), person, lang,
                        PermissionConstants.INSTANCE_EDIT_CONTAINERS, PermissionConstants.ORGANIZATION_EDIT_CONTAINERS,
                        PermissionConstants.PROJECT_EDIT_CONTAINERS);
            }
            case DOCUMENT -> {
                var dto = documentOpenApiService.requireAccessible(id, person, accessibleInstitutionIds);
                yield projectTarget(dto.getActionUnit(), person, lang,
                        PermissionConstants.INSTANCE_EDIT_DOCUMENTS, PermissionConstants.ORGANIZATION_EDIT_DOCUMENTS,
                        PermissionConstants.PROJECT_EDIT_DOCUMENTS);
            }
            case STUDY -> {
                var dto = studyOpenApiService.requireAccessible(id, person, accessibleInstitutionIds);
                yield projectTarget(dto.getActionUnit(), person, lang,
                        PermissionConstants.INSTANCE_EDIT_STUDIES, PermissionConstants.ORGANIZATION_EDIT_STUDIES,
                        PermissionConstants.PROJECT_EDIT_STUDIES);
            }
            case PROJECT -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Type d'enregistrement non pris en charge");
        };
    }

    private Target projectTarget(ActionUnitSummaryDTO project, PersonDTO person, String lang, String instance,
                                String organization, String projectPermission) {
        UserInfo user = userInfo(project.getCreatedByInstitution(), person, lang);
        return new Target(user, profilePermissionService.hasProjectPermission(user, project.getId(), instance, organization,
                projectPermission), project.getId());
    }

    private static UserInfo userInfo(InstitutionDTO institution, PersonDTO person, String lang) {
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enregistrement sans organisation");
        }
        return new UserInfo(institution, person, lang);
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /**
     * The concept of the field code (SIAMD.TYPE or SIAMD.NATURE) with that name: the one that already exists, else
     * a new local concept — in the field's vocabulary, with a label but no URI behind it — which the field's
     * autocomplete then offers like any other.
     */
    private Concept localConcept(Target target, String fieldCode, String name, String lang) {
        String label = name.trim();
        String langCode = lang == null ? "fr" : lang;
        return OpenApiExecutionContext.callWithUserInfo(target.userInfo(), () -> {
            for (var existing : tableFieldConfigService.measureConcepts(target.projectId(), fieldCode, label)) {
                String existingLabel = existing.getConceptLabelToDisplay() != null
                        ? existing.getConceptLabelToDisplay().getLabel() : existing.getOriginalPrefLabel();
                if (label.equalsIgnoreCase(existingLabel)) {
                    return requireConcept(existing.concept().getId());
                }
            }
            Concept fieldConcept;
            try {
                fieldConcept = fieldConfigurationService.findConfigurationForFieldCode(
                        target.userInfo(), fieldCode, target.projectId()).getConcept();
            } catch (NoConfigForFieldException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Ce champ de mesure n'est pas configuré pour l'organisation", e);
            }
            Concept concept = new Concept();
            concept.setVocabulary(fieldConcept.getVocabulary());
            concept.setExternalId("local-" + java.util.UUID.randomUUID());
            concept.setLoaded(true);
            concept = conceptRepository.save(concept);
            ConceptPrefLabel prefLabel = new ConceptPrefLabel();
            prefLabel.setConcept(concept);
            prefLabel.setLangCode(langCode);
            prefLabel.setLabel(label);
            // The field it is a value of: what the field's autocomplete searches on.
            prefLabel.setParentConcept(fieldConcept);
            conceptLabelRepository.save(prefLabel);
            return concept;
        });
    }

    private Concept requireConcept(Long id) {
        return conceptRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Concept introuvable : " + id));
    }

    private String labelOf(Concept concept, String lang) {
        return labelService.findLabelOf(concept, lang == null ? "fr" : lang).getLabel();
    }

    /** The default unit first, then the others; all of the same dimension. */
    private List<UnitDefinition> requireUnits(OnTheFlyMeasurementRequest body) {
        Set<Long> ids = new LinkedHashSet<>();
        if (body.defaultUnitId() != null) ids.add(body.defaultUnitId());
        if (body.allowedUnitIds() != null) ids.addAll(body.allowedUnitIds());
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Une mesure a besoin d'au moins une unité");
        }
        Map<Long, UnitDefinition> found = new LinkedHashMap<>();
        unitDefinitionRepository.findAllById(ids).forEach(unit -> found.put(unit.getId(), unit));
        if (!found.keySet().containsAll(ids)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unité inconnue");
        }
        List<UnitDefinition> units = ids.stream().map(found::get).toList();
        if (units.stream().map(UnitDefinition::getDimension).distinct().count() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Les unités d'une mesure doivent mesurer la même grandeur");
        }
        return units;
    }
}
