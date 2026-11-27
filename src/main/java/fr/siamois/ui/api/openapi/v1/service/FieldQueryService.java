package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.form.config.SystemFieldSpec;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldGeometry;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.form.customfieldanswer.actionunit.CustomFieldAnswerActionUnit;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.*;
import fr.siamois.domain.models.form.customfieldanswer.measurement.CustomFieldAnswerMeasurement;
import fr.siamois.domain.models.form.customfieldanswer.person.CustomFieldAnswerSelectPerson;
import fr.siamois.domain.models.form.customfieldanswer.spatialunit.CustomFieldAnswerSpatialUnit;
import fr.siamois.domain.models.form.customfieldanswer.vocabulary.CustomFieldAnswerSelectConcept;
import fr.siamois.domain.models.form.measurement.MeasurementAnswer;
import fr.siamois.domain.models.form.rules.FieldRules;
import fr.siamois.domain.models.form.rules.FieldRulesJson;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.form.customfield.CustomFieldRelation;
import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationEndKind;
import fr.siamois.domain.models.relation.RelationLink;
import fr.siamois.infrastructure.database.repositories.relation.DefinitionRelation;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.study.Study;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.dto.FieldQuery;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationField;
import fr.siamois.ui.api.openapi.v1.request.list.FieldListQuery;
import fr.siamois.ui.api.openapi.v1.request.list.FilterOperator;
import fr.siamois.ui.api.openapi.v1.request.list.FilterOperator.Match;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import fr.siamois.ui.form.CustomFieldAnswerFactory;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.form.fieldsource.PanelFieldSource;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.PluralAttribute;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.locationtech.jts.geom.Geometry;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.function.Function;

/**
 * Sorting and filtering a list by any of its entity's form fields, whatever the field — one
 * mechanism for every list column instead of a hand-written allow-list per entity.
 *
 * <p>A field is resolved to where its value lives:</p>
 * <ul>
 *   <li>a <strong>system</strong> field, through its {@code valueBinding}, to a property of the
 *       entity (checked against the JPA metamodel — a binding the entity doesn't map is simply not
 *       queryable): a scalar column, a single reference (concept or entity) or a collection;</li>
 *   <li>an <strong>additional</strong> field, to its answer rows ({@code form_config_answer →
 *       custom_field_answer}), reached by a correlated subquery — only the four entities that carry
 *       additional answers (recording unit, find, phase, container) have any.</li>
 * </ul>
 *
 * <p>Then one strategy per kind of value: text (filter {@code contains}, sort case-insensitive),
 * number (range), date (range of ISO dates), reference (filter {@code in} ids, sort by the target's
 * label — the concept's preferred label in the caller's language, an entity's identifier/name) and
 * multi-valued reference (filter {@code in} = has at least one of them, sort by number of values).
 * Every sort puts empty values last and ends on the id, so paging is stable.</p>
 *
 * <p>A filter may name another operator than its kind's default ({@code f.<id>.op}, {@link FilterOperator}):
 * the other text matches (is, starts with, ends with), the negations (is not, does not contain, none
 * of) and empty / not empty, for every kind. A negation keeps exactly the rows its positive form
 * leaves out, empty values included.</p>
 */
@Service
@RequiredArgsConstructor
public class FieldQueryService {



    /** The {@link fr.siamois.domain.models.form.config.FormConfigAnswer} property naming each owner. */
    private static final Map<Class<?>, String> ANSWER_OWNERS = Map.of(
            RecordingUnit.class, "recordingUnit",
            Specimen.class, "specimen",
            Phase.class, "phase",
            Container.class, "container",
            Document.class, "document",
            Study.class, "study");

    /** Where an entity's human label is, in order of preference. */
    private static final List<String> LABEL_ATTRIBUTES =
            List.of("fullIdentifier", "lastname", "name", "title", "identifier", "code");

    /**
     * A form binds the DTO's property name, which is not always the JPA attribute's: the recording
     * unit's {@code chronologicalPhase} is the entity's {@code chronologicalAttribution}
     * ({@code RecordingUnitMapper} maps one to the other).
     */
    private static final Map<Class<?>, Map<String, String>> ENTITY_ATTRIBUTES = Map.of(
            RecordingUnit.class, Map.of("chronologicalPhase", "chronologicalAttribution"));
    public static final String LE_CHAMP = "Le champ ";

    /** The entity attribute a form's {@code valueBinding} reads, differing only by the aliases above. */
    static String entityAttribute(Class<?> entityType, String binding) {
        return ENTITY_ATTRIBUTES.getOrDefault(entityType, Map.of()).getOrDefault(binding, binding);
    }

    /** The one property of a measurement that lists compare and sort on: its value in the base unit. */
    private static final String MEASUREMENT_VALUE = "normalizedValue";

    /**
     * Each entity's system fields, as its details form declares them in code — some (the project's)
     * have no {@code custom_field} row, so a lookup by id cannot rely on the database alone.
     */
    private static final Map<Class<?>, Map<Long, CustomField>> SYSTEM_FIELDS = Map.of(
            ActionUnit.class, systemFieldsOf(ActionUnit.DETAILS_FORM),
            RecordingUnit.class, systemFieldsOf(SystemFieldCatalog.sharedFieldsOf(ConfigurableTable.UE)),
            Specimen.class, systemFieldsOf(SystemFieldCatalog.sharedFieldsOf(ConfigurableTable.MOBILIER)),
            Phase.class, systemFieldsOf(SystemFieldCatalog.sharedFieldsOf(ConfigurableTable.PHASE)),
            Container.class, systemFieldsOf(SystemFieldCatalog.sharedFieldsOf(ConfigurableTable.CONTENANT)),
            Document.class, systemFieldsOf(SystemFieldCatalog.sharedFieldsOf(ConfigurableTable.DOCUMENT)),
            Study.class, systemFieldsOf(SystemFieldCatalog.sharedFieldsOf(ConfigurableTable.STUDY)),
            SpatialUnit.class, systemFieldsOf(SpatialUnit.DETAILS_FORM));

