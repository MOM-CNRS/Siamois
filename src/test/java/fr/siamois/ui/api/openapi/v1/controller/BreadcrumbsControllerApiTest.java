package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.api.openapi.v1.resource.breadcrumb.BreadcrumbResource;
import fr.siamois.ui.api.openapi.v1.resource.breadcrumb.BreadcrumbsResource;
import fr.siamois.ui.api.openapi.v1.response.BreadcrumbsResponse;
import fr.siamois.ui.api.openapi.v1.service.EntityBreadcrumbsService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BreadcrumbsControllerApiTest {

    @Mock private ProjectApiService projectApiService;
    @Mock private EntityBreadcrumbsService entityBreadcrumbsService;

    @InjectMocks
    private BreadcrumbsControllerApi controller;

    private final ProjectApiCaller caller = new ProjectApiCaller(null, Set.of(10L), List.of());

    @Test
    void getBreadcrumbs_answersWithTheServicePathsForAKnownCollection() {
        BreadcrumbsResource resource = new BreadcrumbsResource(
                List.of(List.of(new BreadcrumbResource("project", "1", "Fouille"))), false);
        when(projectApiService.requireCaller()).thenReturn(caller);
        when(entityBreadcrumbsService.findBreadcrumbs(EntityBreadcrumbsService.Kind.RECORDING_UNIT, 5L, Set.of(10L)))
                .thenReturn(resource);

        ResponseEntity<BreadcrumbsResponse> response = controller.getBreadcrumbs("recording-units", 5L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isEqualTo(resource);
    }

    @Test
    void getBreadcrumbs_unknownCollectionIsNotFound() {
        when(projectApiService.requireCaller()).thenReturn(caller);

        assertThatThrownBy(() -> controller.getBreadcrumbs("places", 5L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(entityBreadcrumbsService, never()).findBreadcrumbs(any(), anyLong(), any());
    }
}
