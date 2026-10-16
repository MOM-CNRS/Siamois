package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldRelation;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationEntityKind;
import fr.siamois.domain.models.relation.RelationMirror;
import fr.siamois.domain.models.relation.RelationSide;
import fr.siamois.domain.models.settings.tableconfig.RelationOptions;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.infrastructure.database.initializer.seeder.ConceptSeeder;
import fr.siamois.infrastructure.database.initializer.seeder.RelationDefinitionSeeder;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FieldFormConfigRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FormConfigRepository;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.relation.DefinitionRelation;
import fr.siamois.infrastructure.database.repositories.relation.RelationDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationTypeMappingRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static fr.siamois.domain.models.relation.SystemRelations.VOCABULARY_BASE_URI;
import static fr.siamois.domain.models.relation.SystemRelations.VOCABULARY_ID;

/**
 * An institution's own relation definitions: creating one also creates the field(s) the relation is read and
 * edited through, ready to be added to the forms of a project from the field catalog; deleting one removes its
 * links, its fields and the places they were added at. The platform's definitions are read-only here.
 * <p>
 * One kind of entity per side (with any number of config types of that kind): a side that takes several kinds
 * is a later addition of the model, not a change to it.
 */
@Service
@RequiredArgsConstructor
public class RelationDefinitionService {

    private static final String FR = "fr";
    private static final String EN = "en";

    private final RelationDefinitionRepository definitionRepository;
    private final RelationLinkRepository linkRepository;
    private final RelationLinkService linkService;
    private final RelationTypeMappingRepository mappingRepository;
    private final CustomFieldRepository customFieldRepository;
    private final FieldFormConfigRepository fieldFormConfigRepository;
    private final FormConfigRepository formConfigRepository;
    private final InstitutionRepository institutionRepository;
    private final VocabularyRepository vocabularyRepository;
    private final ConceptRepository conceptRepository;
    private final LabelService labelService;
    private final RelationDefinitionSeeder seeder;
    private final ProfilePermissionService profilePermissionService;
    private final FieldConfigurationService fieldConfigurationService;

    /**
     * What an institution asks for when it creates a relation.
     *
     * @param sourceLabel  the field shown on the source entity (what it lists: its linked entities)
     * @param targetLabel  the mirror field shown on the target entity; ignored unless the mirror is {@code OTHER}
     * @param sourceTypes  the config types (concept ids) the source accepts, empty for any
     * @param targetTypes  the config types the target accepts, empty for any
     * @param uncertain    whether a link carries an "uncertain" flag
     * @param mirrorFieldId an existing field of the target entity to use as the mirror (see
     *                     {@link #mirrorCandidates}) instead of creating one; only for {@code OTHER}
     */
    public record Draft(String sourceLabel, String targetLabel, RelationMirror mirror,
                        RelationEntityKind sourceKind, List<Long> sourceTypes,
                        RelationEntityKind targetKind, List<Long> targetTypes, boolean uncertain,
                        Long mirrorFieldId) {
        public Draft(String sourceLabel, String targetLabel, RelationMirror mirror,
                     RelationEntityKind sourceKind, List<Long> sourceTypes,
                     RelationEntityKind targetKind, List<Long> targetTypes, boolean uncertain) {
            this(sourceLabel, targetLabel, mirror, sourceKind, sourceTypes, targetKind, targetTypes, uncertain, null);
        }
    }

    /** A config type an institution can restrict a side to. */
    public record TypeOption(Long id, String label) {
    }

    /** The config types of {@code kind} the project configures (empty for a kind with no configurable type). */
    @Transactional(readOnly = true)
    public List<TypeOption> typesOf(UserInfo userInfo, Long projectId, RelationEntityKind kind) {
        requireUser(userInfo);
        for (ConfigurableTable table : ConfigurableTable.values()) {
            if (table.relationKind() != kind) continue;
            try {
                Concept fieldConcept = fieldConfigurationService.findParentConceptForFieldcode(userInfo, table.getFieldCode());
                return formConfigRepository.findTypesOfProject(projectId, fieldConcept.getId()).stream()
                        .map(c -> new TypeOption(c.getId(), labelService.findLabelOf(c, userInfo.getLang()).getLabel()))
                        .sorted(java.util.Comparator.comparing(TypeOption::label, String.CASE_INSENSITIVE_ORDER))
                        .toList();
            } catch (RuntimeException | fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException e) {
                return List.of();
            }
        }
        return List.of();
    }