    private static Map<Long, CustomField> systemFieldsOf(fr.siamois.ui.form.dto.FormUiDto form) {
        return systemFieldsOf(new PanelFieldSource(form).getAllFields());
    }

    private static Map<Long, CustomField> systemFieldsOf(java.util.Collection<CustomField> fields) {
        Map<Long, CustomField> out = new HashMap<>();
        for (CustomField field : fields) {
            if (field != null && field.getId() != null) out.put(field.getId(), field);
        }
        return out;
    }

    private final CustomFieldRepository customFieldRepository;
    private final EntityManager entityManager;

    /** The attribute of a configurable relation field's target: not a property, the links of its definition. */
    private static final String RELATION_LINK = "relationLink";
    private static final String SOURCE_ID = "sourceId";
    private static final String TARGET_ID = "targetId";

    private enum Kind {
        TEXT("contains", FilterOperator.CONTAINS, Match.EQUAL, Match.CONTAINS, Match.STARTS_WITH, Match.ENDS_WITH),
        NUMBER("range", FilterOperator.BETWEEN, Match.RANGE),
        DATE("date-range", FilterOperator.BETWEEN, Match.RANGE),
        BOOLEAN("equals", FilterOperator.IN, Match.ANY_OF),
        REFERENCE("in", FilterOperator.IN, Match.ANY_OF),
        REFERENCES("in", FilterOperator.IN, Match.ANY_OF),
        /** One of an enum's constants: filtered by constant names, sorted in declaration order. */
        CHOICE("in", FilterOperator.IN, Match.ANY_OF),
        /** A shape (a column of geometries, or WKT text): only filtered on having one or not, never sorted. */
        GEOMETRY("presence", FilterOperator.NOT_EMPTY);

        final String filterOp;
        /** The operator of a filter that names none: what {@link #filterOp} has always meant. */
        final FilterOperator defaultOperator;
        /** What a filter on this kind can compare with; having a value or not, every kind can. */
        final Set<Match> matches;

        Kind(String filterOp, FilterOperator defaultOperator, Match... matches) {
            this.filterOp = filterOp;
            this.defaultOperator = defaultOperator;
            this.matches = Collections.unmodifiableSet(EnumSet.of(Match.PRESENT, matches));
        }

        boolean accepts(FilterOperator operator) {
            return matches.contains(operator.match());
        }

        boolean sortable() {
            return this != GEOMETRY;
        }

        /** A number or a date: filtered by bounds. */
        boolean isRange() {
            return matches.contains(Match.RANGE);
        }

        /** One or several entities: filtered by their ids. */
        boolean isReference() {
            return this == REFERENCE || this == REFERENCES;
        }
    }

    /**
     * Where a field's value is, and how it is compared. {@code attribute} is the entity property (a
     * system field) or the answer's own property (an additional one: {@code value}, or the
     * collection of referenced entities); {@code labelAttribute} is the referenced entity's label,
     * null for a concept; {@code idFilterable} is false when the target isn't keyed by a numeric id;
     * {@code nested} names the property read on a single linked entity (a measurement's value)
     * rather than the attribute itself.
     */
    private record Target(boolean system, String attribute, Kind kind, Class<?> valueType,
                          @Nullable Class<? extends CustomFieldAnswer> answerClass,
                          boolean concept, @Nullable String labelAttribute, boolean idFilterable,
                          @Nullable String nested, @Nullable LinkSide links) {

        Target(boolean system, String attribute, Kind kind, Class<?> valueType,
               @Nullable Class<? extends CustomFieldAnswer> answerClass,
               boolean concept, @Nullable String labelAttribute, boolean idFilterable) {
            this(system, attribute, kind, valueType, answerClass, concept, labelAttribute, idFilterable, null, null);
        }

        Target(boolean system, String attribute, Kind kind, Class<?> valueType,
               @Nullable Class<? extends CustomFieldAnswer> answerClass,
               boolean concept, @Nullable String labelAttribute, boolean idFilterable, @Nullable String nested) {
            this(system, attribute, kind, valueType, answerClass, concept, labelAttribute, idFilterable, nested, null);
        }
    }

    /** A configurable relation field's side: its definition, and which end of a link the owner stands on. */
    private record LinkSide(long definitionId, boolean symmetric, boolean ownerIsSource) {
    }

    // ========== Catalog ==========

    /** What a list of {@code entityType} accepts on {@code field}'s column; null for nothing. */
    @Nullable
    public FieldResource.Query capabilityOf(Class<?> entityType, CustomField field) {
        Target target = resolve(entityType, field);
        if (target == null) return null;
        boolean filterable = !target.kind.isReference() || target.idFilterable;
        return new FieldResource.Query(target.kind.sortable(), filterable ? target.kind.filterOp : null);
    }

    /**
     * The catalog entry of {@code field} for {@code entityType}: how a list can sort and filter its
     * column, and whether it can be edited at all — a list has no layout to read that from, unlike
     * the fiche.
     */
    public FieldResource withQuery(FieldResource resource, Class<?> entityType, CustomField field) {
        FieldResource withCapability = resource.withQuery(capabilityOf(entityType, field));
        FieldResource maybeReadOnly = isReadOnly(entityType, field) ? withCapability.withReadOnly(true) : withCapability;
        FieldRules rules = rulesOf(entityType, field);
        return rules.isEmpty() ? maybeReadOnly : maybeReadOnly.withRules(FieldRulesJson.toWire(rules));
    }

    /**
     * The conditional rules {@code field}'s column carries in the fixed details form of {@code entityType}
     * (projects, places: their forms are not configurable). They travel with the catalog entry, the way
     * {@code readOnly} does. {@link FieldRules#NONE} for a configurable table, whose rules are per type.
     */
    FieldRules rulesOf(Class<?> entityType, CustomField field) {
        if (field == null || field.getId() == null) return FieldRules.NONE;
        if (TABLES.containsKey(entityType)) {
            // Rules of a configurable table belong to a (project, type): the list reads them from the
            // project's per-type forms, not from one catalog entry shared by every type.
            return FieldRules.NONE;
        }
        FormUiDto form = DETAILS_FORMS.get(entityType);
        if (form == null || form.getLayout() == null) return FieldRules.NONE;
        return form.getLayout().stream()
                .flatMap(panel -> panel.getRows().stream())
                .flatMap(row -> row.getColumns().stream())
                .filter(column -> column.getField() != null && field.getId().equals(column.getField().getId()))
                .map(CustomColUiDto::getRules)
                .filter(rules -> rules != null && !rules.isEmpty())
                .findFirst()
                .orElse(FieldRules.NONE);
    }

