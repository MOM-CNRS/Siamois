package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldBoolean;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.ui.api.openapi.v1.resource.form.BooleanFieldAnswer;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** A yes/no field's answer reaches the API as a plain boolean, whichever entity's projector writes it. */
class BooleanAnswerProjectionTest {

    private final CustomFieldBoolean done = CustomFieldBoolean.builder().id(1L).build();

    @Test
    void everyProjectorWritesABooleanAsIs_andAnythingElseAsNothing() {
        for (Class<?> projector : List.of(RecordingUnitAnswersProjector.class, PhaseAnswersProjector.class,
                ProjectAnswersProjector.class, SpecimenAnswersProjector.class)) {
            assertThat(ReflectionTestUtils.<Object>invokeMethod(projector, "toWireValue", done, true, Map.of()))
                    .as(projector.getSimpleName()).isEqualTo(true);
            assertThat(ReflectionTestUtils.<Object>invokeMethod(projector, "toWireValue", done, false, Map.of()))
                    .as(projector.getSimpleName()).isEqualTo(false);
            assertThat(ReflectionTestUtils.<Object>invokeMethod(projector, "toWireValue", done, "oui", Map.of()))
                    .as(projector.getSimpleName()).isNull();
        }
    }

    @Test
    void aDocumentWritesABooleanAsIs() {
        assertThat(ReflectionTestUtils.<Object>invokeMethod(new DocumentAnswersProjector(), "scalarValue", done, true)).isEqualTo(true);
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(new DocumentAnswersProjector(), "isScalar", done)).isTrue();
    }

    @Test
    void aProjectsYesNoAnswerTakesTrueFalseOrTheirText_andRefusesAnythingElse() {
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(ProjectApiService.class, "coerceBooleanAnswer", true)).isTrue();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(ProjectApiService.class, "coerceBooleanAnswer", " FALSE ")).isFalse();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(ProjectApiService.class, "coerceBooleanAnswer", "True")).isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> ReflectionTestUtils.invokeMethod(ProjectApiService.class, "coerceBooleanAnswer", "peut-être"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void theAnswerOfAYesNoFieldIsABooleanAnswer() {
        FieldAnswerWireService wire = new FieldAnswerWireService(null, null);

        assertThat(wire.toTypedAnswer("BOOLEAN", null, true, "fr")).isEqualTo(new BooleanFieldAnswer("BOOLEAN", null, true));
        assertThat(wire.toTypedAnswer("BOOLEAN", null, "oui", "fr")).isEqualTo(new BooleanFieldAnswer("BOOLEAN", null, null));
    }

    @Test
    void aYesNoFieldTellsWhetherItAllowsNoAnswer() {
        CustomFieldBoolean tri = CustomFieldBoolean.builder().id(2L).allowNoAnswer(true).build();

        FieldResource.Constraints constraints = FieldAnswerWireService.fieldResourceOf(tri, "Topo", null).constraints();

        assertThat(constraints.allowNoAnswer()).isTrue();
        assertThat(FieldAnswerWireService.fieldResourceOf(done, "Topo", null).constraints()).isNull();
    }

    @Test
    void aMeasurementListsItsUnitsWhenItOffersSeveral() {
        UnitDefinition squareMetre = unit(1L, "m²", 1.0);
        UnitDefinition hectare = unit(2L, "ha", 10_000.0);
        CustomFieldMeasurement several = CustomFieldMeasurement.builder().id(3L).unit(hectare)
                .allowedUnits(new LinkedHashSet<>(Set.of(squareMetre, hectare))).build();
        CustomFieldMeasurement single = CustomFieldMeasurement.builder().id(4L).unit(squareMetre).build();

        FieldResource.Constraints severalConstraints = FieldAnswerWireService.fieldResourceOf(several, "Surface", null).constraints();
        FieldResource.Constraints singleConstraints = FieldAnswerWireService.fieldResourceOf(single, "Surface", null).constraints();

        assertThat(severalConstraints.unit()).isEqualTo("ha");
        assertThat(severalConstraints.units()).containsExactly("ha", "m²");
        assertThat(singleConstraints.units()).isNull();
    }

    private static UnitDefinition unit(long id, String symbol, double factor) {
        UnitDefinition unit = new UnitDefinition();
        unit.setId(id);
        unit.setSymbol(symbol);
        unit.setDimension(UnitDefinition.Dimension.AREA);
        unit.setFactorToBase(factor);
        return unit;
    }
}