    @Transactional(rollbackFor = Exception.class)
    public RelationDefinition create(UserInfo userInfo, Draft draft) {
        requireManage(userInfo);
        validate(draft);
        Institution institution = institutionRepository.findById(userInfo.getInstitution().getId())
                .orElseThrow(() -> new NoSuchElementException("Institution inconnue"));

        String slug = slug(draft.sourceLabel());
        String code = "INST" + institution.getId() + "." + slug;
        for (int n = 2; definitionRepository.findByCode(code).isPresent(); n++) {
            code = "INST" + institution.getId() + "." + slug + "-" + n;
        }

        Vocabulary vocabulary = vocabularyRepository
                .findVocabularyByBaseUriAndVocabExternalId(VOCABULARY_BASE_URI, VOCABULARY_ID)
                .orElseThrow(() -> new IllegalStateException("Le vocabulaire des relations n'est pas initialisé"));
        Concept sourceConcept = newConcept(vocabulary, draft.sourceLabel());
        CustomField adopted = draft.mirror() == RelationMirror.OTHER && draft.mirrorFieldId() != null
                ? requireCandidate(userInfo, draft.mirrorFieldId(), draft.targetKind()) : null;
        RelationDefinition adoptedFrom = adopted == null ? null : definitionOf(adopted).orElseThrow();
        Concept targetConcept = adopted != null ? adopted.getConcept()
                : draft.mirror() == RelationMirror.OTHER ? newConcept(vocabulary, draft.targetLabel()) : null;

        RelationDefinition definition = new RelationDefinition();
        definition.setCode(code);
        definition.setInstitution(institution);
        definition.setSystem(false);
        definition.setMirror(draft.mirror());
        definition.setSourceSideConcept(sourceConcept);
        definition.setTargetSideConcept(targetConcept);
        addKinds(definition, RelationSide.SOURCE, draft.sourceKind(), draft.sourceTypes());
        addKinds(definition, RelationSide.TARGET, draft.targetKind(), draft.targetTypes());
        RelationDefinition saved = definitionRepository.save(definition);
        if (draft.uncertain()) {
            seeder.addUncertainLinkForm(saved);
        }

        saveField(draft.sourceLabel(), sourceConcept, saved.getCode(),
                draft.mirror() == RelationMirror.SELF ? DefinitionRelation.Reading.BOTH : DefinitionRelation.Reading.SOURCE,
                draft.targetKind());
        if (adopted != null) {
            adopted.setValueBinding(DefinitionRelation.bindingOf(saved.getCode(), DefinitionRelation.Reading.TARGET, draft.sourceKind()));
            customFieldRepository.save(adopted);
            dropDefinition(adoptedFrom, false);
        } else if (draft.mirror() == RelationMirror.OTHER) {
            saveField(draft.targetLabel(), targetConcept, saved.getCode(), DefinitionRelation.Reading.TARGET, draft.sourceKind());
        }
        return saved;
    }

    /** Deletes a definition of the user's institution with its links, its fields and where they were added. */
    @Transactional(rollbackFor = Exception.class)
    public void delete(UserInfo userInfo, Long definitionId) {
        requireManage(userInfo);
        RelationDefinition definition = definitionRepository.findById(definitionId)
                .orElseThrow(() -> new NoSuchElementException("Relation inconnue : " + definitionId));
        if (definition.isSystem() || definition.getInstitution() == null
                || !definition.getInstitution().getId().equals(userInfo.getInstitution().getId())) {
            throw new ForbiddenOperationException("Cette relation ne peut pas être supprimée");
        }
        dropDefinition(definition, true);
    }

