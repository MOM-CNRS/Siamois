package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.uiview.HomeAccess;
import fr.siamois.domain.models.uiview.UiTableView;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.view.TableViewState;
import fr.siamois.infrastructure.database.HomeAccessRepository;
import fr.siamois.ui.api.openapi.v1.request.view.HomeAccessOrderRequest;
import fr.siamois.ui.api.openapi.v1.request.view.HomeAccessRequest;
import fr.siamois.ui.api.openapi.v1.request.view.ViewWriteRequest;
import fr.siamois.ui.api.openapi.v1.resource.view.HomeAccessListResponse;
import fr.siamois.ui.api.openapi.v1.resource.view.HomeAccessResource;
import fr.siamois.ui.api.openapi.v1.resource.view.ViewResource;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HomeAccessOpenApiServiceTest {

    @Mock private HomeAccessRepository homeAccessRepository;
    @Mock private ViewOpenApiService viewService;
    @Mock private ProfilePermissionService profilePermissionService;
    @Mock private EntityManager entityManager;
    @InjectMocks private HomeAccessOpenApiService service;

    private UserInfo user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(100L);
        PersonDTO person = new PersonDTO();
        person.setId(7L);
        user = new UserInfo(institution, person, "fr");
    }

    private static HomeAccess access(long id, long viewId, String type, String title) {
        UiTableView view = new UiTableView();
        view.setId(viewId);
        view.setResourceType(type);
        view.setTitle(title);
        HomeAccess access = new HomeAccess();
        access.setId(id);
        access.setView(view);
        return access;
    }

    @Test
    void list_givesTheCardsWithTheirViewAndWhetherTheCallerEdits() {
        when(homeAccessRepository.findAllOf(100L)).thenReturn(List.of(access(1, 11, "recordingUnit", "US – Couche")));
        when(profilePermissionService.hasDefaultViewEditPermission(user, null)).thenReturn(true);

        HomeAccessListResponse response = service.list(user);

        assertTrue(response.canEdit());
        assertEquals(List.of(new HomeAccessResource(1L, 11L, "recordingUnit", "US – Couche", "US – Couche", null)), response.data());
    }

    @Test
    void create_makesTheViewThenTheCardAtTheEnd() {
        when(profilePermissionService.hasDefaultViewEditPermission(user, null)).thenReturn(true);
        TableViewState state = new TableViewState();
        ViewResource created = mock(ViewResource.class);
        when(created.id()).thenReturn(11L);
        when(viewService.create(any(UserInfo.class), any(ViewWriteRequest.class))).thenReturn(created);
        when(homeAccessRepository.maxPosition(100L)).thenReturn(3);
        when(homeAccessRepository.save(any(HomeAccess.class))).thenAnswer(call -> {
            HomeAccess saved = call.getArgument(0);
            saved.setId(5L);
            return saved;
        });
        HomeAccessRequest request = new HomeAccessRequest(100L, null, "recordingUnit", "US – Couche", "Couche", state);

        HomeAccessResource result = service.create(user, request);

        assertEquals(new HomeAccessResource(5L, 11L, "recordingUnit", "US – Couche", "Couche", null), result);
        ArgumentCaptor<ViewWriteRequest> view = ArgumentCaptor.forClass(ViewWriteRequest.class);
        verify(viewService).create(any(UserInfo.class), view.capture());
        assertEquals("recordingUnit", view.getValue().resourceType());
        assertEquals(state, view.getValue().state());
        ArgumentCaptor<HomeAccess> saved = ArgumentCaptor.forClass(HomeAccess.class);
        verify(homeAccessRepository).save(saved.capture());
        assertEquals(4, saved.getValue().getPosition());
    }

    @Test
    void create_withAnExistingView_linksItWithoutCreatingOne_andRefusesAViewAlreadyFeatured() {
        when(profilePermissionService.hasDefaultViewEditPermission(user, null)).thenReturn(true);
        ViewResource existing = mock(ViewResource.class);
        when(existing.id()).thenReturn(11L);
        when(existing.resourceType()).thenReturn("find");
        when(existing.title()).thenReturn("Mobiliers");
        when(viewService.get(user, 11L)).thenReturn(existing);
        when(homeAccessRepository.save(any(HomeAccess.class))).thenAnswer(call -> call.getArgument(0));
        HomeAccessRequest request = new HomeAccessRequest(100L, 11L, null, null, null, null);

        HomeAccessResource result = service.create(user, request);

        assertEquals(11L, result.viewId());
        assertEquals("Mobiliers", result.label());
        verify(viewService, never()).create(any(UserInfo.class), any(ViewWriteRequest.class));

        when(homeAccessRepository.existsByViewIdAndInstitutionId(11L, 100L)).thenReturn(true);
        assertThrows(ResponseStatusException.class, () -> service.create(user, request));
    }

    @Test
    void reorder_numbersTheNamedCardsInOrder_thenTheOthers() {
        when(profilePermissionService.hasDefaultViewEditPermission(user, null)).thenReturn(true);
        HomeAccess a = access(1, 11, "find", "A");
        HomeAccess b = access(2, 12, "find", "B");
        HomeAccess c = access(3, 13, "find", "C");
        when(homeAccessRepository.findAllOf(100L)).thenReturn(List.of(a, b, c));

        service.reorder(user, new HomeAccessOrderRequest(100L, List.of(3L, 1L)));

        assertEquals(1, c.getPosition());
        assertEquals(2, a.getPosition());
        assertEquals(3, b.getPosition());
        verify(homeAccessRepository).saveAll(List.of(a, b, c));
    }

    @Test
    void reorder_withoutIds_keepsTheCurrentOrder_andNeedsThePermission() {
        when(profilePermissionService.hasDefaultViewEditPermission(user, null)).thenReturn(true);
        HomeAccess a = access(1, 11, "find", "A");
        HomeAccess b = access(2, 12, "find", "B");
        when(homeAccessRepository.findAllOf(100L)).thenReturn(List.of(a, b));

        service.reorder(user, new HomeAccessOrderRequest(100L, null));

        assertEquals(1, a.getPosition());
        assertEquals(2, b.getPosition());

        when(profilePermissionService.hasDefaultViewEditPermission(user, null)).thenReturn(false);
        HomeAccessOrderRequest request = new HomeAccessOrderRequest(100L, List.of(1L));
        assertThrows(ResponseStatusException.class, () -> service.reorder(user, request));
    }

    @Test
    void list_labelsTheCardWithItsOwnLabelElseTheViewTitle() {
        HomeAccess labelled = access(1, 11, "recordingUnit", "US – Couche");
        labelled.setLabel("Couche");
        HomeAccess plain = access(2, 12, "find", "Mobiliers");
        labelled.getView().setState(new TableViewState());
        labelled.getView().getState().setListFilters(java.util.Map.of("type", "x"));
        when(homeAccessRepository.findAllOf(100L)).thenReturn(List.of(labelled, plain));

        List<HomeAccessResource> data = service.list(user).data();

        assertEquals("Couche", data.get(0).label());
        assertEquals(java.util.Map.of("type", "x"), data.get(0).filters());
        assertEquals("Mobiliers", data.get(1).label());
    }

    @Test
    void create_withoutLabel_usesTheTitleAndRejectsATooLongOne() {
        when(profilePermissionService.hasDefaultViewEditPermission(user, null)).thenReturn(true);
        ViewResource created = mock(ViewResource.class);
        when(created.id()).thenReturn(11L);
        when(viewService.create(any(UserInfo.class), any(ViewWriteRequest.class))).thenReturn(created);
        when(homeAccessRepository.save(any(HomeAccess.class))).thenAnswer(call -> call.getArgument(0));

        assertEquals("Mobiliers", service.create(user, new HomeAccessRequest(100L, null, "find", "Mobiliers", " ", new TableViewState())).label());

        HomeAccessRequest tooLong = new HomeAccessRequest(100L, null, "find", "Mobiliers", "x".repeat(201), new TableViewState());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class, () -> service.create(user, tooLong)).getStatusCode());
    }

    @Test
    void create_withoutThePermission_isForbiddenAndCreatesNothing() {
        when(profilePermissionService.hasDefaultViewEditPermission(user, null)).thenReturn(false);
        HomeAccessRequest request = new HomeAccessRequest(100L, null, "recordingUnit", "x", "x", new TableViewState());

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.create(user, request));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(viewService);
        verify(homeAccessRepository, never()).save(any());
    }

    @Test
    void delete_removesTheCard() {
        when(profilePermissionService.hasDefaultViewEditPermission(user, null)).thenReturn(true);
        HomeAccess access = access(1, 11, "recordingUnit", "x");
        when(homeAccessRepository.findByIdAndInstitutionId(1L, 100L)).thenReturn(Optional.of(access));

        service.delete(user, 1L);

        verify(homeAccessRepository).delete(access);
    }

    @Test
    void delete_unknownCard_is404() {
        when(profilePermissionService.hasDefaultViewEditPermission(user, null)).thenReturn(true);
        when(homeAccessRepository.findByIdAndInstitutionId(9L, 100L)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.delete(user, 9L));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void delete_withoutThePermission_isForbidden() {
        when(profilePermissionService.hasDefaultViewEditPermission(user, null)).thenReturn(false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.delete(user, 1L));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertFalse(error.getReason().isBlank());
        verify(homeAccessRepository, never()).delete(any());
    }
}
