package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
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
import fr.siamois.ui.api.openapi.v1.resource.view.ViewResource;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

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
        assertThatThrownBy(() -> service.create(me, write(" ")))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(uiViewRepository);
    }

    @Test
    void update_byAnotherUser_isForbidden() {
        when(uiViewRepository.findByIdAndInstitutionId(10L, ORG)).thenReturn(Optional.of(view(10L, 99L, UiViewKind.USER)));

        assertThatThrownBy(() -> service.update(me, 10L, write("x")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        verify(uiViewRepository, never()).save(any());
    }

    @Test
    void update_ofAViewOfAnotherOrganisation_isNotFound() {
        when(uiViewRepository.findByIdAndInstitutionId(10L, ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(me, 10L, write("x")))
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

        assertThatThrownBy(() -> service.saveDefault(me,
                new DefaultViewRequest(ORG, "recordingUnit", 3L, null, new TableViewState())))
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
        assertThatThrownBy(() -> service.putState(me, new ViewStateRequest(ORG, "view:10", null)))
                .isInstanceOf(ResponseStatusException.class);
        verify(userViewStateRepository, never()).save(any());
    }

    @Test
    void deleteState_targetsOnlyTheCurrentUser() {
        service.deleteState(me, "ctx:recordingUnit:global");

        verify(userViewStateRepository).deleteOne(eq(ME), eq(ORG), eq("ctx:recordingUnit:global"));
        verify(userViewStateRepository, never()).deleteForAllUsers(anyLong(), any());
    }
}
