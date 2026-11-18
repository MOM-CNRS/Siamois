package fr.siamois.domain.services;

import fr.siamois.infrastructure.database.repositories.PageTargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageTargetServiceTest {

    @Mock
    private PageTargetRepository repository;
    @InjectMocks
    private PageTargetService service;

    @BeforeEach
    void setUp() {
        lenient().when(repository.isKnownKind(anyString())).thenAnswer(call -> !"nope".equals(call.getArgument(0)));
    }

    @Test
    void missingAmong_namesTheEntityPagesWhoseEntityIsGone() {
        when(repository.existingIds("recording-unit", Set.of(1L, 2L))).thenReturn(Set.of(1L));

        assertThat(service.missingAmong(List.of("/recording-unit/1", "/recording-unit/2")))
                .containsExactly("/recording-unit/2");
    }

    @Test
    void missingAmong_namesTheListsOpenedOnADeletedSavedView() {
        when(repository.existingIds(PageTargetRepository.SAVED_VIEW, Set.of(3L, 4L))).thenReturn(Set.of(4L));

        assertThat(service.missingAmong(List.of("/recording-unit?viewId=3", "/specimen?sort=a&viewId=4")))
                .containsExactly("/recording-unit?viewId=3");
    }

    @Test
    void missingAmong_readsAnEntityPageAsItsEntityWhateverItsQueryString() {
        when(repository.existingIds("recording-unit", Set.of(1L))).thenReturn(Set.of());

        assertThat(service.missingAmong(List.of("/recording-unit/1?viewId=7"))).containsExactly("/recording-unit/1?viewId=7");
    }

    @Test
    void missingAmong_neverNamesAPageAboutNothing() {
        assertThat(service.missingAmong(List.of("", "/welcome", "/recording-unit", "/recording-unit?viewId=abc", "/nope/1")))
                .isEmpty();
        verify(repository, never()).existingIds(anyString(), anyCollection());
    }

    @Test
    void isFiche_isTheEntityPagesOnly() {
        assertThat(service.isFiche("/recording-unit/12")).isTrue();
        assertThat(service.isFiche("/recording-unit")).isFalse();
        assertThat(service.isFiche("/recording-unit?viewId=3")).isFalse();
        assertThat(service.isFiche("/welcome")).isFalse();
        assertThat(service.isFiche("/nope/1")).isFalse();
    }

    @Test
    void isMissing_answersForOnePage() {
        when(repository.existingIds("phase", Set.of(9L))).thenReturn(Set.of());

        assertThat(service.isMissing("/phase/9")).isTrue();
        assertThat(service.isMissing("/welcome")).isFalse();
    }
}
