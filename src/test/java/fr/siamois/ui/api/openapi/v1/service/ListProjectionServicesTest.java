package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.domain.services.vocabulary.ConceptLabelBatchResolver;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectResponseMapper;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import fr.siamois.ui.api.openapi.v1.response.project.ProjectListResponse;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The batch half of a list page: labels resolved once, then the projection of each row's answers. */
class ListProjectionServicesTest {

    private final ConceptLabelBatchResolver labels = mock(ConceptLabelBatchResolver.class);
    private final MultiValueAnswers multiValue = mock(MultiValueAnswers.class);
    private final AdditionalAnswersListProjector additional = mock(AdditionalAnswersListProjector.class);

    private static ConceptDTO concept(long id) {
        ConceptDTO c = new ConceptDTO();
        c.setId(id);
        return c;
    }

    // ---------- phases ----------

    @Test
    void phases_buildResolvesTheLabelsOfTheTypesAndProjectsTheRows() {
        PhaseAnswersProjector projector = mock(PhaseAnswersProjector.class);
        PhaseListProjectionService service = new PhaseListProjectionService(projector, labels, multiValue, additional);
        PhaseDTO phase = new PhaseDTO();
        phase.setId(1L);
        phase.setType(concept(9));
        List<PhaseDTO> rows = Arrays.asList(phase, null, new PhaseDTO());
        Set<String> fieldIds = Set.of("-1");
        Map<Long, String> resolved = Map.of(9L, "Type");
        Map<Long, Map<String, Object>> projected = Map.of(1L, Map.of("-1", "x"));
        when(projector.resolveRequestedFieldIds("all")).thenReturn(fieldIds);
        when(projector.collectConcepts(rows, fieldIds)).thenReturn(List.of(concept(3)));
        when(labels.resolveLabels(any(Collection.class), eq("fr"))).thenReturn(resolved);
        when(projector.project(rows, fieldIds, resolved)).thenReturn(projected);
        when(additional.merge(projected, CustomFieldAnswerService.ListOwner.PHASE, List.of(1L), "all", fieldIds, "fr")).thenReturn(projected);
        when(multiValue.shape(eq(Phase.class), eq(projected), eq(fieldIds), anyInt(), eq("fr"))).thenReturn(projected);

        PhaseListProjectionService.PhaseListProjection projection = service.build(rows, "all", "fr");

        assertThat(projection.resolvedLabels()).isEqualTo(resolved);
        assertThat(projection.answersFor(1L)).containsEntry("-1", "x");
        assertThat(projection.answersFor(2L)).isNull();
    }

    @Test
    void phases_buildOfNothingIsEmpty_andBuildOneDelegates() {
        PhaseAnswersProjector projector = mock(PhaseAnswersProjector.class);
        PhaseListProjectionService service = new PhaseListProjectionService(projector, labels, multiValue, additional);

        assertThat(service.build(null, "all", "fr").resolvedLabels()).isEmpty();
        assertThat(service.build(List.of(), "all", "fr").resolvedLabels()).isEmpty();
        assertThat(service.buildOne(null, "fr").resolvedLabels()).isEmpty();
        verify(projector, never()).project(any(), any(), any());

        PhaseDTO phase = new PhaseDTO();
        phase.setId(4L);
        service.buildOne(phase, "fr");
        verify(projector).project(eq(List.of(phase)), any(), any());
    }

    // ---------- containers ----------

    @Test
    void containers_buildResolvesTheLabelsOfTheTypesAndProjectsTheRows() {
        ContainerAnswersProjector projector = mock(ContainerAnswersProjector.class);
        ContainerListProjectionService service = new ContainerListProjectionService(projector, labels, multiValue, additional);
        ContainerDTO container = new ContainerDTO();
        container.setId(1L);
        container.setType(concept(9));
        List<ContainerDTO> rows = Arrays.asList(container, null);
        Set<String> fieldIds = Set.of("-1");
        Map<Long, String> resolved = Map.of(9L, "Type");
        Map<Long, Map<String, Object>> projected = Map.of(1L, Map.of("-1", "x"));
        when(projector.resolveRequestedFieldIds("all")).thenReturn(fieldIds);
        when(projector.collectConcepts(rows, fieldIds)).thenReturn(List.of());
        when(labels.resolveLabels(any(Collection.class), eq("fr"))).thenReturn(resolved);
        when(projector.project(rows, fieldIds, resolved)).thenReturn(projected);
        when(additional.merge(projected, CustomFieldAnswerService.ListOwner.CONTAINER, List.of(1L), "all", fieldIds, "fr")).thenReturn(projected);
        when(multiValue.shape(eq(Container.class), eq(projected), eq(fieldIds), anyInt(), eq("fr"))).thenReturn(projected);

        ContainerListProjectionService.ContainerListProjection projection = service.build(rows, "all", "fr");

        assertThat(projection.answersFor(1L)).containsEntry("-1", "x");
    }

