package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.form.config.SystemFieldSpec;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.ui.form.fieldsource.PanelFieldSource;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.measurement.MeasurementAnswer;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.ui.form.CustomFieldAnswerFactory;
import fr.siamois.domain.models.form.customfieldanswer.actionunit.CustomFieldAnswerActionUnit;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerDateTime;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerDecimal;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerInteger;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerText;
import fr.siamois.domain.models.form.customfieldanswer.measurement.CustomFieldAnswerMeasurement;
import fr.siamois.domain.models.form.customfieldanswer.person.CustomFieldAnswerSelectPerson;
import fr.siamois.domain.models.form.customfieldanswer.spatialunit.CustomFieldAnswerSpatialUnit;
import fr.siamois.domain.models.form.customfieldanswer.vocabulary.CustomFieldAnswerSelectConcept;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.dto.FieldQuery;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.ui.api.openapi.v1.request.list.FieldListQuery;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import fr.siamois.infrastructure.database.repositories.relation.RelationField;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.PluralAttribute;
import fr.siamois.domain.models.form.rules.FieldRules;
import fr.siamois.domain.models.form.rules.FieldRulesJson;
import fr.siamois.ui.form.dto.CustomColUiDto;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.transaction.annotation.Transactional;
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
            Document.class, "document");

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

    private enum Kind {
        TEXT("contains"), NUMBER("range"), DATE("date-range"), REFERENCE("in"), REFERENCES("in");

        final String filterOp;

        Kind(String filterOp) {
            this.filterOp = filterOp;
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
                          @Nullable String nested) {

        Target(boolean system, String attribute, Kind kind, Class<?> valueType,
               @Nullable Class<? extends CustomFieldAnswer> answerClass,
               boolean concept, @Nullable String labelAttribute, boolean idFilterable) {
            this(system, attribute, kind, valueType, answerClass, concept, labelAttribute, idFilterable, null);
        }
    }

    // ========== Catalog ==========

    /** What a list of {@code entityType} accepts on {@code field}'s column; null for nothing. */
    @Nullable
    public FieldResource.Query capabilityOf(Class<?> entityType, CustomField field) {
        Target target = resolve(entityType, field);
        if (target == null) return null;
        boolean filterable = target.kind != Kind.REFERENCE && target.kind != Kind.REFERENCES || target.idFilterable;
        return new FieldResource.Query(true, filterable ? target.kind.filterOp : null);
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
            Document.class, ConfigurableTable.DOCUMENT);

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
        if (query.sortFieldId() != null) {
            Target target = requireTarget(entityType, query.sortFieldId());
            parts.add(orderBy(target, query.sortFieldId(), query.ascending(), query.lang()));
            ordered = true;
        }
        Specification<E> spec = (root, q, cb) -> null;
        for (Specification<E> part : parts) spec = spec.and(part);
        return new FieldQuery(spec, ordered);
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
        CustomField field = Optional.ofNullable(SYSTEM_FIELDS.get(entityType)).map(fields -> fields.get(fieldId))
                .or(() -> customFieldRepository.findById(fieldId).map(f -> (CustomField) Hibernate.unproxy(f)))
                .orElseThrow(() -> badRequest("Champ inconnu : " + fieldId));
        Target target = resolve(entityType, field);
        if (target == null) throw badRequest("Le champ " + fieldId + " ne se trie ni ne se filtre sur cette liste");
        return target;
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
        if (isStratigraphy(entityType, binding)) {
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
        Function<Void, ? extends CustomFieldAnswer> creator =
                CustomFieldAnswerFactory.ANSWER_ENTITY_CREATORS.get(Hibernate.getClass(field));
        if (creator == null) return null;
        Class<? extends CustomFieldAnswer> answerClass = creator.apply(null).getClass();
        boolean single = FieldAnswerWireService.answerTypeOf(field).startsWith("SELECT_ONE");
        Kind refKind = single ? Kind.REFERENCE : Kind.REFERENCES;

        if (CustomFieldAnswerText.class.isAssignableFrom(answerClass)) return scalarAnswer(answerClass, Kind.TEXT, String.class);
        if (CustomFieldAnswerInteger.class.isAssignableFrom(answerClass)) return scalarAnswer(answerClass, Kind.NUMBER, Integer.class);
        if (CustomFieldAnswerDecimal.class.isAssignableFrom(answerClass)
                || CustomFieldAnswerMeasurement.class.isAssignableFrom(answerClass)) return scalarAnswer(answerClass, Kind.NUMBER, Double.class);
        if (CustomFieldAnswerDateTime.class.isAssignableFrom(answerClass)) return scalarAnswer(answerClass, Kind.DATE, LocalDateTime.class);
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

    /** A recording unit's stratigraphic relationships: one field, two JPA collections. */
    private static boolean isStratigraphy(Class<?> entityType, String binding) {
        return RelationField.of(entityType, binding).filter(r -> r == RelationField.RECORDING_UNIT_STRATIGRAPHY).isPresent();
    }

    // ========== Filter ==========

    /** A text filter is a value, a number or date one a range, a reference one ids of a filterable target. */
    private static void requireFilterShape(Target target, long fieldId, FieldListQuery.Criterion criterion) {
        switch (target.kind) {
            case TEXT -> {
                if (criterion.isRange()) throw badRequest("Filtre en plage impossible sur un champ texte : f." + fieldId);
            }
            case NUMBER, DATE -> {
                if (!criterion.isRange()) throw badRequest("Filtre attendu en plage (.from/.to) : f." + fieldId);
            }
            case REFERENCE, REFERENCES -> {
                if (criterion.isRange()) throw badRequest("Filtre en plage impossible sur une référence : f." + fieldId);
                if (!target.idFilterable) throw badRequest("Le champ " + fieldId + " ne se filtre pas");
            }
        }
    }

    private <E> Specification<E> filter(Target target, long fieldId, FieldListQuery.Criterion criterion) {
        requireFilterShape(target, fieldId, criterion);
        // Parsed up front, so a bad value is a 400 before any query runs.
        List<Long> ids = target.kind == Kind.REFERENCE || target.kind == Kind.REFERENCES ? parseIds(fieldId, criterion.values()) : List.of();
        Object from = bound(target, fieldId, criterion.from(), true);
        Object to = bound(target, fieldId, criterion.to(), false);
        List<String> texts = criterion.values().stream().map(v -> "%" + v.toLowerCase(Locale.ROOT) + "%").toList();

        return (root, query, cb) -> {
            if (isStratigraphy(root.getJavaType(), target.attribute)) {
                // Related to at least one of them, from either side of the relationship.
                Subquery<Integer> related = query.subquery(Integer.class);
                Root<StratigraphicRelationship> rel = related.from(StratigraphicRelationship.class);
                related.select(cb.literal(1)).where(cb.or(
                        cb.and(cb.equal(rel.get("unit1"), root), rel.get("unit2").get("id").in(ids)),
                        cb.and(cb.equal(rel.get("unit2"), root), rel.get("unit1").get("id").in(ids))));
                return cb.exists(related);
            }
            if (target.system && target.kind == Kind.REFERENCES) {
                // Has at least one of them: an EXISTS over the collection, since a join would
                // repeat the row once per matching element.
                Subquery<Integer> elements = query.subquery(Integer.class);
                Join<?, ?> element = correlate(elements, root).join(target.attribute);
                elements.select(cb.literal(1)).where(element.get("id").in(ids));
                return cb.exists(elements);
            }
            if (target.system) {
                return valuePredicate(target, systemValue(root, target), texts, ids, from, to, cb);
            }
            Subquery<Integer> answers = query.subquery(Integer.class);
            Root<? extends CustomFieldAnswer> answer = answers.from(target.answerClass);
            Expression<?> value = target.kind == Kind.REFERENCE || target.kind == Kind.REFERENCES
                    ? answer.join(target.attribute)
                    : answer.get(target.attribute);
            answers.select(cb.literal(1)).where(
                    ownedBy(answer, root, fieldId, cb),
                    valuePredicate(target, value, texts, ids, from, to, cb));
            return cb.exists(answers);
        };
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
    private static Predicate valuePredicate(Target target, Expression<?> value, List<String> texts, List<Long> ids,
                                            @Nullable Object from, @Nullable Object to, CriteriaBuilder cb) {
        return switch (target.kind) {
            case TEXT -> cb.or(texts.stream()
                    .map(t -> cb.like(cb.lower((Expression<String>) value), t))
                    .toArray(Predicate[]::new));
            case NUMBER, DATE -> {
                List<Predicate> bounds = new ArrayList<>();
                if (from != null) bounds.add(cb.greaterThanOrEqualTo((Expression<Comparable>) value, (Comparable) from));
                if (to != null) bounds.add(target.kind == Kind.DATE
                        ? cb.lessThan((Expression<Comparable>) value, (Comparable) to)
                        : cb.lessThanOrEqualTo((Expression<Comparable>) value, (Comparable) to));
                yield cb.and(bounds.toArray(Predicate[]::new));
            }
            case REFERENCE, REFERENCES -> ((Path<?>) value).get("id").in(ids);
        };
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
        if (isStratigraphy(root.getJavaType(), target.attribute)) {
            return cb.sum(cb.size((Expression<Collection<?>>) (Expression) root.get("relationshipsAsUnit1")),
                    cb.size((Expression<Collection<?>>) (Expression) root.get("relationshipsAsUnit2")));
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
            case NUMBER, DATE -> systemValue(root, target);
            case REFERENCE -> target.concept
                    ? conceptLabel(query, cb, root.get(target.attribute), lang)
                    : root.join(target.attribute, JoinType.LEFT).get(target.labelAttribute);
            case REFERENCES -> cb.size((Expression<Collection<?>>) (Expression) root.get(target.attribute));
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Expression<?> additionalSortKey(Target target, long fieldId, Root<?> root, CriteriaQuery<?> query, CriteriaBuilder cb, String lang) {
        return switch (target.kind) {
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
        };
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