    private void dropDefinition(RelationDefinition definition, boolean withFields) {
        linkService.deleteAllLinksOf(definition);
        if (withFields) {
            for (CustomField field : fieldsOf(definition)) {
                fieldFormConfigRepository.deleteAll(fieldFormConfigRepository.findAllByField(field));
                customFieldRepository.delete(field);
            }
        }
        mappingRepository.deleteAll(mappingRepository.findByDefinition(definition));
        var linkForm = definition.getLinkFormConfig();
        definitionRepository.delete(definition);
        if (linkForm != null) {
            fieldFormConfigRepository.deleteAll(fieldFormConfigRepository.findAllByFormConfigId(linkForm.getId()));
            formConfigRepository.delete(linkForm);
        }
    }

    /**
     * Cleans up after the last form that carried a relation's field no longer does: a relation with no link and no field
     * left in any form is deleted; one that still has links is kept, so no link is lost by taking a field off a form.
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteIfUnused(UserInfo userInfo, CustomField field) {
        RelationDefinition definition = definitionOf(field).orElse(null);
        if (definition == null || definition.isSystem() || definition.getInstitution() == null
                || !definition.getInstitution().getId().equals(userInfo.getInstitution().getId())
                || linkRepository.countByDefinition(definition) > 0) {
            return;
        }
        boolean placed = fieldsOf(definition).stream().anyMatch(f -> fieldFormConfigRepository.countByFieldId(f.getId()) > 0);
        if (!placed) {
            delete(userInfo, definition.getId());
        }
    }

    /**
     * The fields an entity of {@code kind} already has that can be the mirror of a new relation: the field of one of the
     * institution's relations that has no mirror and no link yet. Using one replaces that relation (empty) by the new one.
     */
    @Transactional(readOnly = true)
    public List<CustomField> mirrorCandidates(UserInfo userInfo, RelationEntityKind kind) {
        requireUser(userInfo);
        List<CustomField> out = new ArrayList<>();
        if (kind == null) return out;
        for (RelationDefinition definition : definitionRepository.findVisibleTo(userInfo.getInstitution().getId())) {
            if (isCandidate(userInfo, definition, kind)) {
                fieldsOf(definition).stream().filter(RelationDefinitionService::isSourceReading).forEach(out::add);
            }
        }
        return out;
    }

    private boolean isCandidate(UserInfo userInfo, RelationDefinition definition, RelationEntityKind kind) {
        return !definition.isSystem() && definition.getInstitution() != null
                && definition.getInstitution().getId().equals(userInfo.getInstitution().getId())
                && definition.getMirror() == RelationMirror.NONE
                && kindOf(definition, RelationSide.SOURCE) == kind
                && linkRepository.countByDefinition(definition) == 0;
    }

