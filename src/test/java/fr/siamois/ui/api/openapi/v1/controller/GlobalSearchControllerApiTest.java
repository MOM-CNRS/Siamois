package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.ui.api.openapi.v1.response.search.GlobalSearchResponse;
import fr.siamois.ui.api.openapi.v1.service.GlobalSearchService;
import fr.siamois.ui.api.openapi.v1.service.OrganizationListService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalSearchControllerApiTest {

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private OrganizationListService organizationListService;
    @Mock
    private GlobalSearchService globalSearchService;
    @InjectMocks
    private GlobalSearchControllerApi controller;

    @Test
    void search_resolvesTheCallerAndOrganizationThenDelegates() {
        ProjectApiCaller caller = new ProjectApiCaller(null, Set.of(7L), List.of());
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(7L);
        GlobalSearchResponse expected = new GlobalSearchResponse(List.of());
        when(projectApiService.requireCaller()).thenReturn(caller);
        when(organizationListService.requireListOrganization(caller, 7L)).thenReturn(institution);
        when(globalSearchService.search(caller, institution, "mur", 9L, List.of("projet:A"), List.of("find"), 5))
                .thenReturn(expected);

        GlobalSearchResponse response = controller.search(7L, "mur", 9L, List.of("projet:A"), List.of("find"), 5);

        assertThat(response).isSameAs(expected);
        verify(organizationListService).requireListOrganization(caller, 7L);
    }
}
