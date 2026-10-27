package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.api.openapi.v1.mapper.RecordingUnitResponseMapper;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordingUnitListAssemblerTest {

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private RecordingUnitResponseMapper recordingUnitResponseMapper;
    @Mock
    private RecordingUnitListProjectionService recordingUnitListProjectionService;
    @Mock
    private ResourceBookmarkService resourceBookmarkService;
    @Mock
    private FirstImageService firstImageService;

    @InjectMocks
    private RecordingUnitListAssembler assembler;

    private ProjectApiCaller caller;
    private InstitutionDTO institution;

    @BeforeEach
    void setUp() {
        caller = new ProjectApiCaller(new PersonDTO(), Set.of(10L), List.of());
        institution = new InstitutionDTO();
        institution.setId(10L);
    }

    private RecordingUnitDTO ru(long id) {
        RecordingUnitDTO dto = new RecordingUnitDTO();
        dto.setId(id);
        dto.setCreatedByInstitution(institution);
        return dto;
    }

    @Test
    void assemble_appliesOnePageWidePermission_projection_andBookmarks() {
        RecordingUnitDTO first = ru(1L);
        Page<RecordingUnitDTO> page = new PageImpl<>(List.of(first), PageRequest.of(0, 10), 11L);
        when(projectApiService.canEditRecordingUnitsForProject(caller, "6", "fr")).thenReturn(true);
        when(recordingUnitListProjectionService.build(List.of(first), "default", "fr"))
                .thenReturn(new RecordingUnitListProjectionService.RecordingUnitListProjection(
                        Map.of(), Map.of(1L, Map.of("8", "x"))));
        RecordingUnitResource resource = new RecordingUnitResource();
        when(recordingUnitResponseMapper.convert(first)).thenReturn(resource);

        RecordingUnitListResponse response = assembler.assemble(caller, page, 6L, "default", "fr", 10, 0);

        assertThat(response.getData()).containsExactly(resource);
        assertThat(resource.getPermissions().canEdit()).isTrue();
        assertThat(resource.getAnswers()).containsEntry("8", "x");
        assertThat(response.getMeta().total()).isEqualTo(11L);
        verify(resourceBookmarkService).markBookmarked(caller.person(), institution, List.of(resource), "fr");
        // The page's first images are looked up once, for the page's rows.
        verify(firstImageService).markRecordingUnits(List.of(resource));
    }

    @Test
    void assemble_withoutProject_isReadOnly_andAnEmptyPageSkipsBookmarks() {
        when(recordingUnitListProjectionService.build(List.of(), null, "fr"))
                .thenReturn(RecordingUnitListProjectionService.RecordingUnitListProjection.empty());

        RecordingUnitListResponse response = assembler.assemble(caller, Page.empty(), null, null, "fr", 10, 0);

        assertThat(response.getData()).isEmpty();
        verify(projectApiService, never()).canEditRecordingUnitsForProject(any(), anyString(), anyString());
        verify(resourceBookmarkService, never()).markBookmarked(any(), any(InstitutionDTO.class), any(List.class), any());
    }

    @Test
    void assemble_aTreePageCarriesItsMatchCountAndEachRowItsTreeFlags() {
        RecordingUnitDTO first = ru(1L);
        first.setTreeHasChildren(true);
        first.setTreeMatch(false);
        Page<RecordingUnitDTO> page = new fr.siamois.dto.TreePage<>(List.of(first), PageRequest.of(0, 10), 11L, 37L);
        when(recordingUnitListProjectionService.build(any(), any(), any()))
                .thenReturn(RecordingUnitListProjectionService.RecordingUnitListProjection.empty());
        when(recordingUnitResponseMapper.convert(first)).thenReturn(new RecordingUnitResource());

        RecordingUnitListResponse response = assembler.assemble(caller, page, null, null, "fr", 10, 0);

        assertThat(response.getData().get(0).getHasChildren()).isTrue();
        assertThat(response.getData().get(0).getMatch()).isFalse();
        // The page counts its roots; the match count is the units that match.
        assertThat(response.getMeta().total()).isEqualTo(11L);
        assertThat(response.getMeta().matchCount()).isEqualTo(37L);
    }
}