    /**
     * Whether {@code field} is read-only on {@code entityType}: a column its details form marks
     * readOnly (the project a recording unit, find, phase or container belongs to, a full
     * identifier…) — the same flag the fiche honours, so a list cell can't edit what the fiche can't.
     */
    static boolean isReadOnly(Class<?> entityType, CustomField field) {
        if (field == null || field.getId() == null) return false;
        // A configurable relation is written through /relation-links, never through the owner's answers.
        if (Hibernate.unproxy(field) instanceof CustomFieldRelation) return true;
        ConfigurableTable table = TABLES.get(entityType);
        if (table != null) {
            return SystemFieldCatalog.specOf(table, field.getId()).map(SystemFieldSpec::readOnly).orElse(false);
        }
        FormUiDto form = DETAILS_FORMS.get(entityType);
        if (form == null || form.getLayout() == null) return false;
        return form.getLayout().stream()
                .flatMap(panel -> panel.getRows().stream())
                .flatMap(row -> row.getColumns().stream())
                .anyMatch(column -> column.isReadOnly() && column.getField() != null
                        && field.getId().equals(column.getField().getId()));
    }

    /** The configurable tables: their system fields (and intrinsic flags) come from the catalog. */
    private static final Map<Class<?>, ConfigurableTable> TABLES = Map.of(
            RecordingUnit.class, ConfigurableTable.UE,
            Specimen.class, ConfigurableTable.MOBILIER,
            Phase.class, ConfigurableTable.PHASE,
            Container.class, ConfigurableTable.CONTENANT,
            Document.class, ConfigurableTable.DOCUMENT,
            Study.class, ConfigurableTable.STUDY);

    private static final Map<Class<?>, FormUiDto> DETAILS_FORMS = Map.of(
            ActionUnit.class, ActionUnit.DETAILS_FORM,
            SpatialUnit.class, SpatialUnit.DETAILS_FORM);

    // ========== Query ==========

    /**
     * The {@link FieldQuery} for a list of {@code entityType}: every filter ANDed, then the sort.
     * A field that doesn't exist, can't be queried on this entity, or a value that doesn't parse
     * for it, is a 400.
     */
    @Transactional(readOnly = true)
    public <E> FieldQuery toFieldQuery(Class<E> entityType, FieldListQuery query) {
        return buildFieldQuery(entityType, query);
    }

    private <E> FieldQuery buildFieldQuery(Class<E> entityType, FieldListQuery query) {
        if (query == null || query.isEmpty()) return FieldQuery.NONE;
        List<Specification<E>> parts = new ArrayList<>();
        for (Map.Entry<Long, FieldListQuery.Criterion> e : query.filters().entrySet()) {
            Target target = requireTarget(entityType, e.getKey());
            parts.add(filter(target, e.getKey(), e.getValue()));
        }
        boolean ordered = false;
        Specification<E> ordering = null;
        if (query.sortFieldId() != null) {
            Target target = requireTarget(entityType, query.sortFieldId());
            if (!target.kind.sortable()) throw badRequest(LE_CHAMP + query.sortFieldId() + " ne se trie pas");
            ordering = orderBy(target, query.sortFieldId(), query.ascending(), query.lang());
            parts.add(ordering);
            ordered = true;
        }
        Specification<E> spec = (root, q, cb) -> null;
        for (Specification<E> part : parts) spec = spec.and(part);
        return new FieldQuery(spec, ordered, !query.filters().isEmpty(), ordering);
    }

    /**
     * The field-keyed part of a list request ({@link FieldListQuery}) for a list of
     * {@code entityType}, ready for its service; {@link FieldQuery#NONE} when there is none.
     */
    @Transactional(readOnly = true)
    public FieldQuery parse(Class<?> entityType, @Nullable MultiValueMap<String, String> queryParams,
                            @Nullable String sort, @Nullable String acceptLanguage) {
        FieldListQuery query = FieldListQuery.parse(queryParams, sort, ProjectApiService.primaryAcceptLanguage(acceptLanguage));
        return query.isEmpty() ? FieldQuery.NONE : buildFieldQuery(entityType, query);
    }

    private Target requireTarget(Class<?> entityType, long fieldId) {
        Optional<ListOnlyColumn> listOnly = ListOnlyColumn.ofFieldId(fieldId);
        Target target = listOnly.isPresent()
                ? resolveSystem(entityType, listOnly.get().attributeOf(entityType))
                : resolve(entityType, requireField(entityType, fieldId));
        if (target == null) throw badRequest(LE_CHAMP + fieldId + " ne se trie ni ne se filtre sur cette liste");
        return target;
    }

    /**
     * The form field {@code fieldId} names: one of the entity's system fields declared in code, else a
     * {@code custom_field} row; an unknown id is a 400.
     */
    private CustomField requireField(Class<?> entityType, long fieldId) {
        return Optional.ofNullable(SYSTEM_FIELDS.get(entityType)).map(fields -> fields.get(fieldId))
                .or(() -> customFieldRepository.findById(fieldId).map(f -> (CustomField) Hibernate.unproxy(f)))
                .orElseThrow(() -> badRequest("Champ inconnu : " + fieldId));
    }

    // ========== Resolution ==========

    @Nullable
    private Target resolve(Class<?> entityType, CustomField field) {
        if (Boolean.TRUE.equals(field.getIsSystemField())) {
            return field.getValueBinding() == null ? null : resolveSystem(entityType, field.getValueBinding());
        }
        return resolveAdditional(entityType, field);
    }

