package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.dto.TreePage;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectResponseMapper;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import fr.siamois.ui.api.openapi.v1.response.project.ProjectListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** A page of projects as a list response: the same for the main list and a place's projects, tree flags included. */
@ExtendWith(MockitoExtension.class)
class ProjectListAssemblerTest {

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private ProjectResponseMapper projectResponseMapper;
    @Mock
    private ProjectListProjectionService projectListProjectionService;

    private ProjectListAssembler assembler;
    private ProjectApiCaller caller;
    private AccessibleProjectForApi row;

    @BeforeEach
    void setUp() {
        assembler = new ProjectListAssembler(projectApiService, projectResponseMapper, projectListProjectionService);
        caller = new ProjectApiCaller(new PersonDTO(), Set.of(10L), List.of());
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(4L);
        row = new AccessibleProjectForApi(project, 0L, 1L);
        when(projectApiService.permissionsFor(any(), any(java.util.Collection.class)))
                .thenReturn(Map.of(4L, ProjectResourcePermissions.of(true)));
        when(projectApiService.bookmarkedResourceUris(any(), any(), anyString())).thenReturn(Set.of());
        when(projectListProjectionService.build(any(), any(), anyString()))
                .thenReturn(new ProjectListProjectionService.ProjectListProjection(Map.of(), Map.of()));
        when(projectResponseMapper.toResource(eq(row), anyString(), any(), anyBoolean(), any(), any()))
                .thenAnswer(inv -> new ProjectResource());
    }

    @Test
    void aPlainPage_carriesNoTreeFlagsAndNoMatchCount() {
        Page<AccessibleProjectForApi> page = new PageImpl<>(List.of(row), PageRequest.of(0, 20), 30);

        ProjectListResponse response = assembler.assemble(caller, page, null, "fr", 20, 0);

        assertThat(response.getData().get(0).getHasChildren()).isNull();
        assertThat(response.getData().get(0).getMatch()).isNull();
        assertThat(response.getMeta().total()).isEqualTo(30L);
        assertThat(response.getMeta().matchCount()).isNull();
    }

    @Test
    void aTreePage_flagsEachRowAndCarriesTheMatchCount() {
        // Project 4 has sub-projects but is only context: the match is another one (9).
        TreePage<AccessibleProjectForApi> page =
                new TreePage<>(List.of(row), PageRequest.of(0, 20), 30, 12, Set.of(4L), Set.of(9L));

        ProjectListResponse response = assembler.assemble(caller, page, null, "fr", 20, 0);

        assertThat(response.getData().get(0).getHasChildren()).isTrue();
        assertThat(response.getData().get(0).getMatch()).isFalse();
        assertThat(response.getMeta().total()).isEqualTo(30L);
        assertThat(response.getMeta().matchCount()).isEqualTo(12L);
    }
}