    private CustomField requireCandidate(UserInfo userInfo, Long fieldId, RelationEntityKind kind) {
        return mirrorCandidates(userInfo, kind).stream().filter(f -> f.getId().equals(fieldId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Ce champ ne peut pas servir de miroir : " + fieldId));
    }

    /** The fields of a definition: the one on the source side and, when mirrored, the one on the target side. */
    public List<CustomField> fieldsOf(RelationDefinition definition) {
        return customFieldRepository.findByValueBindingStartingWith(DefinitionRelation.PREFIX + definition.getCode() + ":");
    }

    /**
     * The fields of the institution's relations that an entity of {@code ownerKind} can carry: the ones that read
     * the relation from the end that kind is at.
     */
    @Transactional(readOnly = true)
    public List<CustomField> fieldsFor(Long institutionId, RelationEntityKind ownerKind) {
        List<CustomField> out = new ArrayList<>();
        for (RelationDefinition definition : definitionRepository.findVisibleTo(institutionId)) {
            if (definition.isSystem()) continue;
            for (CustomField field : fieldsOf(definition)) {
                if (DefinitionRelation.parse(ownerKind, field.getValueBinding())
                        .filter(r -> ownerKindOf(definition, r) == ownerKind).isPresent()) {
                    out.add(field);
                }
            }
        }
        return out;
    }

    private static RelationEntityKind ownerKindOf(RelationDefinition definition, DefinitionRelation relation) {
        return kindOf(definition, relation.reading() == DefinitionRelation.Reading.TARGET ? RelationSide.TARGET : RelationSide.SOURCE);
    }

    /** The field of a definition that lists the far end from the entity that owns the link (the symmetric field too). */
    public CustomField sourceFieldOf(RelationDefinition definition) {
        return fieldsOf(definition).stream()
                .filter(f -> f.getValueBinding() != null && !f.getValueBinding().contains(":" + DefinitionRelation.Reading.TARGET + ":"))
                .findFirst().orElseThrow(() -> new IllegalStateException("Relation sans champ : " + definition.getCode()));
    }

    /** The relation a field belongs to, when it is the field of one. */
    @Transactional(readOnly = true)
    public Optional<RelationDefinition> definitionOf(CustomField field) {
        String binding = field.getValueBinding();
        if (!DefinitionRelation.isBinding(binding)) return Optional.empty();
        String[] parts = binding.substring(DefinitionRelation.PREFIX.length()).split(":");
        return parts.length == 0 ? Optional.empty() : definitionRepository.findByCode(parts[0]);
    }

    /** What a relation field's detail shows: the far kind, the types each side accepts, the mirror and the link form. */
    @Transactional(readOnly = true)
    public Optional<RelationOptions> describe(CustomField field) {
        return definitionOf(field).map(definition -> {
            String mirrorLabel = definition.getMirror() == RelationMirror.OTHER
                    ? fieldsOf(definition).stream()
                    .filter(f -> isSourceReading(f) != isSourceReading(field))
                    .map(CustomField::getLabel).findFirst().orElse(null)
                    : null;
            boolean fromSource = isSourceReading(field);
            return new RelationOptions(
                    kindOf(definition, fromSource ? RelationSide.TARGET : RelationSide.SOURCE),
                    typeIdsOf(definition, fromSource ? RelationSide.SOURCE : RelationSide.TARGET),
                    typeIdsOf(definition, fromSource ? RelationSide.TARGET : RelationSide.SOURCE),
                    definition.getMirror(), mirrorLabel, definition.getLinkFormConfig() != null);
        });
    }

    private static boolean isSourceReading(CustomField field) {
        return !field.getValueBinding().contains(":" + DefinitionRelation.Reading.TARGET + ":");
    }

    private static List<Long> typeIdsOf(RelationDefinition definition, RelationSide side) {
        return definition.kindsOf(side).stream().map(k -> k.getTypeConcept()).filter(java.util.Objects::nonNull)
                .map(Concept::getId).toList();
    }

    /**
     * Applies what the field's detail edits: the accepted types of each side, and the names of its fields. The kind, the
     * mirror and the link form are fixed at creation. The types are those of the entities as seen from the field: its
     * owner's first, the linked entity's second.
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(UserInfo userInfo, CustomField field, String label, RelationOptions options) {
        requireManage(userInfo);
        RelationDefinition definition = definitionOf(field)
                .orElseThrow(() -> new NoSuchElementException("Ce champ n'est pas une relation"));
        if (definition.isSystem() || definition.getInstitution() == null
                || !definition.getInstitution().getId().equals(userInfo.getInstitution().getId())) {
            throw new ForbiddenOperationException("Cette relation ne peut pas être modifiée");
        }
        boolean fromSource = isSourceReading(field);
        RelationSide ownerSide = fromSource ? RelationSide.SOURCE : RelationSide.TARGET;
        RelationSide farSide = fromSource ? RelationSide.TARGET : RelationSide.SOURCE;
        replaceKinds(definition, ownerSide, options.sourceTypes());
        replaceKinds(definition, farSide, options.targetTypes());
        definitionRepository.save(definition);

        if (!blank(label)) {
            field.setLabel(label.trim());
            customFieldRepository.save(field);
        }
        if (definition.getMirror() == RelationMirror.OTHER && !blank(options.mirrorLabel())) {
            fieldsOf(definition).stream().filter(f -> isSourceReading(f) != fromSource).forEach(other -> {
                other.setLabel(options.mirrorLabel().trim());
                customFieldRepository.save(other);
            });
        }
    }

    private void replaceKinds(RelationDefinition definition, RelationSide side, List<Long> typeIds) {
        RelationEntityKind kind = kindOf(definition, side);
        definition.getKinds().removeIf(k -> k.getSide() == side);
        addKinds(definition, side, kind, typeIds);
    }

    private static RelationEntityKind kindOf(RelationDefinition definition, RelationSide side) {
        return definition.kindsOf(side).stream().map(k -> k.getEntityKind()).findFirst().orElse(null);
    }

    private void validate(Draft draft) {
        if (draft == null || blank(draft.sourceLabel())) {
            throw new IllegalArgumentException("Le nom du champ est obligatoire");
        }
        if (draft.mirror() == null || draft.sourceKind() == null || draft.targetKind() == null) {
            throw new IllegalArgumentException("Le miroir et les genres d'entité sont obligatoires");
        }
        if (!draft.sourceKind().canBeSource() || !isPlaceable(draft.sourceKind())) {
            throw new IllegalArgumentException("Une relation ne part pas de ce genre d'entité : " + draft.sourceKind());
        }
        if (draft.mirror() == RelationMirror.OTHER) {
            if (draft.mirrorFieldId() == null && blank(draft.targetLabel())) {
                throw new IllegalArgumentException("Le nom du champ miroir est obligatoire");
            }
            if (!draft.targetKind().canBeSource() || !isPlaceable(draft.targetKind())) {
                throw new IllegalArgumentException("Pas de miroir possible : ce genre d'entité n'a pas de formulaire configurable");
            }
        }
        if (draft.mirror() == RelationMirror.SELF && draft.sourceKind() != draft.targetKind()) {
            throw new IllegalArgumentException("Une relation symétrique relie deux entités du même genre");
        }
    }

    /** Whether the kind has a configurable form, where the relation's field can be added. */
    public static boolean isPlaceable(RelationEntityKind kind) {
        return Set.of(RelationEntityKind.RECORDING_UNIT, RelationEntityKind.SPECIMEN, RelationEntityKind.PHASE,
                RelationEntityKind.CONTAINER, RelationEntityKind.DOCUMENT, RelationEntityKind.STUDY).contains(kind);
    }

    private void addKinds(RelationDefinition definition, RelationSide side, RelationEntityKind kind, List<Long> types) {
        if (types == null || types.isEmpty()) {
            definition.addKind(side, kind, null);
            return;
        }
        for (Long typeId : types) {
            Concept type = conceptRepository.findById(typeId)
                    .orElseThrow(() -> new IllegalArgumentException("Type inconnu : " + typeId));
            definition.addKind(side, kind, type);
        }
    }

    private void saveField(String label, Concept concept, String code, DefinitionRelation.Reading reading,
                           RelationEntityKind farKind) {
        customFieldRepository.save(CustomFieldRelation.builder()
                .label(label.trim())
                .isSystemField(false)
                .concept(concept)
                .valueBinding(DefinitionRelation.bindingOf(code, reading, farKind))
                .build());
    }

    private Concept newConcept(Vocabulary vocabulary, String label) {
        Concept concept = new Concept();
        concept.setExternalId("rel." + UUID.randomUUID());
        concept.setVocabulary(vocabulary);
        concept.setLoaded(true);
        Concept saved = conceptRepository.save(concept);
        labelService.updateLabel(saved, FR, label.trim(), null);
        labelService.updateLabel(saved, EN, label.trim(), null);
        return saved;
    }

    private static String slug(String label) {
        String ascii = Normalizer.normalize(label, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return ascii.isEmpty() ? "relation" : ascii;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static void requireUser(UserInfo userInfo) {
        if (userInfo == null) {
            throw new ForbiddenOperationException("No user in the session");
        }
    }

    private void requireManage(UserInfo userInfo) {
        requireUser(userInfo);
        boolean allowed = profilePermissionService.hasInstancePermission(
                userInfo.getUser(), PermissionConstants.INSTANCE_MANAGE_SETTINGS)
                || profilePermissionService.hasOrganizationPermission(userInfo, PermissionConstants.ORGANIZATION_MANAGE_SETTINGS);
        if (!allowed) {
            throw new ForbiddenOperationException("Cannot manage the relations of this institution");
        }
    }
}
