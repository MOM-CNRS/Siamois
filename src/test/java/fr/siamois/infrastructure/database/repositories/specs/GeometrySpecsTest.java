package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.form.customfieldanswer.basetypes.CustomFieldAnswerText;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

/**
 * {@link RecordingUnitSpec#hasGeometry}, {@link RecordingUnitSpec#hasTextAnswerTo} and
 * {@link SpatialUnitSpec#hasGeometry}: what a list map keeps, the rows whose geometry column is not empty.
 * Mocked criteria (the codebase has no integration-test database): what is checked is the shape of the query.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class GeometrySpecsTest {

    private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
    private final CriteriaBuilder cb = mock(CriteriaBuilder.class);
    private final Path<Object> geom = mock(Path.class);
    private final Predicate notNull = mock(Predicate.class);

    @Test
    void aRecordingUnitHasAGeometryWhenItsGeomIsSet() {
        Root<RecordingUnit> root = mock(Root.class);
        when(root.get("geom")).thenReturn(geom);
        when(cb.isNotNull(geom)).thenReturn(notNull);

        assertSame(notNull, RecordingUnitSpec.hasGeometry().toPredicate(root, query, cb));
    }

    @Test
    void aPlaceHasAGeometryWhenItsGeomIsSet() {
        Root<SpatialUnit> root = mock(Root.class);
        when(root.get("geom")).thenReturn(geom);
        when(cb.isNotNull(geom)).thenReturn(notNull);

        assertSame(notNull, SpatialUnitSpec.hasGeometry().toPredicate(root, query, cb));
    }

    @Test
    void aRecordingUnitHasATextAnswerWhenOneOfItsAnswersToTheFieldIsNotBlank() {
        Root<RecordingUnit> root = mock(Root.class);
        Subquery<Integer> answers = mock(Subquery.class);
        Root<CustomFieldAnswerText> answer = mock(Root.class);
        Path<Object> formConfigAnswer = mock(Path.class);
        Path<Object> owner = mock(Path.class);
        Path<Object> customField = mock(Path.class);
        Path<Object> fieldId = mock(Path.class);
        Path<Object> value = mock(Path.class);
        Expression<String> trimmed = mock(Expression.class);
        Expression<Integer> one = mock(Expression.class);
        Predicate ownedBy = mock(Predicate.class);
        Predicate ofField = mock(Predicate.class);
        Predicate notBlank = mock(Predicate.class);
        Predicate exists = mock(Predicate.class);
        when(query.subquery(Integer.class)).thenReturn((Subquery) answers);
        when(answers.from(CustomFieldAnswerText.class)).thenReturn(answer);
        when(answer.get("formConfigAnswer")).thenReturn(formConfigAnswer);
        when(formConfigAnswer.get("recordingUnit")).thenReturn(owner);
        when(answer.get("customField")).thenReturn(customField);
        when(customField.get("id")).thenReturn(fieldId);
        when(answer.get("value")).thenReturn(value);
        when(cb.trim((Expression) value)).thenReturn(trimmed);
        when(cb.literal(1)).thenReturn(one);
        when(answers.select(one)).thenReturn(answers);
        when(cb.equal(owner, root)).thenReturn(ownedBy);
        when(cb.equal(fieldId, 40L)).thenReturn(ofField);
        when(cb.notEqual(trimmed, "")).thenReturn(notBlank);
        when(cb.exists(answers)).thenReturn(exists);

        Predicate result = RecordingUnitSpec.hasTextAnswerTo(40L).toPredicate(root, query, cb);

        assertSame(exists, result);
        verify(answers).where(ownedBy, ofField, notBlank);
    }
}