    @Nullable
    private Target resolveSystem(Class<?> entityType, String formBinding) {
        String binding = entityAttribute(entityType, formBinding);
        if (stratigraphicPosition(entityType, binding) != null) {
            // Not a property: a unit's relationships are split over relationshipsAsUnit1/2.
            return new Target(true, binding, Kind.REFERENCES, RecordingUnit.class, null, false, "fullIdentifier", true);
        }
        ManagedType<?> type = managedType(entityType);
        if (type == null) return null;
        Attribute<?, ?> attribute;
        try {
            attribute = type.getAttribute(binding);
        } catch (IllegalArgumentException notMapped) {
            return null;
        }
        return switch (attribute.getPersistentAttributeType()) {
            case BASIC -> {
                Kind kind = scalarKind(attribute.getJavaType());
                yield kind == null ? null : new Target(true, binding, kind, attribute.getJavaType(), null, false, null, false);
            }
            case MANY_TO_ONE, ONE_TO_ONE -> MeasurementAnswer.class.equals(attribute.getJavaType())
                    // A measurement (an altitude): compared and sorted as a number, in the base unit.
                    ? new Target(true, binding, Kind.NUMBER, Double.class, null, false, null, false, MEASUREMENT_VALUE)
                    : reference(true, binding, Kind.REFERENCE, attribute.getJavaType(), null);
            case MANY_TO_MANY, ONE_TO_MANY -> {
                Class<?> element = ((PluralAttribute<?, ?, ?>) attribute).getElementType().getJavaType();
                yield reference(true, binding, Kind.REFERENCES, element, null);
            }
            default -> null;
        };
    }

    @Nullable
    private Target resolveAdditional(Class<?> entityType, CustomField field) {
        if (!ANSWER_OWNERS.containsKey(entityType)) return null;
        if (Hibernate.unproxy(field) instanceof CustomFieldRelation) return relationTarget(field);
        Function<Void, ? extends CustomFieldAnswer> creator =
                CustomFieldAnswerFactory.ANSWER_ENTITY_CREATORS.get(Hibernate.getClass(field));
        if (creator == null) return null;
        return answerTarget(field, creator.apply(null).getClass());
    }

    /** The links of a relation field's definition, from its side: filtered and sorted like a collection of references. */
    @Nullable
    private Target relationTarget(CustomField field) {
        LinkSide side = linkSideOf(field.getId());
        return side == null ? null
                : new Target(false, RELATION_LINK, Kind.REFERENCES, Object.class, null, false, null, true, null, side);
    }

    /** An additional field stored as answers of {@code answerClass}. */
    @Nullable
    private Target answerTarget(CustomField field, Class<? extends CustomFieldAnswer> answerClass) {
        boolean single = FieldAnswerWireService.answerTypeOf(field).startsWith("SELECT_ONE");
        Kind refKind = single ? Kind.REFERENCE : Kind.REFERENCES;

        // A geometry answer is stored as WKT text, yet is no text to search in.
        if (CustomFieldGeometry.class.isAssignableFrom(Hibernate.getClass(field))) return scalarAnswer(answerClass, Kind.GEOMETRY, String.class);
        if (CustomFieldAnswerText.class.isAssignableFrom(answerClass)) return scalarAnswer(answerClass, Kind.TEXT, String.class);
        if (CustomFieldAnswerInteger.class.isAssignableFrom(answerClass)) return scalarAnswer(answerClass, Kind.NUMBER, Integer.class);
        if (CustomFieldAnswerDecimal.class.isAssignableFrom(answerClass)
                || CustomFieldAnswerMeasurement.class.isAssignableFrom(answerClass)) return scalarAnswer(answerClass, Kind.NUMBER, Double.class);
        if (CustomFieldAnswerDateTime.class.isAssignableFrom(answerClass)) return scalarAnswer(answerClass, Kind.DATE, LocalDateTime.class);
        if (CustomFieldAnswerBoolean.class.isAssignableFrom(answerClass)) return scalarAnswer(answerClass, Kind.BOOLEAN, Boolean.class);
        if (CustomFieldAnswerSelectConcept.class.isAssignableFrom(answerClass)) return reference(false, "concepts", refKind, Concept.class, answerClass);
        if (CustomFieldAnswerSelectPerson.class.isAssignableFrom(answerClass)) return reference(false, "persons", refKind, targetOf(answerClass, "persons"), answerClass);
        if (CustomFieldAnswerSpatialUnit.class.isAssignableFrom(answerClass)) return reference(false, "spatialUnits", refKind, targetOf(answerClass, "spatialUnits"), answerClass);
        if (CustomFieldAnswerActionUnit.class.isAssignableFrom(answerClass)) return reference(false, "actionUnits", refKind, targetOf(answerClass, "actionUnits"), answerClass);
        return null;
    }

    private static Target scalarAnswer(Class<? extends CustomFieldAnswer> answerClass, Kind kind, Class<?> valueType) {
        return new Target(false, "value", kind, valueType, answerClass, false, null, false);
    }

    @Nullable
    private Target reference(boolean system, String attribute, Kind kind, @Nullable Class<?> targetType,
                             @Nullable Class<? extends CustomFieldAnswer> answerClass) {
        if (targetType == null) return null;
        if (Concept.class.isAssignableFrom(targetType)) {
            return new Target(system, attribute, kind, targetType, answerClass, true, null, true);
        }
        ManagedType<?> target = managedType(targetType);
        if (target == null) return null;
        String label = LABEL_ATTRIBUTES.stream().filter(name -> hasAttribute(target, name)).findFirst().orElse(null);
        // A multi-valued column sorts by its number of values and needs no label; a single one does.
        if (label == null && kind == Kind.REFERENCE) return null;
        boolean numericId = target instanceof EntityType<?> et && et.hasSingleIdAttribute()
                && Number.class.isAssignableFrom(boxed(et.getIdType().getJavaType()));
        return new Target(system, attribute, kind, targetType, answerClass, false, label, numericId);
    }

    @Nullable
    private Class<?> targetOf(Class<?> answerClass, String collection) {
        ManagedType<?> type = managedType(answerClass);
        if (type == null) return null;
        try {
            return ((PluralAttribute<?, ?, ?>) type.getAttribute(collection)).getElementType().getJavaType();
        } catch (IllegalArgumentException | ClassCastException e) {
            return null;
        }
    }

