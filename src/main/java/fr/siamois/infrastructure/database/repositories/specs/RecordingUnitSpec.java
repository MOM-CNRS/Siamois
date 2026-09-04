package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;

import java.time.OffsetDateTime;
import java.util.List;

public class RecordingUnitSpec {

    public static final String FULL_IDENTIFIER = "fullIdentifier";
    public static final String AUTHOR_FILTER = "author";
    public static final String MATRIX_FILTER = "matrixColor";
    public static final String ACTION_UNIT_FILTER = "actionUnit";
    public static final String SPATIAL_UNIT_FILTER = "spatialUnit";
    public static final String OPENING_DATE_FILTER = "openingDate";
    public static final String CLOSING_DATE_FILTER = "closingDate";
    public static final String CONTRIBUTORS_FILTER = "contributors";
    public static final String TYPE_FILTER = "type";
    public static final String ID_FILTER = "id";
    public static final String PARENTS_FILTER = "parents";
    public static final String CHILDREN_FILTER = "children";
    public static final String NATURE_FILTER = "geomorphologicalCycle";
    public static final String AGENT_FILTER = "geomorphologicalAgent";
    public static final String INTERPRETATION_FILTER = "normalizedInterpretation";
    public static final String TPQ_FILTER = "tpq";
    public static final String TAQ_FILTER = "taq";
    /** Synthetic sort key: not a real JPA path, resolved via {@link #orderBySpecimenCount(Sort.Direction)}. */
    public static final String SPECIMEN_COUNT_SORT = "specimenCount";
    /** Synthetic sort key: not a real JPA path, resolved via {@link #orderByRelationshipCount(Sort.Direction)}. */
    public static final String RELATIONSHIP_COUNT_SORT = "relationshipCount";
    /** Synthetic sort key: not a real JPA path, resolved via {@link #orderByParentsCount(Sort.Direction)}. */
    public static final String PARENTS_COUNT_SORT = "parentsCount";
    /** Synthetic sort key: not a real JPA path, resolved via {@link #orderByChildrenCount(Sort.Direction)}. */
    public static final String CHILDREN_COUNT_SORT = "childrenCount";
    /** Synthetic sort key: alphabetical on the nature's label, via {@link #orderByConceptLabel}. */
    public static final String NATURE_LABEL_SORT = "geomorphologicalCycleLabel";
    /** Synthetic sort key: alphabetical on the agent's label, via {@link #orderByConceptLabel}. */
    public static final String AGENT_LABEL_SORT = "geomorphologicalAgentLabel";
    /** Synthetic sort key: alphabetical on the interpretation's label, via {@link #orderByConceptLabel}. */
    public static final String INTERPRETATION_LABEL_SORT = "normalizedInterpretationLabel";


    private RecordingUnitSpec() {
        throw new UnsupportedOperationException("Spec should never be instantiated");
    }

    @NonNull
    public static List<String> allColumns() {
        return List.of(
                FULL_IDENTIFIER,
                AUTHOR_FILTER,
                MATRIX_FILTER,
                ACTION_UNIT_FILTER,
                SPATIAL_UNIT_FILTER,
                OPENING_DATE_FILTER,
                CLOSING_DATE_FILTER,
                CONTRIBUTORS_FILTER,
                TYPE_FILTER,
                SPECIMEN_COUNT_SORT,
                RELATIONSHIP_COUNT_SORT,
                PARENTS_COUNT_SORT,
                CHILDREN_COUNT_SORT,
                NATURE_LABEL_SORT,
                AGENT_LABEL_SORT,
                INTERPRETATION_LABEL_SORT,
                TPQ_FILTER,
                TAQ_FILTER
        );
    }

