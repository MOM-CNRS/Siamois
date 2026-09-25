package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.services.BookmarkService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.resource.container.ContainerResource;
import fr.siamois.ui.api.openapi.v1.resource.phase.PhaseResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceBookmarkServiceTest {

    @Mock
    private BookmarkService bookmarkService;

    private ResourceBookmarkService service;
    private PersonDTO person;
    private InstitutionDTO institution;

    @BeforeEach
    void setUp() {
        service = new ResourceBookmarkService(bookmarkService);
        person = new PersonDTO();
        person.setId(1L);
        institution = new InstitutionDTO();
        institution.setId(10L);
    }

    private static PhaseResource phase(String uri) {
        PhaseResource r = new PhaseResource();
        r.setResourceUri(uri);
        return r;
    }

    @Test
    @SuppressWarnings("unchecked")
    void markBookmarked_list_flagsOnlyBookmarkedUris_inOneQuery() {
        PhaseResource a = phase("/phase/1");
        PhaseResource b = phase("/phase/2");
        PhaseResource noUri = phase(null);
        when(bookmarkService.findBookmarkedResourceUris(any(UserInfo.class), any())).thenReturn(Set.of("/phase/2"));

        service.markBookmarked(person, institution, List.of(a, b, noUri), "fr");

        assertThat(a.isBookmarked()).isFalse();
        assertThat(b.isBookmarked()).isTrue();
        assertThat(noUri.isBookmarked()).isFalse();
        ArgumentCaptor<UserInfo> userInfo = ArgumentCaptor.forClass(UserInfo.class);
        ArgumentCaptor<Collection<String>> uris = ArgumentCaptor.forClass(Collection.class);
        verify(bookmarkService).findBookmarkedResourceUris(userInfo.capture(), uris.capture());
        assertThat(userInfo.getValue().getInstitution()).isSameAs(institution);
        assertThat(userInfo.getValue().getUser()).isSameAs(person);
        assertThat(uris.getValue()).containsExactlyInAnyOrder("/phase/1", "/phase/2");
    }

    @Test
    void markBookmarked_detail_viaUserInfo() {
        ContainerResource r = new ContainerResource();
        r.setResourceUri("/container/5");
        when(bookmarkService.findBookmarkedResourceUris(any(UserInfo.class), any())).thenReturn(Set.of("/container/5"));

        service.markBookmarked(new UserInfo(institution, person, "fr"), r);

        assertThat(r.isBookmarked()).isTrue();
    }

    @Test
    void markBookmarked_withoutInstitutionOrEmptyList_doesNotQuery() {
        service.markBookmarked(person, null, List.of(phase("/phase/1")), "fr");
        service.markBookmarked(person, institution, List.of(), "fr");

        verify(bookmarkService, never()).findBookmarkedResourceUris(any(), any());
    }
}
