package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.relation.RelationEndKind;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.study.Study;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.ContainerRepository;
import fr.siamois.infrastructure.database.repositories.DocumentRepository;
import fr.siamois.infrastructure.database.repositories.PhaseRepository;
import fr.siamois.infrastructure.database.repositories.StudyRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.specimen.SpecimenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelationEndpointResolverTest {

    private static final long PROJECT = 7L;
    private static final long INSTITUTION = 3L;

    @Mock private RecordingUnitRepository recordingUnitRepository;
    @Mock private SpecimenRepository specimenRepository;
    @Mock private PhaseRepository phaseRepository;
    @Mock private ContainerRepository containerRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private StudyRepository studyRepository;
    @Mock private ActionUnitRepository actionUnitRepository;

    @InjectMocks private RelationEndpointResolver resolver;

    private final Concept type = concept();

    @Test
    void aRecordOfAProjectCarriesItsProjectTypeAndInstitution() {
        RecordingUnit unit = owned(new RecordingUnit(), 10L);
        unit.setActionUnit(project());
        unit.setType(type);
        when(recordingUnitRepository.findById(10L)).thenReturn(Optional.of(unit));

        assertThat(resolver.find(RelationEndKind.RECORDING_UNIT, 10L))
                .contains(new RelationEndpoint(RelationEndKind.RECORDING_UNIT, 10L, unit, PROJECT, type, INSTITUTION));
    }

    @Test
    void everyKindOfRecordIsLoadedFromItsOwnTable() {
        Phase phase = owned(new Phase(), 11L);
        phase.setActionUnit(project());
        Container container = owned(new Container(), 12L);
        container.setActionUnit(project());
        Document document = owned(new Document(), 13L);
        document.setActionUnit(project());
        Study study = owned(new Study(), 14L);
        study.setActionUnit(project());
        when(phaseRepository.findById(11L)).thenReturn(Optional.of(phase));
        when(containerRepository.findById(12L)).thenReturn(Optional.of(container));
        when(documentRepository.findById(13L)).thenReturn(Optional.of(document));
        when(studyRepository.findById(14L)).thenReturn(Optional.of(study));

        assertThat(resolver.find(RelationEndKind.PHASE, 11L)).map(RelationEndpoint::projectId).contains(PROJECT);
        assertThat(resolver.find(RelationEndKind.CONTAINER, 12L)).map(RelationEndpoint::projectId).contains(PROJECT);
        assertThat(resolver.find(RelationEndKind.DOCUMENT, 13L)).map(RelationEndpoint::projectId).contains(PROJECT);
        assertThat(resolver.find(RelationEndKind.STUDY, 14L)).map(RelationEndpoint::projectId).contains(PROJECT);
    }

    @Test
    void aProjectIsItsOwnProject() {
        ActionUnit project = project();
        when(actionUnitRepository.findById(PROJECT)).thenReturn(Optional.of(project));

        assertThat(resolver.find(RelationEndKind.ACTION_UNIT, PROJECT)).map(RelationEndpoint::projectId).contains(PROJECT);
    }

    @Test
    void aFindReachesItsProjectDirectlyOrThroughItsUnit() {
        Specimen direct = owned(new Specimen(), 20L);
        direct.setActionUnit(project());
        RecordingUnit unit = owned(new RecordingUnit(), 10L);
        unit.setActionUnit(project());
        Specimen throughUnit = owned(new Specimen(), 21L);
        throughUnit.setRecordingUnit(unit);
        Specimen loose = owned(new Specimen(), 22L);
        when(specimenRepository.findById(20L)).thenReturn(Optional.of(direct));
        when(specimenRepository.findById(21L)).thenReturn(Optional.of(throughUnit));
        when(specimenRepository.findById(22L)).thenReturn(Optional.of(loose));

        assertThat(resolver.find(RelationEndKind.SPECIMEN, 20L)).map(RelationEndpoint::projectId).contains(PROJECT);
        assertThat(resolver.find(RelationEndKind.SPECIMEN, 21L)).map(RelationEndpoint::projectId).contains(PROJECT);
        assertThat(resolver.find(RelationEndKind.SPECIMEN, 22L)).map(RelationEndpoint::projectId).isEmpty();
    }

    @Test
    void aMissingRecordAPlaceOrNoIdFindNothing() {
        when(phaseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(resolver.find(RelationEndKind.PHASE, 99L)).isEmpty();
        assertThat(resolver.find(RelationEndKind.SPATIAL_UNIT, 1L)).isEmpty();
        assertThat(resolver.find(null, 1L)).isEmpty();
        assertThat(resolver.find(RelationEndKind.PHASE, null)).isEmpty();
    }

    @Test
    void findAllLoadsTheRecordsOfOneKindAtOnce() {
        RecordingUnit unit = owned(new RecordingUnit(), 10L);
        Specimen find = owned(new Specimen(), 20L);
        Phase phase = owned(new Phase(), 11L);
        Container container = owned(new Container(), 12L);
        Document document = owned(new Document(), 13L);
        Study study = owned(new Study(), 14L);
        ActionUnit project = project();
        when(recordingUnitRepository.findAllById(List.of(10L))).thenReturn(List.of(unit));
        when(specimenRepository.findAllById(List.of(20L))).thenReturn(List.of(find));
        when(phaseRepository.findAllById(List.of(11L))).thenReturn(List.of(phase));
        when(containerRepository.findAllById(List.of(12L))).thenReturn(List.of(container));
        when(documentRepository.findAllById(List.of(13L))).thenReturn(List.of(document));
        when(studyRepository.findAllById(List.of(14L))).thenReturn(List.of(study));
        when(actionUnitRepository.findAllById(List.of(PROJECT))).thenReturn(List.of(project));

        assertThat(resolver.findAll(RelationEndKind.RECORDING_UNIT, List.of(10L))).containsOnlyKeys(10L);
        assertThat(resolver.findAll(RelationEndKind.SPECIMEN, List.of(20L))).containsOnlyKeys(20L);
        assertThat(resolver.findAll(RelationEndKind.PHASE, List.of(11L))).containsOnlyKeys(11L);
        assertThat(resolver.findAll(RelationEndKind.CONTAINER, List.of(12L))).containsOnlyKeys(12L);
        assertThat(resolver.findAll(RelationEndKind.DOCUMENT, List.of(13L))).containsOnlyKeys(13L);
        assertThat(resolver.findAll(RelationEndKind.STUDY, List.of(14L))).containsOnlyKeys(14L);
        Map<Long, RelationEndpoint> projects = resolver.findAll(RelationEndKind.ACTION_UNIT, List.of(PROJECT));
        assertThat(projects.get(PROJECT).projectId()).isEqualTo(PROJECT);
    }

    @Test
    void findAllOfNoIdOrOfPlacesReadsNothing() {
        assertThat(resolver.findAll(RelationEndKind.RECORDING_UNIT, List.of())).isEmpty();
        assertThat(resolver.findAll(RelationEndKind.SPATIAL_UNIT, List.of(1L))).isEmpty();
        verifyNoInteractions(recordingUnitRepository);
    }

    private static <T extends TraceableEntity> T owned(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        Institution institution = new Institution();
        institution.setId(INSTITUTION);
        entity.setCreatedByInstitution(institution);
        return entity;
    }

    private static ActionUnit project() {
        return owned(new ActionUnit(), PROJECT);
    }

    private static Concept concept() {
        Concept concept = new Concept();
        concept.setId(900L);
        return concept;
    }
}
