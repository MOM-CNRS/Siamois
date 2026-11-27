package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.form.customfield.CustomFieldRelation;
import fr.siamois.domain.models.relation.RelationDefinition;
import jakarta.persistence.TypedQuery;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionUnit;
import fr.siamois.domain.models.form.customfield.basetypes.*;
import fr.siamois.domain.models.form.customfield.person.CustomFieldSelectOnePerson;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultipleFromFieldCode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.form.customfieldanswer.person.CustomFieldAnswerSelectOnePerson;
import fr.siamois.domain.models.form.measurement.MeasurementAnswer;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.dto.FieldQuery;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.ui.api.openapi.v1.request.list.FieldListQuery;
import fr.siamois.ui.api.openapi.v1.request.list.FilterOperator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.*;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The Criteria-API half of {@link FieldQueryService}: which column a field resolves to, what a filter
 * or a sort on it builds, and what is a 400. No database here (the codebase has no integration-test
 * infrastructure): the JPA metamodel and the criteria builder are mocked, and the specification's
 * lambda is run against them.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class FieldQueryServiceQueryTest {

    private static final long FIELD = 900_001L;

    private final CustomFieldRepository customFields = mock(CustomFieldRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final Metamodel metamodel = mock(Metamodel.class);
    private final Map<Class<?>, ManagedType<?>> managed = new HashMap<>();
    private final ManagedType<?> unit = mock(ManagedType.class);
    private final HibernateCriteriaBuilder cb = mock(HibernateCriteriaBuilder.class, RETURNS_DEEP_STUBS);
    private final CriteriaQuery<?> query = mock(CriteriaQuery.class, RETURNS_DEEP_STUBS);
    private final Root<?> root = mock(Root.class, RETURNS_DEEP_STUBS);
    private FieldQueryService service;

    @BeforeEach
    void setUp() {
        service = new FieldQueryService(customFields, entityManager);
        when(entityManager.getMetamodel()).thenReturn(metamodel);
        when(metamodel.managedType(any(Class.class))).thenAnswer(inv -> {
            ManagedType<?> type = managed.get(inv.getArgument(0));
            if (type == null) throw new IllegalArgumentException("not managed");
            return type;
        });
        managed.put(RecordingUnit.class, unit);
        when(unit.getAttribute(anyString())).thenThrow(new IllegalArgumentException("unmapped"));
        when(root.getJavaType()).thenReturn((Class) RecordingUnit.class);
    }

    // ---------- helpers ----------

    private <T extends CustomField> T field(T field, boolean system, String binding) {
        field.setId(FIELD);
        field.setIsSystemField(system);
        field.setValueBinding(binding);
        when(customFields.findById(FIELD)).thenReturn(Optional.of(field));
        return field;
    }

    private void attribute(ManagedType<?> type, String name, Attribute.PersistentAttributeType kind, Class<?> javaType) {
        Attribute attribute = mock(Attribute.class);
        when(attribute.getPersistentAttributeType()).thenReturn(kind);
        when(attribute.getJavaType()).thenReturn((Class) javaType);
        doReturn(attribute).when(type).getAttribute(name);
    }

    private void plural(ManagedType<?> type, String name, Class<?> element) {
        PluralAttribute attribute = mock(PluralAttribute.class);
        Type elementType = mock(Type.class);
        when(elementType.getJavaType()).thenReturn((Class) element);
        when(attribute.getElementType()).thenReturn(elementType);
        when(attribute.getPersistentAttributeType()).thenReturn(Attribute.PersistentAttributeType.MANY_TO_MANY);
        doReturn(attribute).when(type).getAttribute(name);
    }

    private EntityType<?> entityWith(Class<?> javaType, String... attributes) {
        EntityType<?> type = mock(EntityType.class);
        for (String name : attributes) {
            Attribute attribute = mock(Attribute.class);
            when(type.getAttribute(name)).thenReturn(attribute);
        }
        when(type.getAttribute(org.mockito.ArgumentMatchers.argThat(n -> !List.of(attributes).contains(n))))
                .thenThrow(new IllegalArgumentException("unmapped"));
        when(type.hasSingleIdAttribute()).thenReturn(true);
        Type idType = mock(Type.class);
        when(idType.getJavaType()).thenReturn((Class) Long.class);
        when(type.getIdType()).thenReturn(idType);
        managed.put(javaType, type);
        return type;
    }

    private static FieldListQuery.Criterion values(String... v) {
        return new FieldListQuery.Criterion(List.of(v), null, null, null);
    }

    private static FieldListQuery.Criterion range(String from, String to) {
        return new FieldListQuery.Criterion(List.of(), from, to, null);
    }

    private static FieldListQuery.Criterion values(FilterOperator operator, String... v) {
        return new FieldListQuery.Criterion(List.of(v), null, null, operator);
    }

    /** The column was compared with {@code pattern} by a LIKE escaping with a backslash. */
    private void verifyLike(String pattern) {
        verifyLike(pattern, times(1));
    }

    private void verifyLike(String pattern, org.mockito.verification.VerificationMode mode) {
        verify(cb, mode).like(any(), org.mockito.ArgumentMatchers.eq(pattern), org.mockito.ArgumentMatchers.eq('\\'));
    }

    private static FieldListQuery filter(FieldListQuery.Criterion criterion) {
        return new FieldListQuery(Map.of(FIELD, criterion), null, true, "fr");
    }

    private static FieldListQuery sort(boolean ascending) {
        return new FieldListQuery(Map.of(), FIELD, ascending, "fr");
    }

    private FieldQuery build(Class<?> entity, FieldListQuery q) {
        return service.toFieldQuery(entity, q);
    }

    private void run(FieldQuery fieldQuery) {
        Specification spec = fieldQuery.specification();
        spec.toPredicate((Root) root, (CriteriaQuery) query, cb);
    }

    private ResponseStatusException badRequest(Class<?> entity, FieldListQuery q) {
        return org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class, () -> build(entity, q));
    }

    // ---------- nothing to do / unknown ----------

    @Test
    void emptyQueryIsNone() {
        assertThat(service.toFieldQuery(RecordingUnit.class, null)).isSameAs(FieldQuery.NONE);
        assertThat(service.toFieldQuery(RecordingUnit.class, new FieldListQuery(Map.of(), null, true, "fr"))).isSameAs(FieldQuery.NONE);
        assertThat(service.parse(RecordingUnit.class, null, null, null)).isSameAs(FieldQuery.NONE);
    }

    @Test
    void unknownFieldIsABadRequest() {
        when(customFields.findById(FIELD)).thenReturn(Optional.empty());
        assertThat(badRequest(RecordingUnit.class, filter(values("x"))).getReason()).contains("Champ inconnu");
    }

    @Test
    void anUnmappedSystemBindingCannotBeQueried() {
        field(new CustomFieldText(), true, "nothing");
        assertThat(badRequest(RecordingUnit.class, sort(true)).getReason()).contains("ne se trie ni ne se filtre");
    }

    @Test
    void aSystemFieldWithoutBindingCannotBeQueried() {
        field(new CustomFieldText(), true, null);
        badRequest(RecordingUnit.class, sort(true));
    }

    @Test
    void aFieldOnAnEntityWithoutAnswersCannotBeQueriedWhenAdditional() {
        field(new CustomFieldText(), false, null);
        badRequest(fr.siamois.domain.models.actionunit.ActionUnit.class, sort(true));
    }

    // ---------- system scalar columns ----------

    @Test
    void aFilterMarksTheQueryFiltered_aSortAloneDoesNot() {
        field(new CustomFieldText(), true, "fullIdentifier");
        attribute(unit, "fullIdentifier", Attribute.PersistentAttributeType.BASIC, String.class);

        // The tree view treats a filtered list like a searched one; a mere sort narrows nothing.
        assertThat(build(RecordingUnit.class, filter(values("AB"))).filtered()).isTrue();
        assertThat(build(RecordingUnit.class, sort(true)).filtered()).isFalse();
        assertThat(build(RecordingUnit.class, sort(true)).ordered()).isTrue();
        assertThat(FieldQuery.NONE.filtered()).isFalse();
    }

    @Test
    void theOrderingIsKeptApartFromTheFilters() {
        field(new CustomFieldText(), true, "fullIdentifier");
        attribute(unit, "fullIdentifier", Attribute.PersistentAttributeType.BASIC, String.class);

        // The tree view orders every level by the sort but must not narrow its parents by the filters.
        assertThat(build(RecordingUnit.class, sort(true)).ordering()).isNotNull();
        assertThat(build(RecordingUnit.class, filter(values("AB"))).ordering()).isNull();
        assertThat(build(RecordingUnit.class, filter(values("AB"))).<RecordingUnit>orderingSpecificationFor()).isNotNull();
        assertThat(FieldQuery.NONE.<Object>orderingSpecificationFor()).isNotNull();
    }

    @Test
    void textColumn_filterContains_andSortLowercased() {
        field(new CustomFieldText(), true, "fullIdentifier");
        attribute(unit, "fullIdentifier", Attribute.PersistentAttributeType.BASIC, String.class);

        run(build(RecordingUnit.class, filter(values("AB", "cd"))));
        verifyLike("%ab%", atLeastOnce());

        run(build(RecordingUnit.class, sort(true)));
        verify(cb, atLeastOnce()).lower(any());
        verify(query).orderBy(any(jakarta.persistence.criteria.Order.class), any(jakarta.persistence.criteria.Order.class));
    }

    @Test
    void textColumn_refusesARange() {
        field(new CustomFieldText(), true, "fullIdentifier");
        attribute(unit, "fullIdentifier", Attribute.PersistentAttributeType.BASIC, String.class);
        assertThat(badRequest(RecordingUnit.class, filter(range("a", "b"))).getReason()).contains("plage impossible sur un champ texte");
    }

    @Test
    void numberColumn_rangeIsRoundedInwardsOnAnIntegerColumn() {
        field(new CustomFieldInteger(), true, "count");
        attribute(unit, "count", Attribute.PersistentAttributeType.BASIC, int.class);

        run(build(RecordingUnit.class, filter(range("1,2", "4.9"))));

        verify(cb).greaterThanOrEqualTo(any(jakarta.persistence.criteria.Expression.class), org.mockito.ArgumentMatchers.eq(2));
        verify(cb).lessThanOrEqualTo(any(jakarta.persistence.criteria.Expression.class), org.mockito.ArgumentMatchers.eq(4));
    }

    @Test
    void numberColumn_keepsDecimalsOnADoubleColumn_andSorts() {
        field(new CustomFieldDecimal(), true, "zInf");
        attribute(unit, "zInf", Attribute.PersistentAttributeType.BASIC, Double.class);

        run(build(RecordingUnit.class, filter(range("1.5", null))));
        verify(cb).greaterThanOrEqualTo(any(jakarta.persistence.criteria.Expression.class), org.mockito.ArgumentMatchers.eq(1.5));
        verify(cb, never()).lessThanOrEqualTo(any(jakarta.persistence.criteria.Expression.class), any(Comparable.class));

        run(build(RecordingUnit.class, sort(false)));
    }

    @Test
    void numberColumn_badValuesAndShapes() {
        field(new CustomFieldDecimal(), true, "zInf");
        attribute(unit, "zInf", Attribute.PersistentAttributeType.BASIC, Double.class);

        assertThat(badRequest(RecordingUnit.class, filter(range("abc", null))).getReason()).contains("Nombre invalide");
        assertThat(badRequest(RecordingUnit.class, filter(values("1"))).getReason()).contains("plage");
    }

    @Test
    void dateColumn_bareDatesAndOffsetDateTimes() {
        field(new CustomFieldDateTime(), true, "creationTime");
        attribute(unit, "creationTime", Attribute.PersistentAttributeType.BASIC, LocalDateTime.class);

        run(build(RecordingUnit.class, filter(range("2024-05-01", "2024-05-02"))));
        verify(cb).greaterThanOrEqualTo(any(jakarta.persistence.criteria.Expression.class),
                org.mockito.ArgumentMatchers.eq(LocalDateTime.of(2024, java.time.Month.MAY, 1, 0, 0)));
        verify(cb).lessThan(any(jakarta.persistence.criteria.Expression.class),
                org.mockito.ArgumentMatchers.eq(LocalDateTime.of(2024, java.time.Month.MAY, 3, 0, 0)));

        run(build(RecordingUnit.class, filter(range("2024-05-01T10:00:00+02:00", null))));
        verify(cb).greaterThanOrEqualTo(any(jakarta.persistence.criteria.Expression.class),
                org.mockito.ArgumentMatchers.eq(LocalDateTime.of(2024, java.time.Month.MAY, 1, 8, 0)));

        assertThat(badRequest(RecordingUnit.class, filter(range("not-a-date", null))).getReason()).contains("Date invalide");
    }

    @Test
    void aDateColumnOfEachJavaTimeTypeGetsItsOwnBound() {
        for (Class<?> type : List.of(java.time.OffsetDateTime.class, java.time.LocalDate.class, java.time.Instant.class,
                java.time.ZonedDateTime.class, java.util.Date.class)) {
            field(new CustomFieldDateTime(), true, "d");
            attribute(unit, "d", Attribute.PersistentAttributeType.BASIC, type);
            FieldQuery fieldQuery = build(RecordingUnit.class, filter(range("2024-05-01", null)));
            run(fieldQuery);
            verify(cb, atLeastOnce()).greaterThanOrEqualTo(any(jakarta.persistence.criteria.Expression.class),
                    org.mockito.ArgumentMatchers.<Comparable>argThat(type::isInstance));
        }
    }

    @Test
    void aBasicColumnOfAnotherTypeCannotBeQueried() {
        field(new CustomFieldText(), true, "flag");
        attribute(unit, "flag", Attribute.PersistentAttributeType.BASIC, Boolean.class);
        badRequest(RecordingUnit.class, sort(true));
    }

    // ---------- system references ----------

    @Test
    void conceptReference_filterByIds_sortByPreferredLabel() {
        field(new CustomFieldSelectOneFromFieldCode(), true, "type");
        attribute(unit, "type", Attribute.PersistentAttributeType.MANY_TO_ONE, Concept.class);

        run(build(RecordingUnit.class, filter(values("4", "5"))));
        run(build(RecordingUnit.class, sort(true)));

        assertThat(badRequest(RecordingUnit.class, filter(values("x"))).getReason()).contains("Identifiant invalide");
        assertThat(badRequest(RecordingUnit.class, filter(range("1", "2"))).getReason()).contains("plage impossible sur une référence");
    }

    @Test
    void entityReference_sortsByItsLabelAttribute() {
        field(new CustomFieldSelectOneActionUnit(), true, "actionUnit");
        attribute(unit, "actionUnit", Attribute.PersistentAttributeType.MANY_TO_ONE, Phase.class);
        entityWith(Phase.class, "identifier");

        run(build(RecordingUnit.class, filter(values("1"))));
        run(build(RecordingUnit.class, sort(true)));
        assertThat(build(RecordingUnit.class, sort(true)).ordered()).isTrue();
    }

    @Test
    void anEntityReferenceWithoutLabelCannotBeQueried() {
        field(new CustomFieldSelectOneActionUnit(), true, "actionUnit");
        attribute(unit, "actionUnit", Attribute.PersistentAttributeType.MANY_TO_ONE, Phase.class);
        entityWith(Phase.class);
        badRequest(RecordingUnit.class, sort(true));
    }

    @Test
    void aReferenceToAnUnmanagedTypeCannotBeQueried() {
        field(new CustomFieldSelectOneActionUnit(), true, "actionUnit");
        attribute(unit, "actionUnit", Attribute.PersistentAttributeType.MANY_TO_ONE, String.class);
        badRequest(RecordingUnit.class, sort(true));
    }

    @Test
    void multiValuedReference_hasAtLeastOne_sortsByCount() {
        field(new CustomFieldSelectMultipleFromFieldCode(), true, "keywords");
        plural(unit, "keywords", Concept.class);

        run(build(RecordingUnit.class, filter(values("1", "2"))));
        run(build(RecordingUnit.class, sort(true)));
        verify(cb, atLeastOnce()).size(any(jakarta.persistence.criteria.Expression.class));
    }

    @Test
    void aMeasurementIsComparedAsANumberInTheBaseUnit() {
        field(new CustomFieldMeasurement(), true, "length");
        attribute(unit, "length", Attribute.PersistentAttributeType.MANY_TO_ONE, MeasurementAnswer.class);

        run(build(RecordingUnit.class, filter(range("1", "2"))));
        run(build(RecordingUnit.class, sort(true)));
        assertThat(build(RecordingUnit.class, sort(true)).ordered()).isTrue();
    }

    @Test
    void otherAttributeKindsCannotBeQueried() {
        field(new CustomFieldText(), true, "embedded");
        attribute(unit, "embedded", Attribute.PersistentAttributeType.EMBEDDED, Object.class);
        badRequest(RecordingUnit.class, sort(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"anteriorRelationships", "posteriorRelationships", "synchronousRelationships"})
    void eachSideOfTheStratigraphyIsFilteredAndSortedOnItsOwn(String binding) {
        field(new CustomFieldSelectOneActionUnit(), true, binding);

        run(build(RecordingUnit.class, filter(values("3"))));
        run(build(RecordingUnit.class, sort(true)));

        verify(cb, atLeastOnce()).exists(any());
        verify(cb, atLeastOnce()).count(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aConfigurableRelationFieldIsFilteredAndSortedByItsLinks(boolean symmetric) {
        field(new CustomFieldRelation(), false, null);
        relationDefinition(symmetric ? FIELD : 77L, FIELD, symmetric);

        run(build(RecordingUnit.class, filter(values("3"))));
        run(build(RecordingUnit.class, filter(values(FilterOperator.NOT_EMPTY))));
        run(build(RecordingUnit.class, sort(true)));

        verify(cb, atLeastOnce()).exists(any());
        verify(cb, atLeastOnce()).count(any());
    }

    @Test
    void aRelationFieldReadFromItsSourceSideFiltersFromTheSource() {
        field(new CustomFieldRelation(), false, null);
        relationDefinition(FIELD, 77L, false);

        run(build(RecordingUnit.class, filter(values("3"))));

        verify(cb, atLeastOnce()).exists(any());
    }

    @Test
    void aRelationFieldNoDefinitionUsesCannotBeQueried() {
        field(new CustomFieldRelation(), false, null);
        TypedQuery<RelationDefinition> none = mock(TypedQuery.class, RETURNS_SELF);
        when(none.getResultStream()).thenReturn(java.util.stream.Stream.empty());
        when(entityManager.createQuery(anyString(), eq(RelationDefinition.class))).thenReturn(none);

        badRequest(RecordingUnit.class, sort(true));
    }

    /** A definition between {@code sourceField} and {@code targetField}, as linkSideOf reads it. */
    private void relationDefinition(long sourceField, long targetField, boolean symmetric) {
        RelationDefinition definition = new RelationDefinition();
        definition.setId(10L);
        CustomFieldRelation source = new CustomFieldRelation();
        source.setId(sourceField);
        CustomFieldRelation target = new CustomFieldRelation();
        target.setId(targetField);
        definition.setSourceField(source);
        definition.setTargetField(target);
        definition.setSymmetric(symmetric);
        TypedQuery<RelationDefinition> found = mock(TypedQuery.class, RETURNS_SELF);
        when(found.getResultStream()).thenAnswer(inv -> java.util.stream.Stream.of(definition));
        when(entityManager.createQuery(anyString(), eq(RelationDefinition.class))).thenReturn(found);
    }

    @Test
    void theCountQueryIsLeftUnordered() {
        field(new CustomFieldText(), true, "fullIdentifier");
        attribute(unit, "fullIdentifier", Attribute.PersistentAttributeType.BASIC, String.class);
        when(query.getResultType()).thenReturn((Class) Long.class);

        run(build(RecordingUnit.class, sort(true)));

        verify(query, never()).orderBy(any(jakarta.persistence.criteria.Order.class), any(jakarta.persistence.criteria.Order.class));
    }

    // ---------- additional fields (answer rows) ----------

    @Test
    void additionalTextIntegerDecimalAndDateFields() {
        for (CustomField f : List.of(new CustomFieldText(), new CustomFieldInteger(), new CustomFieldDecimal(),
                new CustomFieldDateTime(), new CustomFieldMeasurement())) {
            field(f, false, null);
            boolean date = f instanceof CustomFieldDateTime;
            boolean text = f instanceof CustomFieldText;
            FieldListQuery.Criterion criterion = text ? values("x") : range(date ? "2024-01-01" : "1", null);
            run(build(RecordingUnit.class, filter(criterion)));
            run(build(RecordingUnit.class, sort(true)));
        }
        verify(cb, atLeastOnce()).greatest(any(jakarta.persistence.criteria.Expression.class));
    }

    @Test
    void additionalYesNoFieldFiltersOnTrueOrFalse_andSortsYesAfterNo() {
        field(CustomFieldBoolean.builder().build(), false, null);

        run(build(RecordingUnit.class, filter(values("true", "FALSE"))));
        run(build(RecordingUnit.class, sort(true)));

        verify(cb, atLeastOnce()).isTrue(any(jakarta.persistence.criteria.Expression.class));
        verify(cb, atLeastOnce()).greatest(any(jakarta.persistence.criteria.Expression.class));
    }

    @Test
    void additionalYesNoFieldRefusesARangeAndAnythingButTrueOrFalse() {
        field(CustomFieldBoolean.builder().build(), false, null);

        assertThat(badRequest(RecordingUnit.class, filter(range("a", "b"))).getReason()).contains("plage impossible sur un champ oui/non");
        assertThat(badRequest(RecordingUnit.class, filter(values("peut-être"))).getReason()).contains("Booléen invalide");
    }

    @Test
    void additionalConceptFieldSortsByPreferredLabelInTheCallersLanguage() {
        field(new CustomFieldSelectOneFromFieldCode(), false, null);

        run(build(RecordingUnit.class, filter(values("1"))));
        run(build(RecordingUnit.class, sort(true)));
        verify(cb, atLeastOnce()).least(any(jakarta.persistence.criteria.Expression.class));
    }

    @Test
    void additionalMultiConceptFieldSortsByCount() {
        field(new CustomFieldSelectMultipleFromFieldCode(), false, null);

        run(build(RecordingUnit.class, filter(values("1"))));
        run(build(RecordingUnit.class, sort(false)));
        verify(cb, atLeastOnce()).count(any(jakarta.persistence.criteria.Expression.class));
    }

    @Test
    void additionalPersonFieldReferencesThePersonsOfItsAnswer() {
        field(new CustomFieldSelectOnePerson(), false, null);
        ManagedType<?> answer = mock(ManagedType.class);
        plural(answer, "persons", Person.class);
        managed.put(CustomFieldAnswerSelectOnePerson.class, answer);
        entityWith(Person.class, "lastname");

        run(build(RecordingUnit.class, filter(values("1"))));
        run(build(RecordingUnit.class, sort(true)));
        assertThat(build(RecordingUnit.class, sort(true)).ordered()).isTrue();
    }

    @Test
    void additionalPersonFieldWhoseAnswerIsUnmanagedCannotBeQueried() {
        field(new CustomFieldSelectOnePerson(), false, null);
        badRequest(RecordingUnit.class, sort(true));
    }

    @Test
    void additionalPersonFieldWhoseCollectionIsNotPluralCannotBeQueried() {
        field(new CustomFieldSelectOnePerson(), false, null);
        ManagedType<?> answer = mock(ManagedType.class);
        attribute(answer, "persons", Attribute.PersistentAttributeType.BASIC, String.class);
        managed.put(CustomFieldAnswerSelectOnePerson.class, answer);
        badRequest(RecordingUnit.class, sort(true));
    }

    // ---------- list-only columns ----------

    private static final long STATUS = ListOnlyColumn.VALIDATION_STATUS.fieldId();

    private static FieldListQuery statusFilter(FieldListQuery.Criterion criterion) {
        return new FieldListQuery(Map.of(STATUS, criterion), null, true, "fr");
    }

    @Test
    void theValidationStatusIsFilteredByStatusNames_withoutAnyFormField() {
        attribute(unit, "validationStatus", Attribute.PersistentAttributeType.BASIC, ValidationStatus.class);

        run(build(RecordingUnit.class, statusFilter(values("validated", "CANCELLED"))));

        verify(root.get("validationStatus")).in(List.of(ValidationStatus.VALIDATED, ValidationStatus.CANCELLED));
        verifyNoInteractions(customFields);
    }

    @Test
    void theValidationStatusSortsInWorkflowOrder() {
        attribute(unit, "validationStatus", Attribute.PersistentAttributeType.BASIC, ValidationStatus.class);

        run(build(RecordingUnit.class, new FieldListQuery(Map.of(), STATUS, true, "fr")));

        for (ValidationStatus status : ValidationStatus.values()) {
            verify(cb).equal(any(jakarta.persistence.criteria.Expression.class), org.mockito.ArgumentMatchers.eq(status));
        }
        verify(query).orderBy(any(jakarta.persistence.criteria.Order.class), any(jakarta.persistence.criteria.Order.class));
    }

    @Test
    void theValidationStatusRefusesARangeAndAnUnknownStatus() {
        attribute(unit, "validationStatus", Attribute.PersistentAttributeType.BASIC, ValidationStatus.class);

        assertThat(badRequest(RecordingUnit.class, statusFilter(range("a", "b"))).getReason()).contains("plage impossible");
        assertThat(badRequest(RecordingUnit.class, statusFilter(values("DONE"))).getReason()).contains("Valeur inconnue");
    }

    @Test
    void aListOnlyColumnTheEntityDoesNotMapCannotBeQueried() {
        assertThat(badRequest(RecordingUnit.class, statusFilter(values("VALIDATED"))).getReason())
                .contains("ne se trie ni ne se filtre");
    }

    @Test
    void listOnlyColumnsAreFoundByTheirReservedId() {
        assertThat(ListOnlyColumn.ofFieldId(-901L)).contains(ListOnlyColumn.VALIDATION_STATUS);
        assertThat(ListOnlyColumn.VALIDATION_STATUS.attributeOf(RecordingUnit.class)).isEqualTo("validationStatus");
        assertThat(ListOnlyColumn.ofFieldId(-301L)).isEmpty();
    }

    @Test
    void theIdentifierColumnReadsTheFullIdentifierWhereTheEntityComposesOne() {
        assertThat(ListOnlyColumn.ofFieldId(-902L)).contains(ListOnlyColumn.IDENTIFIER);
        assertThat(ListOnlyColumn.IDENTIFIER.attributeOf(RecordingUnit.class)).isEqualTo("fullIdentifier");
        assertThat(ListOnlyColumn.IDENTIFIER.attributeOf(Phase.class)).isEqualTo("identifier");
        assertThat(ListOnlyColumn.NAME.attributeOf(fr.siamois.domain.models.actionunit.ActionUnit.class)).isEqualTo("name");
    }

    @Test
    void theIdentifierColumnIsFilteredAsAText_withEveryTextOperator() {
        attribute(unit, "fullIdentifier", Attribute.PersistentAttributeType.BASIC, String.class);
        long identifier = ListOnlyColumn.IDENTIFIER.fieldId();

        run(build(RecordingUnit.class, new FieldListQuery(Map.of(identifier, values(FilterOperator.ENDS_WITH, "12")), null, true, "fr")));

        verifyLike("%12");
        verifyNoInteractions(customFields);
    }

    // ---------- geometries ----------

    @Test
    void aGeometryColumnIsOnlyFilteredOnHavingAShape_andNeverSorted() {
        CustomField geometry = field(new CustomFieldGeometry(), true, "geom");
        attribute(unit, "geom", Attribute.PersistentAttributeType.BASIC, org.locationtech.jts.geom.Geometry.class);

        assertThat(service.capabilityOf(RecordingUnit.class, geometry)).isEqualTo(new fr.siamois.ui.api.openapi.v1.resource.form.FieldResource.Query(false, "presence"));
        run(build(RecordingUnit.class, filter(values(FilterOperator.EMPTY))));
        verify(cb).not(any());
        assertThat(badRequest(RecordingUnit.class, filter(values(FilterOperator.CONTAINS, "POINT"))).getReason()).contains("Opérateur contains impossible");
        assertThat(badRequest(RecordingUnit.class, sort(true)).getReason()).contains("ne se trie pas");
    }

    @Test
    void anAdditionalGeometryFieldIsNoTextToSearchIn() {
        CustomField geometry = field(new CustomFieldGeometry(), false, null);

        assertThat(service.capabilityOf(RecordingUnit.class, geometry).filterOp()).isEqualTo("presence");
        run(build(RecordingUnit.class, filter(values(FilterOperator.NOT_EMPTY))));
        // Blank WKT counts as no shape.
        verify(cb).trim(any(jakarta.persistence.criteria.Expression.class));
        verify(cb, atLeastOnce()).exists(any());
    }

    // ---------- operators ----------

    @Test
    void textColumn_eachTextOperatorBuildsItsOwnComparison() {
        field(new CustomFieldText(), true, "fullIdentifier");
        attribute(unit, "fullIdentifier", Attribute.PersistentAttributeType.BASIC, String.class);

        run(build(RecordingUnit.class, filter(values(FilterOperator.STARTS_WITH, "AB"))));
        verifyLike("ab%");
        run(build(RecordingUnit.class, filter(values(FilterOperator.ENDS_WITH, "AB"))));
        verifyLike("%ab");
        run(build(RecordingUnit.class, filter(values(FilterOperator.IS, "AB"))));
        verify(cb).equal(any(jakarta.persistence.criteria.Expression.class), org.mockito.ArgumentMatchers.eq("ab"));
        verify(cb, never()).not(any());
    }

    @Test
    void aNegatedOperatorKeepsTheRowsTheMatchLeavesOut_emptyOnesIncluded() {
        field(new CustomFieldText(), true, "fullIdentifier");
        attribute(unit, "fullIdentifier", Attribute.PersistentAttributeType.BASIC, String.class);

        run(build(RecordingUnit.class, filter(values(FilterOperator.NOT_CONTAINS, "AB"))));

        // NOT (value IS NOT NULL AND value LIKE …): a row without a value is kept.
        verify(cb).isNotNull(any());
        verify(cb).not(any());
    }

    @Test
    void emptyAndNotEmptyTakeNoValue_onAnyKind() {
        field(new CustomFieldDecimal(), true, "zInf");
        attribute(unit, "zInf", Attribute.PersistentAttributeType.BASIC, Double.class);

        run(build(RecordingUnit.class, filter(values(FilterOperator.NOT_EMPTY))));
        verify(cb, never()).not(any());
        run(build(RecordingUnit.class, filter(values(FilterOperator.EMPTY))));
        verify(cb).not(any());
        verify(cb, never()).greaterThanOrEqualTo(any(jakarta.persistence.criteria.Expression.class), any(Comparable.class));
    }

    @Test
    void notIn_onAMultiValuedReference_isHavingNoneOfThem() {
        field(new CustomFieldSelectMultipleFromFieldCode(), true, "keywords");
        plural(unit, "keywords", Concept.class);

        run(build(RecordingUnit.class, filter(values(FilterOperator.NOT_IN, "1", "2"))));

        verify(cb).not(any());
        verify(cb, atLeastOnce()).exists(any());
    }

    @Test
    void additionalField_emptyIsHavingNoAnswerWithAValue() {
        field(new CustomFieldText(), false, null);

        run(build(RecordingUnit.class, filter(values(FilterOperator.EMPTY))));

        verify(cb).not(any());
        verify(cb, atLeastOnce()).exists(any());
    }

    @Test
    void anOperatorTheKindCannotApplyIsABadRequest() {
        field(new CustomFieldDecimal(), true, "zInf");
        attribute(unit, "zInf", Attribute.PersistentAttributeType.BASIC, Double.class);

        assertThat(badRequest(RecordingUnit.class, filter(values(FilterOperator.STARTS_WITH, "1"))).getReason())
                .contains("Opérateur starts_with impossible");
    }

    @Test
    void likePatternsEscapeTheTextsOwnWildcards() {
        assertThat(FilterPredicates.likePattern(FilterOperator.Match.CONTAINS, "10%_a\\b")).isEqualTo("%10\\%\\_a\\\\b%");
        assertThat(FilterPredicates.likePattern(FilterOperator.Match.STARTS_WITH, "ab")).isEqualTo("ab%");
        assertThat(FilterPredicates.likePattern(FilterOperator.Match.ENDS_WITH, "ab")).isEqualTo("%ab");
    }

    // ---------- capabilities ----------

    @Test
    void capabilityOfDescribesWhatAColumnAccepts() {
        CustomField text = field(new CustomFieldText(), true, "fullIdentifier");
        attribute(unit, "fullIdentifier", Attribute.PersistentAttributeType.BASIC, String.class);

        assertThat(service.capabilityOf(RecordingUnit.class, text)).isNotNull();
        assertThat(service.capabilityOf(RecordingUnit.class, field(new CustomFieldText(), true, "nothing"))).isNull();
    }
}