    /**
     * Orders by how many entities a mapped collection of the recording unit holds, via
     * {@code cb.size(...)} — no subquery needed. Not a real filtering predicate: returns a neutral
     * conjunction, the ordering is applied as a side effect on {@code query}. Callers must strip the
     * synthetic sort key from the {@code Pageable}/{@code Sort} passed to the repository, since a
     * collection size is not a real JPA-mapped path.
     *
     * @param attribute the collection attribute whose size orders the results
     * @param direction the ordering direction
     */
    @NonNull
    private static Specification<RecordingUnit> orderByCollectionSize(String attribute, Sort.Direction direction) {
        return (root, query, cb) -> {
            var countExpr = cb.size(root.get(attribute));
            query.orderBy(direction == Sort.Direction.ASC ? cb.asc(countExpr) : cb.desc(countExpr));
            return cb.conjunction();
        };
    }

    @NonNull
    public static Specification<RecordingUnit> orderBySpecimenCount(Sort.Direction direction) {
        return orderByCollectionSize("specimenList", direction);
    }

    @NonNull
    public static Specification<RecordingUnit> orderByParentsCount(Sort.Direction direction) {
        return orderByCollectionSize(PARENTS_FILTER, direction);
    }

    @NonNull
    public static Specification<RecordingUnit> orderByChildrenCount(Sort.Direction direction) {
        return orderByCollectionSize(CHILDREN_FILTER, direction);
    }

    @NonNull
    public static Specification<RecordingUnit> orderByConceptLabel(String attribute, String langCode, Sort.Direction direction) {
        return (root, query, cb) -> {
            Subquery<String> subquery = query.subquery(String.class);
            Root<ConceptPrefLabel> label = subquery.from(ConceptPrefLabel.class);
            subquery.select(label.get("label"));
            subquery.where(cb.and(
                    cb.equal(label.get("concept"), root.get(attribute)),
                    cb.equal(label.get("langCode"), langCode)
            ));
            query.orderBy(direction == Sort.Direction.ASC ? cb.asc(subquery) : cb.desc(subquery));
            return cb.conjunction();
        };
    }

