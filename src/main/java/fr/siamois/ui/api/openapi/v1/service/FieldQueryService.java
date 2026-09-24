package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.ui.form.CustomFieldAnswerFactory;
import fr.siamois.domain.models.form.customfieldanswer.actionunit.CustomFieldAnswerActionCode;
import fr.siamois.domain.models.form.customfieldanswer.actionunit.CustomFieldAnswerActionUnit;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerDateTime;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerDecimal;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerInteger;
import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerText;
import fr.siamois.domain.models.form.customfieldanswer.measurement.CustomFieldAnswerMeasurement;
import fr.siamois.domain.models.form.customfieldanswer.person.CustomFieldAnswerSelectPerson;
import fr.siamois.domain.models.form.customfieldanswer.spatialunit.CustomFieldAnswerSpatialUnit;
import fr.siamois.domain.models.form.customfieldanswer.vocabulary.CustomFieldAnswerSelectConcept;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.dto.FieldQuery;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.ui.api.openapi.v1.request.list.FieldListQuery;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.PluralAttribute;
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
            Container.class, "container");

    /** Where an entity's human label is, in order of preference. */
    private static final List<String> LABEL_ATTRIBUTES =
            List.of("fullIdentifier", "lastname", "name", "title", "identifier", "code");

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
     * null for a concept; {@code idFilterable} is false when the target isn't keyed by a numeric id.
     */
    private record Target(boolean system, String attribute, Kind kind, Class<?> valueType,
                          @Nullable Class<? extends CustomFieldAnswer> answerClass,
                          boolean concept, @Nullable String labelAttribute, boolean idFilterable) {
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

    public FieldResource withQuery(FieldResource resource, Class<?> entityType, CustomField field) {
        return resource.withQuery(capabilityOf(entityType, field));
    }

    // ========== Query ==========

    /**
     * The {@link FieldQuery} for a list of {@code entityType}: every filter ANDed, then the sort.
     * A field that doesn't exist, can't be queried on this entity, or a value that doesn't parse
     * for it, is a 400.
     */
    @Transactional(readOnly = true)
    public <E> FieldQuery toFieldQuery(Class<E> entityType, FieldListQuery query) {
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
        return query.isEmpty() ? FieldQuery.NONE : toFieldQuery(entityType, query);
    }

    private Target requireTarget(Class<?> entityType, long fieldId) {
        CustomField field = customFieldRepository.findById(fieldId)
                .map(f -> (CustomField) Hibernate.unproxy(f))
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
    private Target resolveSystem(Class<?> entityType, String binding) {
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
            case MANY_TO_ONE, ONE_TO_ONE -> reference(true, binding, Kind.REFERENCE, attribute.getJavaType(), null);
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
        if (CustomFieldAnswerActionCode.class.isAssignableFrom(answerClass)) return reference(false, "actionCodes", refKind, targetOf(answerClass, "actionCodes"), answerClass);
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

    // ========== Filter ==========

    private <E> Specification<E> filter(Target target, long fieldId, FieldListQuery.Criterion criterion) {
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
        // Parsed up front, so a bad value is a 400 before any query runs.
        List<Long> ids = target.kind == Kind.REFERENCE || target.kind == Kind.REFERENCES ? parseIds(fieldId, criterion.values()) : List.of();
        Object from = bound(target, fieldId, criterion.from(), true);
        Object to = bound(target, fieldId, criterion.to(), false);
        List<String> texts = criterion.values().stream().map(v -> "%" + v.toLowerCase(Locale.ROOT) + "%").toList();

        return (root, query, cb) -> {
            if (target.system && target.kind == Kind.REFERENCES) {
                // Has at least one of them: an EXISTS over the collection, since a join would
                // repeat the row once per matching element.
                Subquery<Integer> elements = query.subquery(Integer.class);
                Join<?, ?> element = correlate(elements, root).join(target.attribute);
                elements.select(cb.literal(1)).where(element.get("id").in(ids));
                return cb.exists(elements);
            }
            if (target.system) {
                return valuePredicate(target, root.get(target.attribute), texts, ids, from, to, cb);
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
        if (target.system) {
            return switch (target.kind) {
                case TEXT -> cb.lower((Expression<String>) (Expression) root.get(target.attribute));
                case NUMBER, DATE -> root.get(target.attribute);
                case REFERENCE -> target.concept
                        ? conceptLabel(query, cb, root.get(target.attribute), lang)
                        : root.join(target.attribute, JoinType.LEFT).get(target.labelAttribute);
                case REFERENCES -> cb.size((Expression<Collection<?>>) (Expression) root.get(target.attribute));
            };
        }
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
        if (target.kind == Kind.NUMBER) {
            double d;
            try {
                d = Double.parseDouble(raw.replace(',', '.'));
            } catch (NumberFormatException e) {
                throw badRequest("Nombre invalide pour f." + fieldId + " : " + raw);
            }
            if (Integer.class.equals(type)) return (int) (lower ? Math.ceil(d) : Math.floor(d));
            if (Long.class.equals(type)) return (long) (lower ? Math.ceil(d) : Math.floor(d));
            if (Short.class.equals(type)) return (short) (lower ? Math.ceil(d) : Math.floor(d));
            if (Float.class.equals(type)) return (float) d;
            if (BigDecimal.class.equals(type)) return BigDecimal.valueOf(d);
            return d;
        }
        if (target.kind == Kind.DATE) {
            LocalDateTime utc;
            try {
                utc = raw.length() == 10
                        ? LocalDate.parse(raw).plusDays(lower ? 0 : 1).atStartOfDay()
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
        throw badRequest("Filtre en plage impossible : f." + fieldId);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