    @Nullable
    private ManagedType<?> managedType(Class<?> type) {
        try {
            return entityManager.getMetamodel().managedType(type);
        } catch (IllegalArgumentException notManaged) {
            return null;
        }
    }

    private static boolean hasAttribute(ManagedType<?> type, String name) {
        try {
            type.getAttribute(name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Nullable
    private static Kind scalarKind(Class<?> javaType) {
        Class<?> type = boxed(javaType);
        if (type.isEnum()) return Kind.CHOICE;
        if (Geometry.class.isAssignableFrom(type)) return Kind.GEOMETRY;
        if (String.class.equals(type)) return Kind.TEXT;
        if (Number.class.isAssignableFrom(type)) return Kind.NUMBER;
        if (Temporal.class.isAssignableFrom(type) || Date.class.isAssignableFrom(type)) return Kind.DATE;
        return null;
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        return type;
    }

    /**
     * Where the units of a recording unit's stratigraphic field stand relative to it — anterior,
     * posterior or synchronous — or null for any other field. These are not properties: a unit's
     * relationships are split over two JPA collections, whichever side it is on.
     */
    @Nullable
    private static String stratigraphicPosition(Class<?> entityType, String binding) {
        return RelationField.of(entityType, binding).map(RelationField::stratigraphicPosition).orElse(null);
    }

    /**
     * The links of the stratigraphy (docs/reprise-stratigraphie.md) where {@code unit}'s other unit stands at
     * {@code position}, and, when {@code otherIds} is given, is one of those: the order goes from the posterior unit
     * to the anterior one, the synchrony either way.
     */
    private static Predicate stratigraphicPredicate(CriteriaBuilder cb, Root<RelationLink> link,
                                                    Path<?> unit, String position, @Nullable List<Long> otherIds) {
        Path<Object> sourceField = link.get("definition").get("sourceField").get("id");
        Predicate units = cb.and(cb.equal(link.get("sourceKind"), RelationEndKind.RECORDING_UNIT),
                cb.equal(link.get("targetKind"), RelationEndKind.RECORDING_UNIT));
        Predicate ownerIsSource = cb.and(cb.equal(link.get(SOURCE_ID), unit.get("id")), otherIs(cb, link, TARGET_ID, otherIds));
        Predicate ownerIsTarget = cb.and(cb.equal(link.get(TARGET_ID), unit.get("id")), otherIs(cb, link, SOURCE_ID, otherIds));
        Predicate order = cb.equal(sourceField, RelationField.Stratigraphy.ORDER_SOURCE_FIELD);
        return switch (position) {
            // The other unit is posterior to the owner: the owner is the order's target.
            case "posterior" -> cb.and(units, order, ownerIsTarget);
            case "anterior" -> cb.and(units, order, ownerIsSource);
            default -> cb.and(units, cb.equal(sourceField, RelationField.Stratigraphy.SYNCHRONY_FIELD),
                    cb.or(ownerIsSource, ownerIsTarget));
        };
    }

    /** The side of a configurable relation a field is, or null for a relation field no definition uses yet. */
    @Nullable
    private LinkSide linkSideOf(long fieldId) {
        return entityManager.createQuery(
                        "select d from RelationDefinition d where d.sourceField.id = :field or d.targetField.id = :field",
                        RelationDefinition.class)
                .setParameter("field", fieldId)
                .getResultStream().findFirst()
                .map(d -> new LinkSide(d.getId(), d.isSymmetric(), d.getSourceField().getId().equals(fieldId)))
                .orElse(null);
    }

    /**
     * The links of a configurable relation from {@code owner}'s side (either side for a symmetric relation), and,
     * when {@code otherIds} is given, to one of those.
     */
    private static Predicate relationLinkPredicate(CriteriaBuilder cb, Root<RelationLink> link, Root<?> owner,
                                                   LinkSide side, @Nullable List<Long> otherIds) {
        RelationEndKind ownerKind = DefinitionRelation.kindOfOwner(owner.getJavaType());
        Predicate ofDefinition = cb.equal(link.get("definition").get("id"), side.definitionId());
        Predicate fromSource = cb.and(cb.equal(link.get("sourceKind"), ownerKind),
                cb.equal(link.get(SOURCE_ID), owner.get("id")),
                otherIds == null ? cb.conjunction() : link.get(TARGET_ID).in(otherIds));
        Predicate fromTarget = cb.and(cb.equal(link.get("targetKind"), ownerKind),
                cb.equal(link.get(TARGET_ID), owner.get("id")),
                otherIds == null ? cb.conjunction() : link.get(SOURCE_ID).in(otherIds));
        if (side.symmetric()) return cb.and(ofDefinition, cb.or(fromSource, fromTarget));
        return cb.and(ofDefinition, side.ownerIsSource() ? fromSource : fromTarget);
    }

    private static Predicate otherIs(CriteriaBuilder cb, Root<RelationLink> link, String otherEnd,
                                     @Nullable List<Long> otherIds) {
        return otherIds == null ? cb.conjunction() : link.get(otherEnd).in(otherIds);
    }

    // ========== Filter ==========

    public static final String FILTRE_ATTENDU_PLAGE = "Filtre attendu en plage (.from/.to) : f.";
    public static final String FILTRE_PLAGE_IMPOSSIBLE = "Filtre en plage impossible sur une référence : f.";
    private static final String GEOMETRIE_NON_TRIABLE = "Une géométrie ne se trie pas";

    private static final Map<FieldQueryService.Kind, String> MESSAGE_ERREUR = Map.of(
            Kind.TEXT, "Filtre en plage impossible sur un champ texte : f.",
            Kind.NUMBER, FILTRE_ATTENDU_PLAGE,
            Kind.DATE, FILTRE_ATTENDU_PLAGE,
            Kind.BOOLEAN, "Filtre en plage impossible sur un champ oui/non : f.",
            Kind.CHOICE, "Filtre en plage impossible sur une liste de valeurs : f.",
            Kind.REFERENCE, FILTRE_PLAGE_IMPOSSIBLE,
            Kind.REFERENCES, FILTRE_PLAGE_IMPOSSIBLE,
            Kind.GEOMETRY, "Filtre en plage impossible sur une géométrie : f."
    );

    /**
     * The operator the filter names, else its kind's default; one the kind cannot apply is a 400.
     */
    private static FilterOperator operatorOf(Target target, long fieldId, FieldListQuery.Criterion criterion) {
        FilterOperator operator = criterion.operator() == null ? target.kind.defaultOperator : criterion.operator();
        if (!target.kind.accepts(operator)) {
            throw badRequest("Opérateur " + operator.wireName() + " impossible sur ce champ : f." + fieldId);
        }
        return operator;
    }

    /**
     * A number or date filter is a range, any other a value — unless the operator takes no value at
     * all; a filter on reference ids needs a target filterable by id.
     */
    private static void requireFilterShape(Target target, long fieldId, FieldListQuery.Criterion criterion,
                                           FilterOperator operator) {
        if (operator.takesValues() && criterion.isRange() != target.kind.isRange()) {
            throw badRequest(MESSAGE_ERREUR.get(target.kind) + fieldId);
        }
        if (operator.match() == Match.ANY_OF && target.kind.isReference() && !target.idFilterable) {
            throw badRequest(LE_CHAMP + fieldId + " ne se filtre pas");
        }
    }

    private <E> Specification<E> filter(Target target, long fieldId, FieldListQuery.Criterion criterion) {
        FilterOperator operator = operatorOf(target, fieldId, criterion);
        requireFilterShape(target, fieldId, criterion, operator);
        // Parsed up front, so a bad value is a 400 before any query runs.
        Filter parsed = parseFilter(target, fieldId, criterion, operator.match());

        return (root, query, cb) -> {
            Predicate matching = matching(target, fieldId, parsed, root, query, cb);
            // The rows the match leaves out, empty values included: each layout of matching() keeps
            // a row without a value out of its match, so that NOT brings it back.
            return operator.negated() ? cb.not(matching) : matching;
        };
    }

    /** The values a filter compares the column with, parsed for the column's kind and the operator's match. */
    private static Filter parseFilter(Target target, long fieldId, FieldListQuery.Criterion criterion, Match match) {
        List<String> values = criterion.values();
        boolean anyOf = match == Match.ANY_OF;
        List<Long> ids = anyOf && target.kind.isReference() ? parseIds(fieldId, values) : List.of();
        List<Boolean> flags = anyOf && target.kind == Kind.BOOLEAN ? parseBooleans(fieldId, values) : List.of();
        List<Object> choices = anyOf && target.kind == Kind.CHOICE ? parseChoices(target.valueType, fieldId, values) : List.of();
        Object from = bound(target, fieldId, criterion.from(), true);
        Object to = bound(target, fieldId, criterion.to(), false);
        return new Filter(match, FilterPredicates.lowercased(values), ids, flags, choices, from, to);
    }

    /** The rows whose {@code target} column matches {@code parsed}, before any negation. */
    private static Predicate matching(Target target, long fieldId, Filter parsed, Root<?> root,
                                      CriteriaQuery<?> query, CriteriaBuilder cb) {
        if (!target.system && RELATION_LINK.equals(target.attribute)) {
            // Linked to at least one of them (to any record at all, for "not empty") by the field's relation.
            Subquery<Integer> linked = query.subquery(Integer.class);
            Root<RelationLink> link = linked.from(RelationLink.class);
            List<Long> otherIds = parsed.match() == Match.PRESENT ? null : parsed.ids();
            linked.select(cb.literal(1)).where(relationLinkPredicate(cb, link, root, target.links(), otherIds));
            return cb.exists(linked);
        }
        String position = stratigraphicPosition(root.getJavaType(), target.attribute);
        if (position != null) {
            // Related to at least one of them (to any unit at all, for "not empty"), at that position.
            Subquery<Integer> related = query.subquery(Integer.class);
            Root<RelationLink> rel = related.from(RelationLink.class);
            List<Long> otherIds = parsed.match() == Match.PRESENT ? null : parsed.ids();
            related.select(cb.literal(1)).where(stratigraphicPredicate(cb, rel, root, position, otherIds));
            return cb.exists(related);
        }
        if (target.system && target.kind == Kind.REFERENCES) {
            // Has at least one of them: an EXISTS over the collection, since a join would
            // repeat the row once per matching element.
            Subquery<Integer> elements = query.subquery(Integer.class);
            Join<?, ?> element = correlate(elements, root).join(target.attribute);
            elements.select(cb.literal(1)).where(valuePredicate(target, element, parsed, cb));
            return cb.exists(elements);
        }
        if (target.system) {
            // NULL compares as unknown, which NOT would leave unknown: a row without a value must
            // be plainly out of the match for a negation to bring it back.
            Expression<?> value = systemValue(root, target);
            return cb.and(cb.isNotNull(value), valuePredicate(target, value, parsed, cb));
        }
        Subquery<Integer> answers = query.subquery(Integer.class);
        Root<? extends CustomFieldAnswer> answer = answers.from(target.answerClass);
        Expression<?> value = target.kind.isReference()
                ? answer.join(target.attribute)
                : answer.get(target.attribute);
        answers.select(cb.literal(1)).where(
                ownedBy(answer, root, fieldId, cb),
                valuePredicate(target, value, parsed, cb));
        return cb.exists(answers);
    }

    /**
     * A system field's value on the listed entity: its attribute, or — for a measurement — the
     * measurement's value through a LEFT join, so a unit without one is empty, not dropped.
     */
    private static Path<?> systemValue(Root<?> root, Target target) {
        return target.nested == null
                ? root.get(target.attribute)
                : root.join(target.attribute, JoinType.LEFT).get(target.nested);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Root<?> correlate(Subquery<?> subquery, Root<?> root) {
        return subquery.correlate((Root) root);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate valuePredicate(Target target, Expression<?> value, Filter filter, CriteriaBuilder cb) {
        if (filter.match() == Match.PRESENT) return FilterPredicates.hasValue(value, String.class.equals(target.valueType), cb);
        List<Long> ids = filter.ids();
        Object from = filter.from();
        Object to = filter.to();
        return switch (target.kind) {
            case BOOLEAN -> value.in(filter.flags());
            case CHOICE -> value.in(filter.choices());
            case TEXT -> FilterPredicates.textMatches(filter.match(), (Expression<String>) value, filter.texts(), cb);
            case NUMBER, DATE -> {
                List<Predicate> bounds = new ArrayList<>();
                if (from != null) bounds.add(cb.greaterThanOrEqualTo((Expression<Comparable>) value, (Comparable) from));
                if (to != null) bounds.add(target.kind == Kind.DATE
                        ? cb.lessThan((Expression<Comparable>) value, (Comparable) to)
                        : cb.lessThanOrEqualTo((Expression<Comparable>) value, (Comparable) to));
                yield cb.and(bounds.toArray(Predicate[]::new));
            }
            case REFERENCE, REFERENCES -> ((Path<?>) value).get("id").in(ids);
            // Kind.GEOMETRY accepts nothing but PRESENT, handled above.
            case GEOMETRY -> throw new IllegalStateException("Une géométrie ne se compare pas à une valeur");
        };
    }

    /**
     * What a filter compares a column with, parsed once for the kind of the column: the operator's
     * match, and the texts (lowercased), ids, flags, constants or bounds it takes.
     */
    private record Filter(Match match, List<String> texts, List<Long> ids, List<Boolean> flags, List<Object> choices,
                          @Nullable Object from, @Nullable Object to) {
    }

    // ========== Sort ==========

    private <E> Specification<E> orderBy(Target target, long fieldId, boolean ascending, String lang) {
        return (root, query, cb) -> {
            // The count query shares the specification; Spring Data drops its order anyway.
            if (isCountQuery(query)) return null;
            Expression<?> key = sortKey(target, fieldId, root, query, cb, lang);
            HibernateCriteriaBuilder hcb = (HibernateCriteriaBuilder) cb;
            // Empty values last in both directions: an empty cell is never "the smallest value".
            Order order = ascending ? hcb.asc(key, false) : hcb.desc(key, false);
            query.orderBy(order, cb.asc(root.get("id")));
            return null;
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Expression<?> sortKey(Target target, long fieldId, Root<?> root, CriteriaQuery<?> query,
                                  CriteriaBuilder cb, String lang) {
        if (!target.system && RELATION_LINK.equals(target.attribute)) {
            // By how many records the field links to, as a stratigraphic side is.
            Subquery<Long> count = query.subquery(Long.class);
            Root<RelationLink> link = count.from(RelationLink.class);
            count.select(cb.count(link)).where(relationLinkPredicate(cb, link, root, target.links(), null));
            return count;
        }
        String position = stratigraphicPosition(root.getJavaType(), target.attribute);
        if (position != null) {
            Subquery<Long> count = query.subquery(Long.class);
            Root<RelationLink> rel = count.from(RelationLink.class);
            count.select(cb.count(rel)).where(stratigraphicPredicate(cb, rel, root, position, null));
            return count;
        }
        if (target.system) {
            return systemSortKey(target, root, query, cb, lang);
        }
        return additionalSortKey(target, fieldId, root, query, cb, lang);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Expression<?> systemSortKey(Target target, Root<?> root, CriteriaQuery<?> query, CriteriaBuilder cb, String lang) {
        return switch (target.kind) {
            case TEXT -> cb.lower(root.<String>get(target.attribute));
            case NUMBER, DATE, BOOLEAN -> systemValue(root, target);
            case CHOICE -> declarationRank(cb, root.get(target.attribute), target.valueType);
            case REFERENCE -> target.concept
                    ? conceptLabel(query, cb, root.get(target.attribute), lang)
                    : root.join(target.attribute, JoinType.LEFT).get(target.labelAttribute);
            case REFERENCES -> cb.size((Expression<Collection<?>>) (Expression) root.get(target.attribute));
            case GEOMETRY -> throw new IllegalStateException(GEOMETRIE_NON_TRIABLE);
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Expression<?> additionalSortKey(Target target, long fieldId, Root<?> root, CriteriaQuery<?> query, CriteriaBuilder cb, String lang) {
        return switch (target.kind) {
            case BOOLEAN -> {
                // max() has no boolean form: sort on 1 (yes) / 0 (no), and no value stays empty.
                Subquery<Integer> sub = query.subquery(Integer.class);
                Root<? extends CustomFieldAnswer> answer = sub.from(target.answerClass);
                Expression<Boolean> flag = answer.get(target.attribute);
                sub.select(cb.greatest(cb.<Integer>selectCase().when(cb.isTrue(flag), 1).when(cb.isFalse(flag), 0)))
                        .where(ownedBy(answer, root, fieldId, cb));
                yield sub;
            }
            case TEXT, NUMBER, DATE -> {
                Subquery<Comparable> sub = query.subquery(Comparable.class);
                Root<? extends CustomFieldAnswer> answer = sub.from(target.answerClass);
                Expression<Comparable> value = target.kind == Kind.TEXT
                        ? (Expression) cb.lower(answer.get(target.attribute))
                        : answer.get(target.attribute);
                sub.select(cb.greatest(value)).where(ownedBy(answer, root, fieldId, cb));
                yield sub;
            }
            case REFERENCE -> {
                Subquery<String> sub = query.subquery(String.class);
                Root<? extends CustomFieldAnswer> answer = sub.from(target.answerClass);
                Join<?, ?> referenced = answer.join(target.attribute);
                if (target.concept) {
                    Root<ConceptPrefLabel> label = sub.from(ConceptPrefLabel.class);
                    sub.select(cb.least(label.<String>get("label"))).where(
                            ownedBy(answer, root, fieldId, cb),
                            cb.equal(label.get("concept"), referenced),
                            cb.equal(label.get("langCode"), lang));
                } else {
                    sub.select(cb.least(cb.lower(referenced.get(target.labelAttribute))))
                            .where(ownedBy(answer, root, fieldId, cb));
                }
                yield sub;
            }
            case REFERENCES -> {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<? extends CustomFieldAnswer> answer = sub.from(target.answerClass);
                Join<?, ?> referenced = answer.join(target.attribute);
                sub.select(cb.count(referenced)).where(ownedBy(answer, root, fieldId, cb));
                yield sub;
            }
            // An additional field's answer is never an enum: only system properties resolve to CHOICE.
            case CHOICE -> throw new IllegalStateException("Le champ additionnel " + fieldId + " n'a pas de liste de valeurs");
            case GEOMETRY -> throw new IllegalStateException(GEOMETRIE_NON_TRIABLE);
        };
    }

    /**
     * The rank of an enum column's value in its type's declaration order — the workflow order of a
     * validation status (en cours, terminé, validé, annulé) rather than the alphabetical order of the
     * stored names.
     */
    private static Expression<Integer> declarationRank(CriteriaBuilder cb, Expression<?> value, Class<?> enumType) {
        CriteriaBuilder.Case<Integer> rank = cb.selectCase();
        for (Object constant : enumType.getEnumConstants()) {
            rank = rank.when(cb.equal(value, constant), ((Enum<?>) constant).ordinal());
        }
        return rank.otherwise(cb.nullLiteral(Integer.class));
    }

    private static Subquery<String> conceptLabel(CriteriaQuery<?> query, CriteriaBuilder cb, Expression<?> concept, String lang) {
        Subquery<String> sub = query.subquery(String.class);
        Root<ConceptPrefLabel> label = sub.from(ConceptPrefLabel.class);
        sub.select(cb.least(cb.lower(label.get("label")))).where(
                cb.equal(label.get("concept"), concept),
                cb.equal(label.get("langCode"), lang));
        return sub;
    }

    /** The answer rows of one field for the listed entity. */
    private static Predicate ownedBy(Root<? extends CustomFieldAnswer> answer, Root<?> root, long fieldId, CriteriaBuilder cb) {
        String owner = ANSWER_OWNERS.get(root.getJavaType());
        return cb.and(
                cb.equal(answer.get("formConfigAnswer").get(owner), root),
                cb.equal(answer.get("customField").get("id"), fieldId));
    }

    private static boolean isCountQuery(CriteriaQuery<?> query) {
        Class<?> resultType = query.getResultType();
        return Long.class.equals(resultType) || long.class.equals(resultType);
    }

    // ========== Values ==========

    private static List<Boolean> parseBooleans(long fieldId, List<String> values) {
        List<Boolean> flags = new ArrayList<>(values.size());
        for (String value : values) {
            if ("true".equalsIgnoreCase(value)) flags.add(Boolean.TRUE);
            else if ("false".equalsIgnoreCase(value)) flags.add(Boolean.FALSE);
            else throw badRequest("Booléen invalide pour f." + fieldId + " : " + value + " (true ou false attendu)");
        }
        return flags;
    }

    /**
     * The constants of {@code enumType} named by {@code values}, case-insensitively; an unknown name is a 400.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Object> parseChoices(Class<?> enumType, long fieldId, List<String> values) {
        List<Object> choices = new ArrayList<>(values.size());
        for (String value : values) {
            try {
                choices.add(Enum.valueOf((Class<? extends Enum>) enumType, value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw badRequest("Valeur inconnue pour f." + fieldId + " : " + value);
            }
        }
        return choices;
    }

    private static List<Long> parseIds(long fieldId, List<String> values) {
        List<Long> ids = new ArrayList<>(values.size());
        for (String value : values) {
            try {
                ids.add(Long.parseLong(value));
            } catch (NumberFormatException e) {
                throw badRequest("Identifiant invalide pour f." + fieldId + " : " + value);
            }
        }
        return ids;
    }

    /**
     * A range bound in the column's own Java type. Numbers are rounded inwards onto an integer
     * column; a bare date {@code to} is exclusive of the next day, so "to 2024-05-02" keeps the whole
     * of the 2nd.
     */
    @Nullable
    private static Object bound(Target target, long fieldId, @Nullable String raw, boolean lower) {
        if (raw == null) return null;
        Class<?> type = boxed(target.valueType);
        if (target.kind == Kind.NUMBER) return numberBound(type, fieldId, raw, lower);
        if (target.kind == Kind.DATE) return dateBound(type, fieldId, raw, lower);
        throw badRequest("Filtre en plage impossible : f." + fieldId);
    }

    private static Object numberBound(Class<?> type, long fieldId, String raw, boolean lower) {
        double d;
        try {
            d = Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException e) {
            throw badRequest("Nombre invalide pour f." + fieldId + " : " + raw);
        }
        double rounded = lower ? Math.ceil(d) : Math.floor(d);
        if (Integer.class.equals(type)) return (int) rounded;
        if (Long.class.equals(type)) return (long) rounded;
        if (Short.class.equals(type)) return (short) rounded;
        if (Float.class.equals(type)) return (float) d;
        if (BigDecimal.class.equals(type)) return BigDecimal.valueOf(d);
        return d;
    }

    private static Object dateBound(Class<?> type, long fieldId, String raw, boolean lower) {
        LocalDateTime utc;
        try {
            long extraDays = lower ? 0 : 1;
            utc = raw.length() == 10
                    ? LocalDate.parse(raw).plusDays(extraDays).atStartOfDay()
                    : OffsetDateTime.parse(raw).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException e) {
            throw badRequest("Date invalide (ISO-8601 attendu) pour f." + fieldId + " : " + raw);
        }
        if (OffsetDateTime.class.equals(type)) return utc.atOffset(ZoneOffset.UTC);
        if (LocalDate.class.equals(type)) return utc.toLocalDate();
        if (Instant.class.equals(type)) return utc.toInstant(ZoneOffset.UTC);
        if (ZonedDateTime.class.equals(type)) return utc.atZone(ZoneOffset.UTC);
        if (Date.class.isAssignableFrom(type)) return Date.from(utc.toInstant(ZoneOffset.UTC));
        return utc;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
