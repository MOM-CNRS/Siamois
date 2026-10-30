package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.VocabularyInstance;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyInstanceRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VocabularyInstanceServiceTest {

    private static final String BASE_URI = "https://opentheso.huma-num.fr/opentheso";

    @Mock
    private VocabularyInstanceRepository vocabularyInstanceRepository;
    @Mock
    private VocabularyRepository vocabularyRepository;

    @InjectMocks
    private VocabularyInstanceService service;

    /**
     * A vocabulary of a saved instance.
     */
    private static Vocabulary vocabularyOf(VocabularyInstance instance, String externalId) {
        Vocabulary vocabulary = new Vocabulary();
        vocabulary.setInstance(instance);
        vocabulary.setExternalVocabularyId(externalId);
        return vocabulary;
    }

    @Test
    void findOrCreate_returnsTheExistingInstance() {
        VocabularyInstance existing = new VocabularyInstance(BASE_URI);
        existing.setId(4L);
        when(vocabularyInstanceRepository.findFirstByBaseUriIgnoreCaseOrderByIdAsc(BASE_URI)).thenReturn(Optional.of(existing));

        assertThat(service.findOrCreate(BASE_URI)).isSameAs(existing);
        verify(vocabularyInstanceRepository, never()).save(any());
    }

    @Test
    void findOrCreate_savesAnUnknownInstance() {
        when(vocabularyInstanceRepository.findFirstByBaseUriIgnoreCaseOrderByIdAsc(BASE_URI)).thenReturn(Optional.empty());
        when(vocabularyInstanceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.findOrCreate(BASE_URI).getBaseUri()).isEqualTo(BASE_URI);
    }

    @Test
    void findDocumentationInstancesIn_returnsEachInstanceOnce() {
        VocabularyInstance first = new VocabularyInstance(BASE_URI);
        VocabularyInstance second = new VocabularyInstance("https://thesaurus.mom.fr");
        ProfilePermissionService.SettingsScope scope =
                new ProfilePermissionService.SettingsScope(false, Set.of(12L), false, Set.of(), Set.of(40L));
        when(vocabularyRepository.findDocumentationThesauriOf(false, Set.of(12L), false, Set.of(), Set.of(40L)))
                .thenReturn(List.of(vocabularyOf(first, "th1"), vocabularyOf(second, "th2"), vocabularyOf(first, "th3")));

        assertThat(service.findDocumentationInstancesIn(scope)).containsExactly(first, second);
    }
}
