package fr.siamois.domain.services.exporttemplate;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.OutputType;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ExportValueFormatterTest {

    private final ExportValueFormatter formatter = new ExportValueFormatter(Map.of(7L, "Fait"));

    private Optional<String> format(Object value) {
        return formatter.format(value, OutputType.TEXT);
    }

    @Test
    void nullAndPlainValues() {
        assertThat(format(null)).contains("");
        assertThat(format("abc")).contains("abc");
        assertThat(format(true)).contains("true");
        assertThat(format(12)).contains("12");
        assertThat(format(new BigDecimal("1.500"))).contains("1.5");
        assertThat(format(2.0d)).contains("2");
    }

    @Test
    void concept_usesTheResolvedLabel_orFallsBackToItsExternalId() {
        Concept known = new Concept();
        known.setId(7L);
        known.setExternalId("x");
        Concept unknown = new Concept();
        unknown.setId(8L);
        unknown.setExternalId("ext-8");

        assertThat(format(known)).contains("Fait");
        assertThat(format(unknown)).contains("ext-8");
    }

    @Test
    void person_actionUnit_spatialUnit() {
        Person person = new Person();
        person.setName("Ada");
        person.setLastname("Lovelace");
        ActionUnit project = new ActionUnit();
        project.setFullIdentifier("ORG-1");
        ActionUnit unnamed = new ActionUnit();
        unnamed.setName("Fouille");
        SpatialUnit place = new SpatialUnit();
        place.setName("Parcelle A");

        assertThat(format(person)).contains("Ada Lovelace");
        assertThat(format(project)).contains("ORG-1");
        assertThat(format(unnamed)).contains("Fouille");
        assertThat(format(place)).contains("Parcelle A");
    }

    @Test
    void dates_areIso8601_andDateOutputKeepsOnlyTheDay() {
        OffsetDateTime moment = OffsetDateTime.of(2026, Month.MARCH.getValue(), 4, 10, 30, 0, 0, ZoneOffset.UTC);

        assertThat(format(LocalDate.of(2026, Month.MARCH, 4))).contains("2026-03-04");
        assertThat(format(moment)).contains("2026-03-04T10:30:00Z");
        assertThat(formatter.format(moment, OutputType.DATE)).contains("2026-03-04");
    }

    @Test
    void unsupportedType_isEmpty() {
        assertThat(format(new Object())).isEmpty();
    }

    @Test
    void measurement_usesTheEnteredValue_elseTheNormalizedOne() {
        fr.siamois.domain.models.form.measurement.MeasurementAnswer normalized =
                fr.siamois.domain.models.form.measurement.MeasurementAnswer.builder().numericValue(150.0).normalizedValue(1.5).build();
        fr.siamois.domain.models.form.measurement.MeasurementAnswer onlyNormalized =
                fr.siamois.domain.models.form.measurement.MeasurementAnswer.builder().normalizedValue(1.5).build();

        assertThat(format(normalized)).contains("150");
        assertThat(format(onlyNormalized)).contains("1.5");
    }

    @Test
    void recordingUnit_specimen_phase_document_useTheirIdentifierOrTitle() {
        fr.siamois.domain.models.recordingunit.RecordingUnit ru = new fr.siamois.domain.models.recordingunit.RecordingUnit();
        ru.setFullIdentifier("UE-1");
        fr.siamois.domain.models.phase.Phase phase = new fr.siamois.domain.models.phase.Phase();
        phase.setTitle("Phase 1");

        assertThat(format(ru)).contains("UE-1");
        assertThat(format(phase)).contains("Phase 1");
    }

    @Test
    void anUnknownEntity_isNotRead() {
        assertThat(format(new Object())).isEmpty();
    }

    @Test
    void anInstant_isWrittenInUtc() {
        java.time.Instant instant = java.time.OffsetDateTime.parse("2026-03-01T23:30:00+01:00").toInstant();

        assertThat(format(instant)).contains("2026-03-01T22:30:00Z");
    }
}