    @Test
    void containers_buildOfNothingIsEmpty_andBuildOneDelegates() {
        ContainerAnswersProjector projector = mock(ContainerAnswersProjector.class);
        ContainerListProjectionService service = new ContainerListProjectionService(projector, labels, multiValue, additional);

        assertThat(service.build(List.of(), "all", "fr").resolvedLabels()).isEmpty();
        assertThat(service.buildOne(null, "fr").resolvedLabels()).isEmpty();

        ContainerDTO container = new ContainerDTO();
        container.setId(4L);
        service.buildOne(container, "fr");
        verify(projector).project(eq(List.of(container)), any(), any());
    }

    // ---------- additional answers ----------

    @Test
    void additionalAnswers_areMergedOntoTheProjectedOnesAndEveryRowGetsAMap() {
        CustomFieldAnswerService answers = mock(CustomFieldAnswerService.class);
        FieldAnswerWireService wire = mock(FieldAnswerWireService.class);
        AdditionalAnswersListProjector projector = new AdditionalAnswersListProjector(answers, wire);
        CustomField field = new CustomFieldText();
        Map<CustomField, CustomFieldAnswerViewModel> loaded = Map.of(field, mock(CustomFieldAnswerViewModel.class));
        when(answers.loadAdditionalFieldAnswers(CustomFieldAnswerService.ListOwner.PHASE, List.of(1L, 2L), Set.of(77L)))
                .thenReturn(Map.of(1L, loaded));
        when(wire.additionalAnswers(loaded, "fr")).thenReturn(Map.of("77", "valeur"));

        Map<Long, Map<String, Object>> merged = projector.merge(Map.of(1L, Map.of("-1", "x")),
                CustomFieldAnswerService.ListOwner.PHASE, List.of(1L, 2L), "77, all, -1, ,unknown", Set.of("-1"), "fr");

        assertThat(merged.get(1L)).containsEntry("-1", "x").containsEntry("77", "valeur");
        assertThat(merged.get(2L)).isEmpty();
    }

    @Test
    void additionalAnswers_nothingAskedNothingLoaded() {
        CustomFieldAnswerService answers = mock(CustomFieldAnswerService.class);
        AdditionalAnswersListProjector projector = new AdditionalAnswersListProjector(answers, mock(FieldAnswerWireService.class));
        Map<Long, Map<String, Object>> projected = Map.of(1L, Map.of("-1", "x"));

        assertThat(projector.merge(projected, CustomFieldAnswerService.ListOwner.PHASE, List.of(1L), null, Set.of(), "fr")).isSameAs(projected);
        assertThat(projector.merge(projected, CustomFieldAnswerService.ListOwner.PHASE, List.of(1L), " ", null, "fr")).isSameAs(projected);
        assertThat(projector.merge(projected, CustomFieldAnswerService.ListOwner.PHASE, List.of(), "77", null, "fr")).isSameAs(projected);
        assertThat(projector.merge(projected, CustomFieldAnswerService.ListOwner.PHASE, List.of(1L), "-1,all", Set.of("-1"), "fr")).isSameAs(projected);
        verify(answers, never()).loadAdditionalFieldAnswers(any(), any(), any());
    }

    // ---------- projects ----------

    @Test
    void projectListAssembler_buildsEachResourceWithItsPermissionsAndBookmark() {
        ProjectApiService api = mock(ProjectApiService.class);
        ProjectResponseMapper mapper = mock(ProjectResponseMapper.class);
        ProjectListProjectionService projections = mock(ProjectListProjectionService.class);
        ProjectListAssembler assembler = new ProjectListAssembler(api, mapper, projections);

        ActionUnitDTO first = new ActionUnitDTO();
        first.setId(1L);
        ActionUnitDTO second = new ActionUnitDTO();
        second.setId(2L);
        AccessibleProjectForApi row1 = new AccessibleProjectForApi(first, 0, 0);
        AccessibleProjectForApi row2 = new AccessibleProjectForApi(second, 0, 0);
        List<AccessibleProjectForApi> content = List.of(row1, row2);
        ProjectResourcePermissions granted = ProjectResourcePermissions.of(true);
        when(api.permissionsFor(any(), eq((Collection<AccessibleProjectForApi>) content))).thenReturn(Map.of(1L, granted));
        when(api.bookmarkedResourceUris(any(), eq((Collection<AccessibleProjectForApi>) content), eq("fr")))
                .thenReturn(Set.of(ProjectApiService.actionUnitResourceUri(2L)));
        ProjectListProjectionService.ProjectListProjection projection = new ProjectListProjectionService.ProjectListProjection(
                Map.of(), Map.of(1L, Map.of("k", "v")));
        when(projections.build(content, "all", "fr")).thenReturn(projection);
        ProjectResource resource1 = new ProjectResource();
        ProjectResource resource2 = new ProjectResource();
        when(mapper.toResource(eq(row1), eq("fr"), eq(granted), eq(false), any(), eq(Map.of("k", "v")))).thenReturn(resource1);
        when(mapper.toResource(eq(row2), eq("fr"), any(ProjectResourcePermissions.class), eq(true), any(), isNull())).thenReturn(resource2);

        ProjectListResponse response = assembler.assemble(null, new PageImpl<>(content, PageRequest.of(0, 2), 7), "all", "fr", 2, 0);

        assertThat(response.getData()).containsExactly(resource1, resource2);
        assertThat(response.getMeta().total()).isEqualTo(7);
    }
}
