package fr.siamois.domain.services.exporttemplate;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.exporttemplate.ExportSubject;
import fr.siamois.domain.models.exporttemplate.ExportTechnicalSource;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExportNavigationsTest {

    private static RecordingUnit unitOf(ActionUnit project) {
        RecordingUnit ru = new RecordingUnit();
        ru.setActionUnit(project);
        return ru;
    }

    @Test
    void follow_project_fromAnEntity() {
        ActionUnit project = new ActionUnit();
        ExportRow row = ExportRow.of(ExportSubject.RECORDING_UNIT, unitOf(project));

        Optional<ExportRow> result = ExportNavigations.follow(row, List.of("project"));

        assertThat(result).hasValueSatisfying(r -> {
            assertThat(r.subject()).isEqualTo(ExportSubject.PROJECT);
            assertThat(r.entity()).isSameAs(project);
        });
    }

    @Test
    void follow_emptyPath_returnsTheRowItself() {
        ExportRow row = ExportRow.of(ExportSubject.PROJECT, new ActionUnit());

        assertThat(ExportNavigations.follow(row, List.of())).containsSame(row);
    }

    @Test
    void follow_stopsAtANullLink() {
        ExportRow row = ExportRow.of(ExportSubject.SPECIMEN, new Specimen());

        assertThat(ExportNavigations.follow(row, List.of("recordingUnit", "project"))).isEmpty();
    }

    @Test
    void follow_chainsSeveralNavigations() {
        ActionUnit project = new ActionUnit();
        RecordingUnit ru = unitOf(project);
        Specimen specimen = new Specimen();
        specimen.setRecordingUnit(ru);

        Optional<ExportRow> result = ExportNavigations.follow(
                ExportRow.of(ExportSubject.SPECIMEN, specimen), List.of("recordingUnit", "project"));

        assertThat(result).hasValueSatisfying(r -> assertThat(r.entity()).isSameAs(project));
    }

    @Test
    void follow_unknownName_isRejected() {
        ExportRow row = ExportRow.of(ExportSubject.RECORDING_UNIT, new RecordingUnit());

        List<String> steps1 = List.of("nope");

        assertThatThrownBy(() -> ExportNavigations.follow(row, steps1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void follow_onATechnicalRow_startsByAnEnd() {
        ActionUnit project = new ActionUnit();
        ExportRow unit1 = ExportRow.of(ExportSubject.RECORDING_UNIT, unitOf(project));
        ExportRow technical = ExportRow.technical(Map.of("unit1", unit1), Map.of());

        assertThat(ExportNavigations.follow(technical, List.of("unit1"))).containsSame(unit1);
        assertThat(ExportNavigations.follow(technical, List.of("unit1", "project")))
                .hasValueSatisfying(r -> assertThat(r.entity()).isSameAs(project));
    }

    @Test
    void follow_onATechnicalRow_withoutAnEnd_isRejected() {
        ExportRow technical = ExportRow.technical(Map.of(), Map.of());

        List<String> steps2 = List.of();

        assertThatThrownBy(() -> ExportNavigations.follow(technical, steps2))
                .isInstanceOf(IllegalArgumentException.class);
        List<String> steps3 = List.of("unit1");
        assertThatThrownBy(() -> ExportNavigations.follow(technical, steps3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void targetOf_entityPath() {
        assertThat(ExportNavigations.targetOf(ExportSubject.SPECIMEN, List.of("recordingUnit", "project")))
                .isEqualTo(ExportSubject.PROJECT);
        assertThat(ExportNavigations.targetOf(ExportSubject.PROJECT, List.of("mainLocation")))
                .isEqualTo(ExportSubject.SPATIAL_UNIT);
        assertThat(ExportNavigations.targetOf(ExportSubject.PROJECT, List.of())).isEqualTo(ExportSubject.PROJECT);
    }

    @Test
    void targetOf_unknownStep_isRejected() {
        List<String> steps4 = List.of("project");
        assertThatThrownBy(() -> ExportNavigations.targetOf(ExportSubject.SPATIAL_UNIT, steps4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void targetOf_technicalSource() {
        assertThat(ExportNavigations.targetOf(ExportTechnicalSource.STRATIGRAPHIC_RELATIONSHIP, List.of("unit1")))
                .isEqualTo(ExportSubject.RECORDING_UNIT);
        assertThat(ExportNavigations.targetOf(ExportTechnicalSource.STRATIGRAPHIC_RELATIONSHIP, List.of("unit2", "project")))
                .isEqualTo(ExportSubject.PROJECT);
        List<String> steps5 = List.of();
        assertThatThrownBy(() -> ExportNavigations.targetOf(ExportTechnicalSource.RECORDING_UNIT_HIERARCHY, steps5))
                .isInstanceOf(IllegalArgumentException.class);
        List<String> steps6 = List.of("unit1");
        assertThatThrownBy(() -> ExportNavigations.targetOf(ExportTechnicalSource.RECORDING_UNIT_HIERARCHY, steps6))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void names_listsTheNavigationsOfAnEntity() {
        assertThat(ExportNavigations.names(ExportSubject.SPECIMEN)).containsExactlyInAnyOrder("project", "recordingUnit");
        assertThat(ExportNavigations.names(ExportSubject.SPATIAL_UNIT)).isEmpty();
    }

    @Test
    void technicalSource_ofKey() {
        assertThat(ExportTechnicalSource.ofKey("STRATIGRAPHIC_RELATIONSHIP")).contains(ExportTechnicalSource.STRATIGRAPHIC_RELATIONSHIP);
        assertThat(ExportTechnicalSource.ofKey("DROP TABLE")).isEmpty();
        assertThat(ExportTechnicalSource.ofKey(null)).isEmpty();
    }
}
