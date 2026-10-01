package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionUnit;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDecimal;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.Type;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        return new FieldListQuery.Criterion(List.of(v), null, null);
    }

    private static FieldListQuery.Criterion range(String from, String to) {
        return new FieldListQuery.Criterion(List.of(), from, to);
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
    void textColumn_filterContains_andSortLowercased() {
        field(new CustomFieldText(), true, "fullIdentifier");
        attribute(unit, "fullIdentifier", Attribute.PersistentAttributeType.BASIC, String.class);

        run(build(RecordingUnit.class, filter(values("AB", "cd"))));
        verify(cb, atLeastOnce()).like(any(), org.mockito.ArgumentMatchers.eq("%ab%"));

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

    @Test
    void stratigraphyIsRelatedThroughEitherSide() {
        field(new CustomFieldSelectOneActionUnit(), true, "stratigraphicRelationships");

        run(build(RecordingUnit.class, filter(values("3"))));
        run(build(RecordingUnit.class, sort(true)));

        verify(cb, atLeastOnce()).exists(any());
        verify(cb, atLeastOnce()).sum(any(jakarta.persistence.criteria.Expression.class), any(jakarta.persistence.criteria.Expression.class));
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

    // ---------- capabilities ----------

    @Test
    void capabilityOfDescribesWhatAColumnAccepts() {
        CustomField text = field(new CustomFieldText(), true, "fullIdentifier");
        attribute(unit, "fullIdentifier", Attribute.PersistentAttributeType.BASIC, String.class);

        assertThat(service.capabilityOf(RecordingUnit.class, text)).isNotNull();
        assertThat(service.capabilityOf(RecordingUnit.class, field(new CustomFieldText(), true, "nothing"))).isNull();
    }
}
