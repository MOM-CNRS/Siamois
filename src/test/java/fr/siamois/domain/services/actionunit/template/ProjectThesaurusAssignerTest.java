package fr.siamois.domain.services.actionunit.template;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.settings.DocumentationThesaurus;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.actionunit.DefaultProjectDefinition;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.DocumentationThesaurusRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectThesaurusAssignerTest {

    private final DocumentationThesaurusRepository documentation = mock(DocumentationThesaurusRepository.class);
    private final ActionUnitRepository projects = mock(ActionUnitRepository.class);
    private final VocabularyRepository vocabularies = mock(VocabularyRepository.class);
    private final DefaultProjectDefinition definition = new DefaultProjectDefinition();
    private final ProjectThesaurusAssigner assigner = new ProjectThesaurusAssigner(documentation, projects, vocabularies, definition);

    private final Vocabulary commonCore = new Vocabulary();
    private final Vocabulary institutionVocabulary = new Vocabulary();
    private ActionUnit project;

    @BeforeEach
    void setUp() {
        Institution institution = new Institution();
        institution.setId(1L);
        project = new ActionUnit();
        project.setId(5L);
        project.setCreatedByInstitution(institution);
        when(projects.findById(5L)).thenReturn(Optional.of(project));
        when(vocabularies.findVocabularyByBaseUriAndVocabExternalId(anyString(), anyString())).thenReturn(Optional.of(commonCore));
        when(documentation.findByActionUnitId(5L)).thenReturn(Optional.empty());
    }

    private DocumentationThesaurus saved() {
        ArgumentCaptor<DocumentationThesaurus> captor = ArgumentCaptor.forClass(DocumentationThesaurus.class);
        verify(documentation).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void defaultAndEmptyTemplatesRestOnTheCommonCoreThesaurus() {
        assigner.assignCommonCore(5L);

        DocumentationThesaurus thesaurus = saved();
        assertThat(thesaurus.getVocabulary()).isSameAs(commonCore);
        assertThat(thesaurus.getActionUnit()).isSameAs(project);
    }

    @Test
    void aCopyWithoutThesaurusOfItsOwnTakesTheInstitutionOne() {
        DocumentationThesaurus institutionThesaurus = new DocumentationThesaurus();
        institutionThesaurus.setVocabulary(institutionVocabulary);
        when(documentation.findOfInstitution(1L)).thenReturn(Optional.of(institutionThesaurus));

        assigner.assignInstitutionOrCommonCore(project);

        assertThat(saved().getVocabulary()).isSameAs(institutionVocabulary);
    }

    @Test
    void aCopyWhoseInstitutionHasNoThesaurusTakesTheCommonCore() {
        when(documentation.findOfInstitution(1L)).thenReturn(Optional.empty());

        assigner.assignInstitutionOrCommonCore(project);

        assertThat(saved().getVocabulary()).isSameAs(commonCore);
    }

    @Test
    void aProjectThatAlreadyHasAThesaurusKeepsIt() {
        when(documentation.findByActionUnitId(5L)).thenReturn(Optional.of(new DocumentationThesaurus()));

        assigner.assignInstitutionOrCommonCore(project);

        verify(documentation, never()).save(any());
    }

    @Test
    void whenTheCommonCoreIsMissingNothingIsAssigned() {
        when(vocabularies.findVocabularyByBaseUriAndVocabExternalId(anyString(), anyString())).thenReturn(Optional.empty());

        assigner.assignCommonCore(5L);

        verify(documentation, never()).save(any());
    }

    @Test
    void assigningTwiceKeepsTheFirstThesaurus() {
        when(documentation.findByActionUnitId(5L)).thenReturn(Optional.of(new DocumentationThesaurus()));

        assigner.assignCommonCore(5L);

        verify(documentation, never()).save(any());
    }
}
