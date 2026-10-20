package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.uiview.FicheConfig;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.FicheConfigRepository;
import fr.siamois.ui.api.openapi.v1.request.view.FicheConfigRequest;
import fr.siamois.ui.api.openapi.v1.resource.view.FicheConfigResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FicheConfigOpenApiServiceTest {

    private static final long ORG = 5L;
    private static final long ME = 7L;
    private static final long PROJECT = 11L;
    private static final long TYPE = 21L;

    @Mock FicheConfigRepository repository;
    @Mock ProfilePermissionService profilePermissionService;
    @Mock EntityManager entityManager;

    @InjectMocks FicheConfigOpenApiService service;

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

    private static Map<String, Object> tabs(String... keys) {
        List<Object> list = new ArrayList<>();
        for (String key : keys) list.add(Map.of("key", key));
        return Map.of("v", 1, "tabs", list);
    }

    private static FicheConfig config(Long typeId, Map<String, Object> tabs) {
        FicheConfig c = new FicheConfig();
        c.setTypeId(typeId);
        c.setTabs(tabs);
        return c;
    }

    @Test
    void getReturnsTheConfigurationOfTheSubTypeAndTheRightToEdit() {
        when(profilePermissionService.hasDefaultViewEditPermission(me, PROJECT)).thenReturn(true);
        when(repository.find(ORG, "recordingUnit", PROJECT, TYPE)).thenReturn(Optional.of(config(TYPE, tabs("fiche"))));

        FicheConfigResponse response = service.get(me, "recordingUnit", PROJECT, TYPE);

        assertThat(response.tabs()).isEqualTo(tabs("fiche"));
        assertThat(response.typeId()).isEqualTo(TYPE);
        assertThat(response.canEdit()).isTrue();
    }

    @Test
    void getFallsBackOnTheConfigurationOfEverySubType() {
        when(repository.find(ORG, "recordingUnit", PROJECT, TYPE)).thenReturn(Optional.empty());
        when(repository.find(ORG, "recordingUnit", PROJECT, 0L)).thenReturn(Optional.of(config(null, tabs("fiche", "documents"))));

        FicheConfigResponse response = service.get(me, "recordingUnit", PROJECT, TYPE);

        assertThat(response.tabs()).isEqualTo(tabs("fiche", "documents"));
        assertThat(response.typeId()).isNull();
        assertThat(response.canEdit()).isFalse();
    }

    @Test
    void getWithoutAnyConfigurationHasNoTabs() {
        when(repository.find(ORG, "project", 0L, 0L)).thenReturn(Optional.empty());

        FicheConfigResponse response = service.get(me, "project", null, null);

        assertThat(response.tabs()).isNull();
    }

    @Test
    void saveNeedsTheDefaultViewsRight() {
        when(profilePermissionService.hasDefaultViewEditPermission(me, PROJECT)).thenReturn(false);
        FicheConfigRequest request = new FicheConfigRequest(ORG, "recordingUnit", PROJECT, null, tabs("fiche"));

        assertThatThrownBy(() -> service.save(me, request))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        verify(repository, never()).save(any());
    }

    @Test
    void saveCreatesTheConfigurationThenReplacesIt() {
        when(profilePermissionService.hasDefaultViewEditPermission(me, PROJECT)).thenReturn(true);
        when(repository.find(ORG, "recordingUnit", PROJECT, TYPE)).thenReturn(Optional.empty());
        when(repository.save(any(FicheConfig.class))).thenAnswer(i -> i.getArgument(0));

        FicheConfigResponse created = service.save(me, new FicheConfigRequest(ORG, "recordingUnit", PROJECT, TYPE, tabs("fiche")));

        assertThat(created.tabs()).isEqualTo(tabs("fiche"));
        assertThat(created.canEdit()).isTrue();

        FicheConfig existing = config(TYPE, tabs("fiche"));
        when(repository.find(ORG, "recordingUnit", PROJECT, TYPE)).thenReturn(Optional.of(existing));
        service.save(me, new FicheConfigRequest(ORG, "recordingUnit", PROJECT, TYPE, tabs("documents", "fiche")));

        assertThat(existing.getTabs()).isEqualTo(tabs("documents", "fiche"));
    }

    private void assertSaveRefused(Map<String, Object> tabs) {
        FicheConfigRequest request = new FicheConfigRequest(ORG, "project", null, null, tabs);
        assertThatThrownBy(() -> service.save(me, request))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        verify(repository, never()).save(any());
    }

    @Test
    void saveRefusesTabsWithoutAList() {
        when(profilePermissionService.hasDefaultViewEditPermission(me, null)).thenReturn(true);

        assertSaveRefused(Map.of("v", 1));
        assertSaveRefused(null);
    }

    @Test
    void saveRefusesTabsWithoutKeyOrWithADuplicateKey() {
        when(profilePermissionService.hasDefaultViewEditPermission(me, null)).thenReturn(true);

        assertSaveRefused(tabs("a", "a"));
        assertSaveRefused(Map.of("tabs", List.of(Map.of("label", "no key"))));
        assertSaveRefused(Map.of("tabs", List.of("not a tab")));
        assertSaveRefused(Map.of("tabs", List.of(Map.of("key", " "))));
    }

    @Test
    void saveRefusesTooManyTabs() {
        when(profilePermissionService.hasDefaultViewEditPermission(me, null)).thenReturn(true);
        String[] many = new String[FicheConfigOpenApiService.MAX_TABS + 1];
        for (int i = 0; i < many.length; i++) many[i] = "k" + i;

        assertSaveRefused(tabs(many));
    }

    @Test
    void saveRefusesTooLongKeysNamesAndConfigurations() {
        when(profilePermissionService.hasDefaultViewEditPermission(me, null)).thenReturn(true);
        String tooLong = "x".repeat(FicheConfigOpenApiService.MAX_TEXT_LENGTH + 1);

        assertSaveRefused(Map.of("tabs", List.of(Map.of("key", tooLong))));
        assertSaveRefused(Map.of("tabs", List.of(Map.of("key", "a", "label", tooLong))));
        assertSaveRefused(Map.of("tabs", List.of(Map.of("key", "a", "baseFilters", "y".repeat(FicheConfigOpenApiService.MAX_BYTES)))));
    }

    @Test
    void everyOperationNeedsAResourceType() {
        FicheConfigRequest request = new FicheConfigRequest(ORG, " ", null, null, tabs("fiche"));

        assertThatThrownBy(() -> service.get(me, null, null, null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.save(me, request)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.delete(me, "", null, null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void deleteNeedsTheRightAndRemovesTheExactConfiguration() {
        when(profilePermissionService.hasDefaultViewEditPermission(me, PROJECT)).thenReturn(true);
        FicheConfig existing = config(TYPE, tabs("fiche"));
        when(repository.find(ORG, "recordingUnit", PROJECT, TYPE)).thenReturn(Optional.of(existing));

        service.delete(me, "recordingUnit", PROJECT, TYPE);

        verify(repository).delete(existing);

        when(profilePermissionService.hasDefaultViewEditPermission(me, PROJECT)).thenReturn(false);
        assertThatThrownBy(() -> service.delete(me, "recordingUnit", PROJECT, TYPE))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
    }
}
