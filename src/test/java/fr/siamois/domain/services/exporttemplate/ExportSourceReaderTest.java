package fr.siamois.domain.services.exporttemplate;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.exporttemplate.ExportSubject;
import fr.siamois.domain.models.exporttemplate.ExportTemplateDefinition.*;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.infrastructure.database.repositories.ContainerRepository;
import fr.siamois.infrastructure.database.repositories.DocumentRepository;
import fr.siamois.infrastructure.database.repositories.PhaseRepository;
import fr.siamois.infrastructure.database.repositories.StudyRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.StratigraphicRelationshipRepository;
import fr.siamois.infrastructure.database.repositories.specimen.SpecimenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExportSourceReaderTest {

    private static final Long PROJECT_ID = 5L;

    @Mock private ActionUnitRepository actionUnitRepository;
    @Mock private RecordingUnitRepository recordingUnitRepository;
    @Mock private SpecimenRepository specimenRepository;
    @Mock private PhaseRepository phaseRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private ContainerRepository containerRepository;
    @Mock private StudyRepository studyRepository;
    @Mock private StratigraphicRelationshipRepository stratigraphicRelationshipRepository;

    private ExportSourceReader reader;
    private ActionUnit project;

    @BeforeEach
    void setUp() {
        reader = new ExportSourceReader(actionUnitRepository, recordingUnitRepository, specimenRepository,
                phaseRepository, documentRepository, containerRepository, studyRepository, stratigraphicRelationshipRepository);
        project = new ActionUnit();
        project.setId(PROJECT_ID);
    }

    private static Concept concept(String thesaurus, String id) {
        Vocabulary v = new Vocabulary();
        v.setExternalVocabularyId(thesaurus);
        Concept c = new Concept();
        c.setExternalId(id);
        c.setVocabulary(v);
        return c;
    }

    private static RecordingUnit unit(Long id, Concept type) {
        RecordingUnit ru = new RecordingUnit();
        ru.setId(id);
        ru.setType(type);
        return ru;
    }

    private static SpatialUnit place(Long id) {
        SpatialUnit su = new SpatialUnit();
        su.setId(id);
        su.setChildren(new HashSet<>());
        return su;
    }

    @Test
    void read_projectSource_isASingleRow() {
        when(actionUnitRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        List<ExportRow> rows = reader.read(new ProjectSource(), PROJECT_ID);

        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.subject()).isEqualTo(ExportSubject.PROJECT);
            assertThat(r.entity()).isSameAs(project);
        });
    }

    @Test
    void count_groupsTheEntitiesOfTheProjectByType() {
        Concept pottery = concept("th1", "10");
        Concept lithic = concept("th1", "11");
        when(recordingUnitRepository.findAllByActionUnitId(PROJECT_ID))
                .thenReturn(List.of(unit(1L, pottery), unit(2L, pottery), unit(3L, lithic), unit(4L, null)));

        ExportSourceReader.SourceCounts counts = reader.count(ExportSubject.RECORDING_UNIT, PROJECT_ID);

        assertThat(counts.total()).isEqualTo(4);
        assertThat(counts.byType()).containsEntry("th1|10", 2).containsEntry("th1|11", 1).hasSize(2);
        assertThat(counts.rowsOf(List.of())).isEqualTo(4);
        assertThat(counts.rowsOf(List.of(new ConceptRef("TH1", "10", null), new ConceptRef("th1", "99", null)))).isEqualTo(2);
    }

    @Test
    void count_projectIsASingleRow() {
        when(actionUnitRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        assertThat(reader.count(ExportSubject.PROJECT, PROJECT_ID).total()).isEqualTo(1);
    }

    @Test
    void read_unknownProject_isNotFound() {
        when(actionUnitRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

        Source projectSource = new ProjectSource();
        assertThatThrownBy(() -> reader.read(projectSource, PROJECT_ID)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void read_studySource_filtersTheStudiesOfTheProjectByType() {
        Concept treatment = concept("th252", "4291174");
        fr.siamois.domain.models.study.Study kept = new fr.siamois.domain.models.study.Study();
        kept.setType(treatment);
        fr.siamois.domain.models.study.Study other = new fr.siamois.domain.models.study.Study();
        other.setType(concept("th252", "4291173"));
        when(studyRepository.findAllByActionUnitId(PROJECT_ID)).thenReturn(List.of(kept, other));

        List<ExportRow> rows = reader.read(
                new EntitySource(EntityKind.STUDY, List.of(new ConceptRef("th252", "4291174", null))),
                PROJECT_ID);

        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.subject()).isEqualTo(ExportSubject.STUDY);
            assertThat(r.entity()).isSameAs(kept);
        });
    }

    @Test
    void read_entitySource_withoutTypes_returnsEveryEntityOfTheProject() {
        when(recordingUnitRepository.findAllByActionUnitId(PROJECT_ID))
                .thenReturn(List.of(unit(1L, concept("th", "A")), unit(2L, null)));

        List<ExportRow> rows = reader.read(new EntitySource(EntityKind.RECORDING_UNIT, List.of()), PROJECT_ID);

        assertThat(rows).hasSize(2).allSatisfy(r -> assertThat(r.subject()).isEqualTo(ExportSubject.RECORDING_UNIT));
    }

    @Test
    void read_entitySource_withTypes_keepsOnlyThoseTypes() {
        RecordingUnit wanted = unit(1L, concept("TH338", "a"));
        when(recordingUnitRepository.findAllByActionUnitId(PROJECT_ID))
                .thenReturn(List.of(wanted, unit(2L, concept("th338", "B")), unit(3L, null)));

        List<ExportRow> rows = reader.read(
                new EntitySource(EntityKind.RECORDING_UNIT, List.of(new ConceptRef("th338", "A", null))), PROJECT_ID);

        assertThat(rows).singleElement().satisfies(r -> assertThat(r.entity()).isSameAs(wanted));
    }

    @Test
    void read_specimenSource_usesTheSpecimensOfTheProject() {
        Specimen specimen = new Specimen();
        when(specimenRepository.findAllByActionUnitId(PROJECT_ID)).thenReturn(List.of(specimen));

        assertThat(reader.read(new EntitySource(EntityKind.SPECIMEN, List.of()), PROJECT_ID))
                .singleElement().satisfies(r -> assertThat(r.entity()).isSameAs(specimen));
    }

    @Test
    void read_spatialUnits_includeMainLocationContextAndDescendantsOnce() {
        SpatialUnit main = place(1L);
        SpatialUnit context = place(2L);
        SpatialUnit child = place(3L);
        SpatialUnit grandChild = place(4L);
        main.getChildren().add(child);
        child.getChildren().add(grandChild);
        grandChild.getChildren().add(main); // cycle : ne doit pas boucler
        project.setMainLocation(main);
        project.setSpatialContext(new HashSet<>(Set.of(context, child)));
        when(actionUnitRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        List<ExportRow> rows = reader.read(new EntitySource(EntityKind.SPATIAL_UNIT, List.of()), PROJECT_ID);

        assertThat(rows).extracting(r -> ((SpatialUnit) r.entity()).getId()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
    }

    @Test
    void read_spatialUnits_withoutLocation_isEmpty() {
        project.setSpatialContext(new HashSet<>());
        when(actionUnitRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        assertThat(reader.read(new EntitySource(EntityKind.SPATIAL_UNIT, List.of()), PROJECT_ID)).isEmpty();
    }

    @Test
    void read_unknownTechnicalSource_isRejected() {
        Source injected = new TechnicalSource("users; DROP TABLE x");
        assertThatThrownBy(() -> reader.read(injected, PROJECT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown technical source");
        verifyNoInteractions(recordingUnitRepository);
    }

    @Test
    void read_hierarchy_givesOneRowPerParentChildPair() {
        RecordingUnit parent = unit(1L, null);
        RecordingUnit c1 = unit(2L, null);
        RecordingUnit c2 = unit(3L, null);
        parent.setChildren(new HashSet<>(Set.of(c1, c2)));
        c1.setChildren(new HashSet<>());
        c2.setChildren(new HashSet<>());
        when(recordingUnitRepository.findAllByActionUnitId(PROJECT_ID)).thenReturn(List.of(parent, c1, c2));

        List<ExportRow> rows = reader.read(new TechnicalSource("RECORDING_UNIT_HIERARCHY"), PROJECT_ID);

        assertThat(rows).hasSize(2).allSatisfy(r -> {
            assertThat(r.isTechnical()).isTrue();
            assertThat(r.ends().get("parent").entity()).isSameAs(parent);
        });
        assertThat(rows).extracting(r -> r.ends().get("child").entity()).containsExactlyInAnyOrder(c1, c2);
    }

    @Test
    void read_stratigraphy_exposesEndsAndValues() {
        RecordingUnit u1 = unit(1L, null);
        RecordingUnit u2 = unit(2L, null);
        Concept relation = concept("th", "sur");
        StratigraphicRelationship rel = new StratigraphicRelationship();
        rel.setUnit1(u1);
        rel.setUnit2(u2);
        rel.setConcept(relation);
        rel.setUncertain(true);
        when(recordingUnitRepository.findAllByActionUnitId(PROJECT_ID)).thenReturn(List.of(u1, u2));
        when(stratigraphicRelationshipRepository.findAllByUnit1IdIn(List.of(1L, 2L))).thenReturn(List.of(rel));

        List<ExportRow> rows = reader.read(new TechnicalSource("STRATIGRAPHIC_RELATIONSHIP"), PROJECT_ID);

        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.ends().get("unit1").entity()).isSameAs(u1);
            assertThat(r.ends().get("unit2").entity()).isSameAs(u2);
            assertThat(r.values()).containsEntry("relationType", relation).containsEntry("uncertain", true)
                    .doesNotContainKey("asynchronous");
        });
    }

    @Test
    void read_stratigraphy_withoutUnits_doesNotQueryRelations() {
        when(recordingUnitRepository.findAllByActionUnitId(PROJECT_ID)).thenReturn(List.of());

        assertThat(reader.read(new TechnicalSource("STRATIGRAPHIC_RELATIONSHIP"), PROJECT_ID)).isEmpty();
        verify(stratigraphicRelationshipRepository, never()).findAllByUnit1IdIn(anyCollection());
    }
}
