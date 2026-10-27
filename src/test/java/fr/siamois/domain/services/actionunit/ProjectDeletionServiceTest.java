package fr.siamois.domain.services.actionunit;

import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.infrastructure.database.repositories.*;
import fr.siamois.infrastructure.database.repositories.form.config.*;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.specimen.SpecimenRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.DocumentationThesaurusRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ProjectCodeSourceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectDeletionServiceTest {

    @Mock private EntityManager entityManager;
    @Mock private Query query;
    @Mock private RecordingUnitRepository recordingUnitRepository;
    @Mock private SpecimenRepository specimenRepository;
    @Mock private PhaseRepository phaseRepository;
    @Mock private ContainerRepository containerRepository;
    @Mock private StudyRepository studyRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentService documentService;
    @Mock private CustomFieldAnswerService customFieldAnswerService;
    @Mock private FormConfigAnswerRepository formConfigAnswerRepository;
    @Mock private FormConfigRepository formConfigRepository;
    @Mock private FormConfigGroupRepository formConfigGroupRepository;
    @Mock private FieldFormConfigRepository fieldFormConfigRepository;
    @Mock private ConceptFieldStateRepository conceptFieldStateRepository;
    @Mock private DocumentationThesaurusRepository documentationThesaurusRepository;
    @Mock private ProjectCodeSourceRepository projectCodeSourceRepository;
    @Mock private ArkRepository arkRepository;

    @InjectMocks
    private ProjectDeletionService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(0L);
        when(query.getResultList()).thenReturn(List.of(5L));
        when(query.executeUpdate()).thenReturn(0);
        when(entityManager.getReference(any(Class.class), any()))
                .thenAnswer(inv -> org.springframework.beans.BeanUtils.instantiateClass((Class<?>) inv.getArgument(0)));
    }

    @Test
    void crossProjectLinks_none_returnsEmpty() {
        assertTrue(service.crossProjectLinks(1L).isEmpty());
    }

    @Test
    void crossProjectLinks_reportsEveryKindOfStraddlingLink() {
        when(query.getSingleResult()).thenReturn(2L);

        List<String> links = service.crossProjectLinks(1L);

        assertTrue(links.contains("la hiérarchie des unités d'enregistrement"));
        assertTrue(links.contains("des relations stratigraphiques"));
        assertTrue(links.contains("des mobiliers rattachés à un autre projet"));
        assertTrue(links.contains("des mouvements de mobiliers"));
        assertTrue(links.contains("des groupes de mobiliers"));
    }

    @Test
    void deleteContent_linkedToAnotherProject_throwsAndDeletesNothing() {
        when(query.getSingleResult()).thenReturn(1L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.deleteContent(1L));

        assertTrue(ex.getMessage().contains("Retirez d'abord"));
        verify(recordingUnitRepository, never()).deleteAllById(anyList());
        verify(specimenRepository, never()).deleteAllById(anyList());
    }

    @Test
    void deleteContent_clean_deletesEverythingInsideAndReturnsTheArks() {
        Document document = new Document();
        document.setId(5L);
        when(documentRepository.findById(5L)).thenReturn(Optional.of(document));
        FormConfig formConfig = new FormConfig();
        formConfig.setId(8L);
        when(formConfigRepository.findAllByActionUnitId(1L)).thenReturn(List.of(formConfig));

        List<Long> arks = service.deleteContent(1L);

        assertEquals(List.of(5L, 5L), arks);
        verify(customFieldAnswerService).deleteAnswerSets(anyList());
        verify(specimenRepository).deleteAllById(List.of(5L));
        verify(studyRepository).deleteAllById(List.of(5L));
        verify(containerRepository).deleteAllById(List.of(5L));
        verify(phaseRepository).deleteAllById(List.of(5L));
        verify(recordingUnitRepository).deleteAllById(List.of(5L));
        verify(documentService).deleteDocument(document);
        verify(formConfigRepository).delete(formConfig);
        verify(documentationThesaurusRepository).deleteAll(anyList());
        verify(projectCodeSourceRepository).deleteAll(anyList());
    }

    @Test
    void deleteContent_documentAlreadyGone_isSkipped() {
        when(documentRepository.findById(5L)).thenReturn(Optional.empty());

        service.deleteContent(1L);

        verify(documentService, never()).deleteDocument(any());
    }

    @Test
    void deleteArks_deletesThem_andIgnoresNothingToDelete() {
        service.deleteArks(List.of(3L, 4L));
        verify(arkRepository).deleteAllById(List.of(3L, 4L));

        service.deleteArks(List.of());
        service.deleteArks(null);
        verify(arkRepository).deleteAllById(anyList());
    }
}
