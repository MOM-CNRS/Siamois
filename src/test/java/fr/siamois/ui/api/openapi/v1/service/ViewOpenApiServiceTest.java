package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.uiview.UiTableView;
import fr.siamois.domain.models.uiview.UiViewKind;
import fr.siamois.domain.models.uiview.UserViewState;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.view.TableViewState;
import fr.siamois.infrastructure.database.UiViewRepository;
import fr.siamois.infrastructure.database.UserViewStateRepository;
import fr.siamois.infrastructure.database.repositories.BookmarkRepository;
import fr.siamois.ui.api.openapi.v1.request.view.DefaultViewRequest;
import fr.siamois.ui.api.openapi.v1.request.view.ViewStateRequest;
import fr.siamois.ui.api.openapi.v1.request.view.ViewWriteRequest;
import fr.siamois.ui.api.openapi.v1.resource.view.DefaultViewResponse;
import fr.siamois.ui.api.openapi.v1.resource.view.ViewListResponse;
import fr.siamois.ui.api.openapi.v1.resource.view.ViewResource;
import fr.siamois.ui.api.openapi.v1.resource.view.ViewStateResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ViewOpenApiServiceTest {

    private static final long ORG = 5L;
    private static final long ME = 7L;

    @Mock UiViewRepository uiViewRepository;
    @Mock UserViewStateRepository userViewStateRepository;
    @Mock BookmarkRepository bookmarkRepository;
    @Mock ProfilePermissionService profilePermissionService;
    @Mock EntityManager entityManager;

    @InjectMocks ViewOpenApiService service;

    private UserInfo me;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(ORG);
        PersonDTO person = new PersonDTO();
        person.setId(ME);
        me = new UserInfo(institution, person, "fr");
    }

    private static UiTableView view(long id, long ownerId, UiViewKind kind) {
        Person owner = new Person();
        owner.setId(ownerId);
        owner.setName("Ada");
        owner.setLastname("Lovelace");
        UiTableView v = new UiTableView();
        v.setId(id);
        v.setOwner(owner);
        v.setKind(kind);
        v.setTitle("t");
        v.setState(new TableViewState());
        return v;
    }

    private static ViewWriteRequest write(String title) {
        return new ViewWriteRequest(ORG, "recordingUnit", title, null, new TableViewState());
    }

    @Test
    void create_stampsCurrentVersionAndOwner() {
        when(entityManager.getReference(Person.class, ME)).thenReturn(new Person());
        when(entityManager.getReference(Institution.class, ORG)).thenReturn(new Institution());
        when(uiViewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ViewResource created = service.create(me, write("  Mes UE  "));

        assertThat(created.title()).isEqualTo("Mes UE");
        assertThat(created.kind()).isEqualTo("USER");
        assertThat(created.state().getVersion()).isEqualTo(TableViewState.CURRENT_VERSION);
    }

    @Test
    void create_rejectsBlankTitle() {
        ViewWriteRequest blank = write(" ");

        assertThatThrownBy(() -> service.create(me, blank))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(uiViewRepository);
    }

    @Test
    void update_byAnotherUser_isForbidden() {
        when(uiViewRepository.findByIdAndInstitutionId(10L, ORG)).thenReturn(Optional.of(view(10L, 99L, UiViewKind.USER)));
        ViewWriteRequest body = write("x");

        assertThatThrownBy(() -> service.update(me, 10L, body))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        verify(uiViewRepository, never()).save(any());
    }

    @Test
    void update_ofAViewOfAnotherOrganisation_isNotFound() {
        when(uiViewRepository.findByIdAndInstitutionId(10L, ORG)).thenReturn(Optional.empty());
        ViewWriteRequest body = write("x");

        assertThatThrownBy(() -> service.update(me, 10L, body))
                .hasMessageContaining("404");
    }

    @Test
    void delete_byOwner_dropsEveryonesStateAndBookmarksOnTheView() {
        UiTableView mine = view(10L, ME, UiViewKind.USER);
        when(uiViewRepository.findByIdAndInstitutionId(10L, ORG)).thenReturn(Optional.of(mine));

        service.delete(me, 10L);

        verify(userViewStateRepository).deleteForAllUsers(ORG, "view:10");
        verify(bookmarkRepository).deleteAllOfView(ORG, 10L);
        verify(uiViewRepository).delete(mine);
    }

    @Test
    void aDefaultViewIsNeverEditableAsAUserView() {
        UiTableView def = view(11L, ME, UiViewKind.DEFAULT);
        when(uiViewRepository.findByIdAndInstitutionId(11L, ORG)).thenReturn(Optional.of(def));

        assertThat(service.get(me, 11L).canEdit()).isFalse();
        assertThatThrownBy(() -> service.delete(me, 11L)).hasMessageContaining("403");
    }

    @Test
    void saveDefault_withoutPermission_isForbidden() {
        when(profilePermissionService.hasDefaultViewEditPermission(me, 3L)).thenReturn(false);
        DefaultViewRequest body = new DefaultViewRequest(ORG, "recordingUnit", 3L, null, new TableViewState());

        assertThatThrownBy(() -> service.saveDefault(me, body))
                .hasMessageContaining("403");
        verify(uiViewRepository, never()).save(any());
    }

    @Test
    void saveDefault_replacesTheExistingDefaultOfThePair() {
        UiTableView existing = view(12L, ME, UiViewKind.DEFAULT);
        TableViewState fresh = new TableViewState();
        fresh.setSort("creationTime:desc");
        when(profilePermissionService.hasDefaultViewEditPermission(me, null)).thenReturn(true);
        when(uiViewRepository.findDefault(ORG, UiViewKind.DEFAULT, "project", 0L, 0L)).thenReturn(Optional.of(existing));
        when(uiViewRepository.save(existing)).thenReturn(existing);

        ViewResource saved = service.saveDefault(me, new DefaultViewRequest(ORG, "project", null, null, fresh));

        assertThat(saved.id()).isEqualTo(12L);
        assertThat(saved.state().getSort()).isEqualTo("creationTime:desc");
    }

    @Test
    void putState_updatesTheRowOfTheUserAndKey() {
        UserViewState row = new UserViewState();
        when(userViewStateRepository.findByPersonIdAndInstitutionIdAndViewKey(ME, ORG, "view:10"))
                .thenReturn(Optional.of(row));

        service.putState(me, new ViewStateRequest(ORG, "view:10", Map.of("limit", 50)));

        assertThat(row.getState()).containsEntry("limit", 50);
        verify(userViewStateRepository).save(row);
    }

    @Test
    void putState_rejectsMissingState() {
        ViewStateRequest withoutState = new ViewStateRequest(ORG, "view:10", null);

        assertThatThrownBy(() -> service.putState(me, withoutState))
                .isInstanceOf(ResponseStatusException.class);
        verify(userViewStateRepository, never()).save(any());
    }

    @Test
    void deleteState_targetsOnlyTheCurrentUser() {
        service.deleteState(me, "ctx:recordingUnit:global");

        verify(userViewStateRepository).deleteOne(ME, ORG, "ctx:recordingUnit:global");
        verify(userViewStateRepository, never()).deleteForAllUsers(anyLong(), any());
    }

    @Test
    void list_searchesTheOrganisationsUserViewsByTitleAndClampsThePage() {
        UiTableView mine = view(10L, ME, UiViewKind.USER);
        when(uiViewRepository.search(eq(ORG), eq(UiViewKind.USER), eq("recordingUnit"), eq("%mes ue%"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(mine), PageRequest.of(1, 50), 51));

        ViewListResponse response = service.list(me, "recordingUnit", "  Mes UE ", 1000, 50);

        assertThat(response.data()).extracting(ViewResource::id).containsExactly(10L);
        assertThat(response.data().get(0).canEdit()).isTrue();
        assertThat(response.totalCount()).isEqualTo(51);
        // The size is capped, the offset becomes the page that holds it.
        assertThat(response.limit()).isEqualTo(200);
        assertThat(response.offset()).isZero();
    }

    @Test
    void list_withoutTypeOrQueryMatchesEverything() {
        when(uiViewRepository.search(eq(ORG), eq(UiViewKind.USER), eq(""), eq("%"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        ViewListResponse response = service.list(me, null, null, 0, -5);

        assertThat(response.data()).isEmpty();
        assertThat(response.limit()).isEqualTo(1);
        assertThat(response.offset()).isZero();
    }

    @Test
    void get_returnsAViewOfTheOrganisationWhoeverOwnsIt() {
        when(uiViewRepository.findByIdAndInstitutionId(10L, ORG)).thenReturn(Optional.of(view(10L, 99L, UiViewKind.USER)));

        ViewResource resource = service.get(me, 10L);

        assertThat(resource.ownerName()).isEqualTo("Ada Lovelace");
        assertThat(resource.canEdit()).isFalse();
    }

    @Test
    void get_ofAViewOfAnotherOrganisation_isNotFound() {
        when(uiViewRepository.findByIdAndInstitutionId(10L, ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(me, 10L)).hasMessageContaining("404");
    }

    @Test
    void update_byTheOwner_replacesTheViewsContent() {
        UiTableView mine = view(10L, ME, UiViewKind.USER);
        when(uiViewRepository.findByIdAndInstitutionId(10L, ORG)).thenReturn(Optional.of(mine));
        when(uiViewRepository.save(mine)).thenReturn(mine);

        ViewResource updated = service.update(me, 10L, new ViewWriteRequest(ORG, "project", " Renommée ", "project", new TableViewState()));

        assertThat(updated.title()).isEqualTo("Renommée");
        assertThat(mine.getResourceType()).isEqualTo("project");
        assertThat(mine.getScopeEntityType()).isEqualTo("project");
        assertThat(mine.getState().getVersion()).isEqualTo(TableViewState.CURRENT_VERSION);
    }

    @Test
    void create_rejectsATitleTooLongAndAMissingResourceTypeOrState() {
        ViewWriteRequest tooLong = write("x".repeat(201));
        ViewWriteRequest noType = new ViewWriteRequest(ORG, " ", "t", null, new TableViewState());
        ViewWriteRequest noState = new ViewWriteRequest(ORG, "recordingUnit", "t", null, null);

        assertThatThrownBy(() -> service.create(me, tooLong)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.create(me, noType)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.create(me, noState)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(uiViewRepository);
    }

    @Test
    void delete_ofAViewOfAnotherOrganisation_isNotFound() {
        when(uiViewRepository.findByIdAndInstitutionId(10L, ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(me, 10L)).hasMessageContaining("404");
        verify(uiViewRepository, never()).delete(any());
    }

    @Test
    void getDefault_returnsTheViewOfThePairAndTheRightToEditIt() {
        when(profilePermissionService.hasDefaultViewEditPermission(me, 3L)).thenReturn(true);
        when(uiViewRepository.findDefault(ORG, UiViewKind.DEFAULT, "recordingUnit", 3L, 7L))
                .thenReturn(Optional.of(view(12L, ME, UiViewKind.DEFAULT)));

        DefaultViewResponse response = service.getDefault(me, "recordingUnit", 3L, 7L);

        assertThat(response.view().id()).isEqualTo(12L);
        assertThat(response.canEdit()).isTrue();
    }

    @Test
    void getDefault_withoutAnyHasNoViewAndNeedsAType() {
        when(uiViewRepository.findDefault(ORG, UiViewKind.DEFAULT, "project", 0L, 0L)).thenReturn(Optional.empty());

        DefaultViewResponse response = service.getDefault(me, "project", null, null);

        assertThat(response.view()).isNull();
        assertThat(response.canEdit()).isFalse();
        assertThatThrownBy(() -> service.getDefault(me, " ", null, null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void saveDefault_createsTheDefaultOfAPairThatHasNone() {
        when(profilePermissionService.hasDefaultViewEditPermission(me, 3L)).thenReturn(true);
        when(uiViewRepository.findDefault(ORG, UiViewKind.DEFAULT, "recordingUnit", 3L, 7L)).thenReturn(Optional.empty());
        when(entityManager.getReference(Institution.class, ORG)).thenReturn(new Institution());
        when(entityManager.getReference(ActionUnit.class, 3L)).thenReturn(new ActionUnit());
        when(uiViewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ViewResource saved = service.saveDefault(me, new DefaultViewRequest(ORG, "recordingUnit", 3L, 7L, new TableViewState()));

        assertThat(saved.kind()).isEqualTo("DEFAULT");
        assertThat(saved.typeId()).isEqualTo(7L);
        assertThat(saved.ownerId()).isNull();
    }

    @Test
    void saveDefault_needsATypeAndAState() {
        DefaultViewRequest noType = new DefaultViewRequest(ORG, "", null, null, new TableViewState());
        DefaultViewRequest noState = new DefaultViewRequest(ORG, "project", null, null, null);

        assertThatThrownBy(() -> service.saveDefault(me, noType)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.saveDefault(me, noState)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getState_returnsWhatTheUserKeptOrNothing() {
        UserViewState row = new UserViewState();
        row.setState(Map.of("limit", 50));
        when(userViewStateRepository.findByPersonIdAndInstitutionIdAndViewKey(ME, ORG, "view:10")).thenReturn(Optional.of(row));
        when(userViewStateRepository.findByPersonIdAndInstitutionIdAndViewKey(ME, ORG, "view:11")).thenReturn(Optional.empty());

        assertThat(service.getState(me, "view:10").state()).containsEntry("limit", 50);
        assertThat(service.getState(me, "view:11").state()).isNull();
    }

    @Test
    void putState_createsTheRowOfAUserWhoKeptNothingYet() {
        when(userViewStateRepository.findByPersonIdAndInstitutionIdAndViewKey(ME, ORG, "detail:project:5")).thenReturn(Optional.empty());
        when(entityManager.getReference(Person.class, ME)).thenReturn(new Person());
        when(entityManager.getReference(Institution.class, ORG)).thenReturn(new Institution());

        ViewStateResponse saved = service.putState(me, new ViewStateRequest(ORG, "detail:project:5", Map.of("v", 1)));

        assertThat(saved.viewKey()).isEqualTo("detail:project:5");
        verify(userViewStateRepository).save(argThat(row -> "detail:project:5".equals(row.getViewKey()) && row.getState().containsKey("v")));
    }

    @Test
    void everyStateOperationNeedsAViewKeyOfAtMost300Characters() {
        String tooLong = "k".repeat(301);

        assertThatThrownBy(() -> service.getState(me, " ")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.getState(me, null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.deleteState(me, tooLong)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(userViewStateRepository);
    }
}