    /**
     * Orders by the number of stratigraphic relationships involving the recording unit, as either
     * {@code unit1} or {@code unit2} — unlike {@link #orderBySpecimenCount(Sort.Direction)}, there is
     * no single mapped collection for this relation (two FK columns matched with OR), so a genuine
     * correlated {@link Subquery} with {@code COUNT} is required. Same caveats otherwise: neutral
     * conjunction predicate, ordering applied as a side effect, synthetic sort key must be stripped
     * from the {@code Pageable}/{@code Sort} passed to the repository.
     */
    @NonNull
    public static Specification<RecordingUnit> orderByRelationshipCount(Sort.Direction direction) {
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<StratigraphicRelationship> rel = subquery.from(StratigraphicRelationship.class);
            subquery.select(cb.count(rel));
            subquery.where(cb.or(
                    cb.equal(rel.get("unit1"), root),
                    cb.equal(rel.get("unit2"), root)
            ));
            query.orderBy(direction == Sort.Direction.ASC ? cb.asc(subquery) : cb.desc(subquery));
            return cb.conjunction();
        };
    }

    @NonNull
    public static Specification<RecordingUnit> recordingUnitInInstitution(long institutionId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("createdByInstitution").get("id"), institutionId);
    }

    @NonNull
    public static Specification<RecordingUnit> recordingUnitInActionUnit(long actionUnitId) {
        return ((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(ACTION_UNIT_FILTER).get("id"), actionUnitId));
    }

    @NonNull
    public static Specification<RecordingUnit> recordingUnitInSpatialUnit(long spatialUnitId) {
        return ((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(SPATIAL_UNIT_FILTER).get("id"), spatialUnitId));
    }

    @NonNull
    public static Specification<RecordingUnit> recordingUnitInRecordingUnit(long id) {
        return ((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("parent").get("id"), id));
    }

    @NonNull
    public static Specification<RecordingUnit> fullIdentifierContains(String fullIdentifier) {
        return (root, query, criteriaBuilder) -> {
            if (fullIdentifier == null || fullIdentifier.isEmpty()) {
                return null;
            }
            return criteriaBuilder.like(criteriaBuilder.lower(root.get(FULL_IDENTIFIER)), "%" + fullIdentifier.toLowerCase() + "%");
        };
    }


    @NonNull
    public static Specification<RecordingUnit> dateFieldBetween(String fieldName, OffsetDateTime from, OffsetDateTime to) {
        return (root, query, criteriaBuilder) -> {
            if (from != null && to != null) {
                return criteriaBuilder.between(root.get(fieldName), from, to);
            } else if (from != null) {
                return criteriaBuilder.greaterThanOrEqualTo(root.get(fieldName), from);
            } else if (to != null) {
                return criteriaBuilder.lessThanOrEqualTo(root.get(fieldName), to);
            }
            return null;
        };
    }

    @NonNull
    public static Specification<RecordingUnit> integerFieldBetween(String fieldName, Integer from, Integer to) {
        return (root, query, criteriaBuilder) -> {
            if (from != null && to != null) {
                return criteriaBuilder.between(root.get(fieldName), from, to);
            } else if (from != null) {
                return criteriaBuilder.greaterThanOrEqualTo(root.get(fieldName), from);
            } else if (to != null) {
                return criteriaBuilder.lessThanOrEqualTo(root.get(fieldName), to);
            }
            return null;
        };
    }

    @NonNull
    public static Specification<RecordingUnit> authorIsIn(List<Long> personsIds) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.in(root.get(RecordingUnitSpec.AUTHOR_FILTER).get("id")).value(personsIds);
    }

    @NonNull
    public static Specification<RecordingUnit> matrixContains(String matrixInput) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.like(criteriaBuilder.lower(root.get(RecordingUnitSpec.MATRIX_FILTER)), "%" + matrixInput.toLowerCase() + "%");
    }

    @NonNull
    public static Specification<RecordingUnit> isInSpatialUnit(List<Long> spatialUnitIds) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.in(root.get(RecordingUnitSpec.SPATIAL_UNIT_FILTER).get("id")).value(spatialUnitIds);
    }

    @NonNull
    public static Specification<RecordingUnit> isInActionUnit(List<Long> actionUnitIds) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.in(root.get(RecordingUnitSpec.ACTION_UNIT_FILTER).get("id")).value(actionUnitIds);
    }

    @NonNull
    public static Specification<RecordingUnit> isInContributors(List<Long> personIds) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.in(root.get(RecordingUnitSpec.CONTRIBUTORS_FILTER).get("id")).value(personIds);
    }

    @NonNull
    public static Specification<RecordingUnit> conceptIsIn(String attribute, List<Long> conceptIds) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.in(root.get(attribute).get("id")).value(conceptIds);
    }

    @NonNull
    public static Specification<RecordingUnit> typeIsIn(List<Long> conceptIds) {
        return conceptIsIn(TYPE_FILTER, conceptIds);
    }

    @NonNull
    public static Specification<RecordingUnit> unitIsRoot() {
        return ((root, query, criteriaBuilder) -> criteriaBuilder.isEmpty(root.get(PARENTS_FILTER)));
    }

    @NonNull
    public static Specification<RecordingUnit> idIn(java.util.Collection<Long> ids) {
        return (root, query, criteriaBuilder) -> root.get("id").in(ids);
    }

    @NonNull
    public static Specification<RecordingUnit> isChildOf(List<Long> parentIds) {
        return (root, query, cb) -> {
            Join<RecordingUnit, RecordingUnit> parentsJoin = root.join(PARENTS_FILTER);
            query.distinct(true);
            return cb.in(parentsJoin.get("id")).value(parentIds);
        };
    }

    @NonNull
    public static Specification<RecordingUnit> isParentOf(List<Long> childIds) {
        return (root, query, cb) -> {
            Join<RecordingUnit, RecordingUnit> childrenJoin = root.join(CHILDREN_FILTER);
            query.distinct(true);
            return cb.in(childrenJoin.get("id")).value(childIds);
        };
    }

}
