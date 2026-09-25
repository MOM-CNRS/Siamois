package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.actionunit.form.ActionUnitForm;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import fr.siamois.ui.table.definitions.ActionUnitTableColumnDefaults;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectAnswersProjectorTest {

    private final ProjectAnswersProjector projector = new ProjectAnswersProjector();

    private static String fieldId(fr.siamois.domain.models.form.customfield.CustomField field) {
        return String.valueOf(field.getId());
    }

    private static ConceptDTO concept(long id, String externalId) {
        ConceptDTO c = new ConceptDTO();
        c.setId(id);
        c.setExternalId(externalId);
        return c;
    }

    private static ActionUnitDTO project(long id) {
        ActionUnitDTO dto = new ActionUnitDTO();
        dto.setId(id);
        return dto;
    }

    @Test
    void resolveRequestedFieldIds_absentParam_meansNoProjection() {
        assertThat(projector.resolveRequestedFieldIds(null)).isNull();
        assertThat(projector.resolveRequestedFieldIds("  ")).isNull();
    }

    @Test
    void resolveRequestedFieldIds_defaultMatchesTheTableColumnDefaults() {
        assertThat(projector.resolveRequestedFieldIds("default"))
                .isEqualTo(ActionUnitTableColumnDefaults.defaultVisibleFieldIds())
                .contains(fieldId(ActionUnitForm.STATUS_FIELD), fieldId(ActionUnitForm.OA_CODE_FIELD))
                .doesNotContain(fieldId(ActionUnitForm.ZMIN_FIELD));
    }

    @Test
    void resolveRequestedFieldIds_allCoversEveryDetailsFormField() {
        Set<String> all = projector.resolveRequestedFieldIds("all");
        assertThat(all).contains(
                fieldId(ActionUnitForm.ZMIN_FIELD),
                fieldId(ActionUnitForm.STATUS_FIELD),
                fieldId(ActionUnitForm.NAME_FIELD));
    }

    /**
     * Un id inconnu est ignoré plutôt que rejeté : le catalogue de champs évolue, et une vue de tableau
     * enregistrée côté client ne doit pas se transformer en 400 après une migration.
     */
    @Test
    void resolveRequestedFieldIds_ignoresUnknownIds() {
        Set<String> ids = projector.resolveRequestedFieldIds(fieldId(ActionUnitForm.OA_CODE_FIELD) + ",999999, ");
        assertThat(ids).containsExactly(fieldId(ActionUnitForm.OA_CODE_FIELD));
    }

    @Test
    void project_readsScalarBindingsOffTheDto() {
        ActionUnitDTO dto = project(1L);
        dto.setOaCode("OA-2024-01");
        dto.setOpeningRate(0.42);
        dto.setVolumeCount(3);

        Map<String, Object> answers = projector.project(
                List.of(dto),
                Set.of(fieldId(ActionUnitForm.OA_CODE_FIELD),
                        fieldId(ActionUnitForm.OPENING_RATE_FIELD),
                        fieldId(ActionUnitForm.VOLUME_COUNT_FIELD)),
                Map.of()).get(1L);

        assertThat(answers).containsEntry(fieldId(ActionUnitForm.OA_CODE_FIELD), "OA-2024-01");
        assertThat(answers).containsEntry(fieldId(ActionUnitForm.OPENING_RATE_FIELD), 0.42);
        assertThat(answers).containsEntry(fieldId(ActionUnitForm.VOLUME_COUNT_FIELD), 3);
    }

    @Test
    void project_emitsResourceRefsForSelectOneAndSelectMultiple() {
        ActionUnitDTO dto = project(1L);
        dto.setStatus(concept(7L, "1234"));
        dto.setPeriods(new java.util.LinkedHashSet<>(List.of(concept(8L, "5678"))));
        SpatialUnitSummaryDTO place = new SpatialUnitSummaryDTO();
        place.setId(9L);
        place.setName("Lyon");
        dto.setMainLocation(place);

        Map<String, Object> answers = projector.project(
                List.of(dto),
                Set.of(fieldId(ActionUnitForm.STATUS_FIELD),
                        fieldId(ActionUnitForm.PERIODS_FIELD),
                        fieldId(ActionUnitForm.MAIN_LOCATION_FIELD)),
                Map.of(7L, "En cours", 8L, "Néolithique")).get(1L);

        assertThat(answers.get(fieldId(ActionUnitForm.STATUS_FIELD)))
                .isEqualTo(new ResourceRef("7", "concepts", "En cours"));
        assertThat(answers.get(fieldId(ActionUnitForm.PERIODS_FIELD)))
                .isEqualTo(List.of(new ResourceRef("8", "concepts", "Néolithique")));
        assertThat(answers.get(fieldId(ActionUnitForm.MAIN_LOCATION_FIELD)))
                .isEqualTo(new ResourceRef("9", "spatial-units", "Lyon"));
    }

    /**
     * Repli identique à {@code LabelService.findLabelOf} quand le lot n'a rien résolu pour ce concept.
     */
    @Test
    void project_fallsBackToExternalIdWhenALabelIsMissing() {
        ActionUnitDTO dto = project(1L);
        dto.setStatus(concept(7L, "1234"));

        Map<String, Object> answers = projector.project(
                List.of(dto), Set.of(fieldId(ActionUnitForm.STATUS_FIELD)), Map.of()).get(1L);

        assertThat(answers.get(fieldId(ActionUnitForm.STATUS_FIELD)))
                .isEqualTo(new ResourceRef("7", "concepts", "[1234]"));
    }

    @Test
    void project_emitsNullForAnUnsetBindingRatherThanOmittingTheKey() {
        Map<String, Object> answers = projector.project(
                List.of(project(1L)), Set.of(fieldId(ActionUnitForm.OA_CODE_FIELD)), Map.of()).get(1L);

        assertThat(answers).containsKey(fieldId(ActionUnitForm.OA_CODE_FIELD));
        assertThat(answers.get(fieldId(ActionUnitForm.OA_CODE_FIELD))).isNull();
    }

    @Test
    void project_withoutRequestedFields_returnsNothing() {
        assertThat(projector.project(List.of(project(1L)), null, Map.of())).isEmpty();
        assertThat(projector.project(List.of(project(1L)), Set.of(), Map.of())).isEmpty();
    }

    @Test
    void collectConcepts_gathersSingleAndMultiValuedConceptsForOneBatch() {
        ActionUnitDTO dto = project(1L);
        dto.setStatus(concept(7L, "1234"));
        dto.setSubjects(new java.util.LinkedHashSet<>(List.of(concept(8L, "5678"))));

        List<ConceptDTO> concepts = projector.collectConcepts(
                List.of(dto),
                Set.of(fieldId(ActionUnitForm.STATUS_FIELD), fieldId(ActionUnitForm.SUBJECTS_FIELD)));

        assertThat(concepts).extracting(ConceptDTO::getId).containsExactlyInAnyOrder(7L, 8L);
    }
}
