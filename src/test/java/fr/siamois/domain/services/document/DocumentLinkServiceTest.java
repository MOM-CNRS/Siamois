package fr.siamois.domain.services.document;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.infrastructure.database.repositories.DocumentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentLinkServiceTest {

    @Mock
    private EntityManager entityManager;
    @Mock
    private DocumentRepository documentRepository;

    private DocumentLinkService service;
    private Document document;

    @BeforeEach
    void setUp() {
        service = new DocumentLinkService(entityManager, documentRepository);
        document = new Document();
        document.setId(9L);
    }

    private static ActionUnit project(long id) {
        ActionUnit au = new ActionUnit();
        au.setId(id);
        return au;
    }

    private static Institution institution(long id) {
        Institution i = new Institution();
        i.setId(id);
        return i;
    }

    @Test
    void kind_isFoundByItsPathSegment_andHasItsEntityClass() {
        assertThat(DocumentLinkKind.ofPathSegment("recording-units")).contains(DocumentLinkKind.RECORDING_UNIT);
        assertThat(DocumentLinkKind.ofPathSegment("places")).contains(DocumentLinkKind.PLACE);
        assertThat(DocumentLinkKind.ofPathSegment("nope")).isEmpty();
        assertThat(DocumentLinkKind.FIND.entityClass()).isEqualTo(Specimen.class);
        assertThat(DocumentLinkKind.PHASE.pathSegment()).isEqualTo("phases");
        assertThat(DocumentLinkKind.CONTAINER.documentsLinkedTo(1L)).isNotNull();
    }

    @Test
    void findTarget_givesTheInstitutionAndTheProjectOfEachKind() {
        RecordingUnit ru = new RecordingUnit();
        ru.setActionUnit(project(5L));
        ru.setCreatedByInstitution(institution(2L));
        when(entityManager.find(RecordingUnit.class, 1L)).thenReturn(ru);
        Specimen find = new Specimen();
        find.setActionUnit(project(6L));
        find.setCreatedByInstitution(institution(2L));
        when(entityManager.find(Specimen.class, 2L)).thenReturn(find);
        Phase phase = new Phase();
        phase.setActionUnit(project(7L));
        phase.setCreatedByInstitution(institution(2L));
        when(entityManager.find(Phase.class, 3L)).thenReturn(phase);
        Container container = new Container();
        container.setActionUnit(project(8L));
        container.setCreatedByInstitution(institution(2L));
        when(entityManager.find(Container.class, 4L)).thenReturn(container);
        SpatialUnit place = new SpatialUnit();
        place.setCreatedByInstitution(institution(2L));
        when(entityManager.find(SpatialUnit.class, 5L)).thenReturn(place);

        assertThat(service.findTarget(DocumentLinkKind.RECORDING_UNIT, 1L)).contains(new DocumentLinkService.Target(2L, 5L));
        assertThat(service.findTarget(DocumentLinkKind.FIND, 2L)).contains(new DocumentLinkService.Target(2L, 6L));
        assertThat(service.findTarget(DocumentLinkKind.PHASE, 3L)).contains(new DocumentLinkService.Target(2L, 7L));
        assertThat(service.findTarget(DocumentLinkKind.CONTAINER, 4L)).contains(new DocumentLinkService.Target(2L, 8L));
        assertThat(service.findTarget(DocumentLinkKind.PLACE, 5L)).contains(new DocumentLinkService.Target(2L, null));
    }

    @Test
    void findTarget_ofAnUnknownEntity_isEmpty() {
        assertThat(service.findTarget(DocumentLinkKind.PHASE, 99L)).isEmpty();
    }

    @Test
    void findTarget_ofAnEntityWithoutProjectOrInstitution_hasNoneOfThem() {
        Phase phase = new Phase();
        when(entityManager.find(Phase.class, 3L)).thenReturn(phase);

        assertThat(service.findTarget(DocumentLinkKind.PHASE, 3L)).contains(new DocumentLinkService.Target(null, null));
    }

    @Test
    void link_addsTheEntityToTheDocumentOfItsKind_andIsIdempotent() {
        when(documentRepository.findById(9L)).thenReturn(Optional.of(document));
        Phase phase = new Phase();
        phase.setId(3L);
        when(entityManager.find(Phase.class, 3L)).thenReturn(phase);

        assertThat(service.link(9L, DocumentLinkKind.PHASE, 3L)).isTrue();
        assertThat(service.link(9L, DocumentLinkKind.PHASE, 3L)).isFalse();
        assertThat(document.getPhases()).containsExactly(phase);
        assertThat(document.getContainers()).isEmpty();
    }

    @Test
    void link_coversEveryKind() {
        when(documentRepository.findById(9L)).thenReturn(Optional.of(document));
        RecordingUnit ru = new RecordingUnit();
        ru.setId(1L);
        Specimen find = new Specimen();
        find.setId(2L);
        Container container = new Container();
        container.setId(4L);
        SpatialUnit place = new SpatialUnit();
        place.setId(5L);
        when(entityManager.find(RecordingUnit.class, 1L)).thenReturn(ru);
        when(entityManager.find(Specimen.class, 2L)).thenReturn(find);
        when(entityManager.find(Container.class, 4L)).thenReturn(container);
        when(entityManager.find(SpatialUnit.class, 5L)).thenReturn(place);

        assertThat(service.link(9L, DocumentLinkKind.RECORDING_UNIT, 1L)).isTrue();
        assertThat(service.link(9L, DocumentLinkKind.FIND, 2L)).isTrue();
        assertThat(service.link(9L, DocumentLinkKind.CONTAINER, 4L)).isTrue();
        assertThat(service.link(9L, DocumentLinkKind.PLACE, 5L)).isTrue();
        assertThat(document.getRecordingUnits()).containsExactly(ru);
        assertThat(document.getFinds()).containsExactly(find);
        assertThat(document.getContainers()).containsExactly(container);
        assertThat(document.getPlaces()).containsExactly(place);
    }

    @Test
    void unlink_removesOnlyTheLinkOfThatKind_andIsIdempotent() {
        when(documentRepository.findById(9L)).thenReturn(Optional.of(document));
        RecordingUnit ru = new RecordingUnit();
        ru.setId(1L);
        Specimen find = new Specimen();
        find.setId(1L);
        Phase phase = new Phase();
        phase.setId(1L);
        Container container = new Container();
        container.setId(1L);
        SpatialUnit place = new SpatialUnit();
        place.setId(1L);
        document.getRecordingUnits().add(ru);
        document.getFinds().add(find);
        document.getPhases().add(phase);
        document.getContainers().add(container);
        document.getPlaces().add(place);

        assertThat(service.unlink(9L, DocumentLinkKind.RECORDING_UNIT, 1L)).isTrue();
        assertThat(service.unlink(9L, DocumentLinkKind.RECORDING_UNIT, 1L)).isFalse();
        assertThat(service.unlink(9L, DocumentLinkKind.FIND, 1L)).isTrue();
        assertThat(service.unlink(9L, DocumentLinkKind.PHASE, 1L)).isTrue();
        assertThat(service.unlink(9L, DocumentLinkKind.CONTAINER, 1L)).isTrue();
        assertThat(service.unlink(9L, DocumentLinkKind.PLACE, 1L)).isTrue();
        assertThat(document.getRecordingUnits()).isEmpty();
        assertThat(document.getFinds()).isEmpty();
        assertThat(document.getPhases()).isEmpty();
        assertThat(document.getContainers()).isEmpty();
        assertThat(document.getPlaces()).isEmpty();
    }

    @Test
    void link_ofAnUnknownDocumentOrEntity_isRefused() {
        when(documentRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.link(1L, DocumentLinkKind.PHASE, 3L)).isInstanceOf(IllegalArgumentException.class);

        when(documentRepository.findById(9L)).thenReturn(Optional.of(document));
        assertThatThrownBy(() -> service.link(9L, DocumentLinkKind.PHASE, 3L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.unlink(1L, DocumentLinkKind.PHASE, 3L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void link_toADocumentCreatedInTheSameTransaction_reloadsItsCollectionsFirst() throws Exception {
        Document fresh = new Document();
        fresh.setId(9L);
        java.lang.reflect.Field field = Document.class.getDeclaredField("recordingUnits");
        field.setAccessible(true);
        field.set(fresh, null);
        when(documentRepository.findById(9L)).thenReturn(Optional.of(fresh));
        RecordingUnit ru = new RecordingUnit();
        ru.setId(1L);
        when(entityManager.find(RecordingUnit.class, 1L)).thenReturn(ru);
        org.mockito.Mockito.doAnswer(invocation -> {
            field.set(fresh, new java.util.HashSet<>());
            return null;
        }).when(entityManager).refresh(fresh);

        assertThat(service.link(9L, DocumentLinkKind.RECORDING_UNIT, 1L)).isTrue();

        org.mockito.Mockito.verify(entityManager).flush();
        assertThat(fresh.getRecordingUnits()).containsExactly(ru);
    }
}
