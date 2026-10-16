package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.infrastructure.database.repositories.misc.GlobalSearchRow;
import fr.siamois.infrastructure.database.repositories.misc.SearchRepository;
import fr.siamois.ui.api.openapi.v1.response.search.GlobalSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalSearchServiceTest {

    @Mock
    private SearchRepository searchRepository;
    @Mock
    private OrganizationListService organizationListService;
    @InjectMocks
    private GlobalSearchService service;

    private final ProjectApiCaller caller = new ProjectApiCaller(null, java.util.Set.of(7L), List.of());
    private InstitutionDTO institution;

    @BeforeEach
    void setUp() {
        institution = new InstitutionDTO();
        institution.setId(7L);
    }

    @Test
    void search_groupsRowsBySectionInDisplayOrderWithTotals() {
        when(organizationListService.visibleProjectIdsOrNull(caller, institution)).thenReturn(null);
        when(searchRepository.searchSections("abc", 7L, null, null, null, 5)).thenReturn(List.of(
                new GlobalSearchRow("place", 3L, "Abbaye", null, null, 12),
                new GlobalSearchRow("recordingUnit", 1L, "UE-1", "P1", 9L, 40),
                new GlobalSearchRow("recordingUnit", 2L, "UE-2", "P1", 9L, 40)));

        GlobalSearchResponse response = service.search(caller, institution, " abc ", null, null, null, 5);

        assertThat(response.sections()).extracting(GlobalSearchResponse.Section::type)
                .containsExactly("recordingUnit", "place");
        assertThat(response.sections().get(0).total()).isEqualTo(40);
        assertThat(response.sections().get(0).items()).hasSize(2);
    }

    @Test
    void search_blankInputWithoutFilterSkipsTheDatabase() {
        assertThat(service.search(caller, institution, "  ", null, null, null, 5).sections()).isEmpty();
        verify(searchRepository, never()).searchSections(any(), anyLong(), any(), any(), any(), anyInt());
    }

    @Test
    void search_implicitProjectRestrictsToThatProject() {
        when(organizationListService.visibleProjectIdsOrNull(caller, institution)).thenReturn(null);
        when(searchRepository.searchSections("x", 7L, List.of(9L), null, null, 5)).thenReturn(List.of());

        service.search(caller, institution, "x", 9L, null, null, 5);

        verify(searchRepository).searchSections("x", 7L, List.of(9L), null, null, 5);
    }

    @Test
    void search_explicitProjectFilterOverridesTheImplicitProject() {
        when(searchRepository.findProjectIdsByRef("ABC", 7L)).thenReturn(List.of(4L));
        when(organizationListService.visibleProjectIdsOrNull(caller, institution)).thenReturn(null);
        when(searchRepository.searchSections("x", 7L, List.of(4L), null, null, 5)).thenReturn(List.of());

        service.search(caller, institution, "x", 9L, List.of("projet:ABC"), null, 5);

        verify(searchRepository).searchSections("x", 7L, List.of(4L), null, null, 5);
    }

    @Test
    void search_unknownProjectReferenceYieldsNoResults() {
        when(searchRepository.findProjectIdsByRef(eq("nope"), anyLong())).thenReturn(List.of());

        assertThat(service.search(caller, institution, "x", null, List.of("projet:nope"), null, 5).sections()).isEmpty();
        verify(searchRepository, never()).searchSections(any(), anyLong(), any(), any(), any(), anyInt());
    }

    @Test
    void search_memberWithoutVisibleProjectsSeesNothing() {
        when(organizationListService.visibleProjectIdsOrNull(caller, institution)).thenReturn(List.of());

        assertThat(service.search(caller, institution, "x", null, null, null, 5).sections()).isEmpty();
    }

    @Test
    void search_rejectsUnknownKeywordTypeAndLimit() {
        assertThatThrownBy(() -> service.search(caller, institution, "x", null, List.of("couleur:rouge"), null, 5))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.search(caller, institution, "x", null, null, List.of("martian"), 5))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.search(caller, institution, "x", null, null, null, 0))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.search(caller, institution, "x", null, null, null, 99))
                .isInstanceOf(ResponseStatusException.class);
    }
}
