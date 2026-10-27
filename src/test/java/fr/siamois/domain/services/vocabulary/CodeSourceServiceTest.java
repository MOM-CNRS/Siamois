package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.settings.ProjectCodeSource;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.dto.entity.vocabulary.VocabularyDTO;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.concept.ConceptBranchDTO;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptHierarchyRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ProjectCodeSourceRepository;
import fr.siamois.mapper.vocabulary.VocabularyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodeSourceServiceTest {

    private static final long PROJECT = 42L;
    private static final String CODE = "SIAAU.TYPE";

    @Mock
    private ProjectCodeSourceRepository projectCodeSourceRepository;
    @Mock
    private ActionUnitRepository actionUnitRepository;
    @Mock
    private ConceptService conceptService;
    @Mock
    private VocabularyService vocabularyService;
    @Mock
    private VocabularyMapper vocabularyMapper;
    @Mock
    private ConceptApi conceptApi;
    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private ConceptRepository conceptRepository;
    @Mock
    private ConceptHierarchyRepository conceptHierarchyRepository;

    @InjectMocks
    private CodeSourceService service;

    private ActionUnit project;

    @BeforeEach
    void setUp() {
        project = new ActionUnit();
        project.setId(PROJECT);
        lenient().when(actionUnitRepository.findById(PROJECT)).thenReturn(Optional.of(project));
        lenient().when(projectCodeSourceRepository.findByActionUnitIdAndFieldCode(PROJECT, CODE)).thenReturn(Optional.empty());
    }

    private ProjectCodeSource saved() {
        ArgumentCaptor<ProjectCodeSource> captor = ArgumentCaptor.forClass(ProjectCodeSource.class);
        verify(projectCodeSourceRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void useFieldCode_createsTheRedirectionOfTheProject() {
        Vocabulary vocabulary = new Vocabulary();

        service.useFieldCode(PROJECT, CODE, vocabulary, "OWN.TYPE");

        ProjectCodeSource source = saved();
        assertThat(source.getActionUnit()).isSameAs(project);
        assertThat(source.getFieldCode()).isEqualTo(CODE);
        assertThat(source.getVocabulary()).isSameAs(vocabulary);
        assertThat(source.getSourceFieldCode()).isEqualTo("OWN.TYPE");
        assertThat(source.getBranchTopTerm()).isNull();
    }

    @Test
    void useFieldCode_replacesABranchRedirection() {
        ProjectCodeSource existing = new ProjectCodeSource();
        existing.setBranchTopTerm(new Concept());
        when(projectCodeSourceRepository.findByActionUnitIdAndFieldCode(PROJECT, CODE)).thenReturn(Optional.of(existing));

        service.useFieldCode(PROJECT, CODE, new Vocabulary(), "OWN.TYPE");

        assertThat(saved()).isSameAs(existing);
        assertThat(existing.getBranchTopTerm()).isNull();
        assertThat(existing.isBranch()).isFalse();
    }

    @Test
    void useBranch_savesTheBranchAndDropsTheCode() throws Exception {
        Vocabulary vocabulary = new Vocabulary();
        VocabularyDTO vocabularyDTO = new VocabularyDTO();
        vocabularyDTO.setBaseUri("https://thesaurus.example.org");
        vocabularyDTO.setExternalVocabularyId("th1");
        ConceptDTO branch = ConceptDTO.builder().externalId("123").vocabulary(vocabularyDTO).deleted(false).build();
        Concept concept = new Concept();
        concept.setExternalId("123");
        concept.setVocabulary(vocabulary);
        when(vocabularyService.findOrCreateVocabularyOfUri(any())).thenReturn(vocabulary);
        when(vocabularyMapper.convert(vocabulary)).thenReturn(vocabularyDTO);
        when(conceptService.saveOrGetConcept(branch)).thenReturn(concept);
        when(conceptApi.fetchDownExpansion(vocabulary, "123")).thenReturn(new ConceptBranchDTO());
        when(applicationContext.getBean(ConceptApi.class)).thenReturn(conceptApi);
        when(applicationContext.getBean(ConceptService.class)).thenReturn(conceptService);
        when(applicationContext.getBean(ConceptRepository.class)).thenReturn(conceptRepository);
        when(applicationContext.getBean(ConceptHierarchyRepository.class)).thenReturn(conceptHierarchyRepository);
        ProjectCodeSource existing = new ProjectCodeSource();
        existing.setVocabulary(vocabulary);
        existing.setSourceFieldCode("OWN.TYPE");
        when(projectCodeSourceRepository.findByActionUnitIdAndFieldCode(PROJECT, CODE)).thenReturn(Optional.of(existing));

        service.useBranch(PROJECT, CODE, branch, null);

        assertThat(saved().getBranchTopTerm()).isSameAs(concept);
        assertThat(existing.getSourceFieldCode()).isNull();
        assertThat(existing.getVocabulary()).isNull();
    }

    @Test
    void clear_deletesTheRedirection() {
        ProjectCodeSource existing = new ProjectCodeSource();
        when(projectCodeSourceRepository.findByActionUnitIdAndFieldCode(PROJECT, CODE)).thenReturn(Optional.of(existing));

        service.clear(PROJECT, CODE);

        verify(projectCodeSourceRepository).delete(existing);
    }

    @Test
    void clear_doesNothing_whenTheProjectRedirectsNothing() {
        service.clear(PROJECT, CODE);

        verify(projectCodeSourceRepository, never()).delete(any());
    }
}
