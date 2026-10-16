package fr.siamois.domain.services.document;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.document.DocumentImportDraft;
import fr.siamois.domain.models.document.DocumentImportItem;
import fr.siamois.domain.models.document.DocumentImportItemStatus;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.document.DocumentImportDraftRepository;
import fr.siamois.infrastructure.database.repositories.document.DocumentImportItemRepository;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.specimen.SpecimenRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.files.DocumentImportDraftStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentImportDraftServiceTest {

    @Mock private DocumentImportDraftRepository draftRepository;
    @Mock private DocumentImportItemRepository itemRepository;
    @Mock private DocumentImportDraftStorage draftStorage;
    @Mock private PersonRepository personRepository;
    @Mock private InstitutionRepository institutionRepository;
    @Mock private ConceptRepository conceptRepository;
    @Mock private ActionUnitRepository actionUnitRepository;
    @Mock private RecordingUnitRepository recordingUnitRepository;
    @Mock private SpecimenRepository specimenRepository;
    @Mock private SpatialUnitRepository spatialUnitRepository;

    private DocumentImportDraftService service;
    private Person person;
    private Institution institution;
    private DocumentImportDraft draft;

    @BeforeEach
    void setUp() {
        service = new DocumentImportDraftService(
                draftRepository,
                itemRepository,
                draftStorage,
                personRepository,
                institutionRepository,
                conceptRepository,
                actionUnitRepository,
                recordingUnitRepository,
                specimenRepository,
                spatialUnitRepository);

        person = new Person();
        person.setId(7L);
        institution = new Institution();
        institution.setId(10L);
        draft = new DocumentImportDraft();
        draft.setId(3L);
        draft.setPerson(person);
        draft.setInstitution(institution);
        draft.setItems(new ArrayList<>());
    }

    @Test
    void getOrCreateActiveDraft_reusesExisting() {
        when(draftRepository.findByPersonIdAndInstitutionId(7L, 10L)).thenReturn(Optional.of(draft));

        DocumentImportDraft result = service.getOrCreateActiveDraft(7L, 10L);

        assertThat(result).isSameAs(draft);
        verify(draftRepository, never()).save(any());
    }

    @Test
    void getOrCreateActiveDraft_createsWhenMissing() {
        when(draftRepository.findByPersonIdAndInstitutionId(7L, 10L)).thenReturn(Optional.empty());
        when(personRepository.findById(7L)).thenReturn(Optional.of(person));
        when(institutionRepository.findById(10L)).thenReturn(Optional.of(institution));
        when(draftRepository.save(any())).thenAnswer(inv -> {
            DocumentImportDraft d = inv.getArgument(0);
            d.setId(99L);
            return d;
        });

        DocumentImportDraft result = service.getOrCreateActiveDraft(7L, 10L);

        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getPerson()).isEqualTo(person);
        assertThat(result.getInstitution()).isEqualTo(institution);
    }

    @Test
    void addFile_writesDiskAndPersistsItem() throws Exception {
        when(draftRepository.findByPersonIdAndInstitutionId(7L, 10L)).thenReturn(Optional.of(draft));
        when(itemRepository.countByDraftId(3L)).thenReturn(0L);
        when(draftStorage.save(eq(10L), eq(3L), eq("plan.pdf"), any())).thenReturn("uuid.pdf");
        when(itemRepository.save(any())).thenAnswer(inv -> {
            DocumentImportItem item = inv.getArgument(0);
            item.setId(55L);
            return item;
        });
        when(draftRepository.save(draft)).thenReturn(draft);

        DocumentImportItem item = service.addFile(
                7L, 10L, "plan.pdf", "application/pdf",
                new ByteArrayInputStream(new byte[]{1, 2, 3}),
                DocumentImportDraftService.ItemDefaults.empty());

        assertThat(item.getId()).isEqualTo(55L);
        assertThat(item.getOriginalFileName()).isEqualTo("plan.pdf");
        assertThat(item.getStoredFileName()).isEqualTo("uuid.pdf");
        assertThat(item.getTitle()).isEqualTo("plan");
        assertThat(item.getMd5Sum()).isNotBlank();
        assertThat(item.getStatus()).isEqualTo(DocumentImportItemStatus.DRAFT);
        verify(draftStorage).save(eq(10L), eq(3L), eq("plan.pdf"), any());
    }

    @Test
    void addFile_rejectsWhenMaxItemsReached() {
        when(draftRepository.findByPersonIdAndInstitutionId(7L, 10L)).thenReturn(Optional.of(draft));
        when(itemRepository.countByDraftId(3L)).thenReturn((long) DocumentImportDraftService.MAX_ITEMS);

        assertThatThrownBy(() -> service.addFile(
                7L, 10L, "x.pdf", "application/pdf",
                new ByteArrayInputStream(new byte[]{1}),
                DocumentImportDraftService.ItemDefaults.empty()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("MAX_ITEMS");
    }

    @Test
    void updateItemMetadata_replacesTargetsAndConcepts() {
        when(draftRepository.findByPersonIdAndInstitutionId(7L, 10L)).thenReturn(Optional.of(draft));
        DocumentImportItem item = new DocumentImportItem();
        item.setId(55L);
        item.setDraft(draft);
        item.setProjects(new java.util.HashSet<>());
        item.setRecordingUnits(new java.util.HashSet<>());
        item.setFinds(new java.util.HashSet<>());
        item.setPlaces(new java.util.HashSet<>());
        when(itemRepository.findByIdAndDraftId(55L, 3L)).thenReturn(Optional.of(item));
        when(itemRepository.save(item)).thenReturn(item);
        when(draftRepository.save(draft)).thenReturn(draft);

        DocumentImportDraftService.ItemMetadata meta = new DocumentImportDraftService.ItemMetadata(
                "Titre", "Desc", null, null, null,
                Set.of(), Set.of(), Set.of(), Set.of());

        DocumentImportItem updated = service.updateItemMetadata(7L, 10L, 55L, meta);

        assertThat(updated.getTitle()).isEqualTo("Titre");
        assertThat(updated.getDescription()).isEqualTo("Desc");
        assertThat(updated.getStatus()).isEqualTo(DocumentImportItemStatus.DRAFT);
    }

    @Test
    void removeItem_deletesFileAndRow() {
        when(draftRepository.findByPersonIdAndInstitutionId(7L, 10L)).thenReturn(Optional.of(draft));
        DocumentImportItem item = new DocumentImportItem();
        item.setId(55L);
        item.setDraft(draft);
        item.setStoredFileName("uuid.pdf");
        when(itemRepository.findByIdAndDraftId(55L, 3L)).thenReturn(Optional.of(item));
        when(draftRepository.save(draft)).thenReturn(draft);

        service.removeItem(7L, 10L, 55L);

        verify(draftStorage).deleteItem(10L, 3L, "uuid.pdf");
        verify(itemRepository).delete(item);
    }

    @Test
    void abandonDraft_purgesDiskAndDatabase() {
        when(draftRepository.findByPersonIdAndInstitutionId(7L, 10L)).thenReturn(Optional.of(draft));

        service.abandonDraft(7L, 10L);

        verify(draftStorage).deleteDraftTree(10L, 3L);
        verify(draftRepository).delete(draft);
    }

    @Test
    void loadDraftWithItems_returnsOrderedItems() {
        when(draftRepository.findByPersonIdAndInstitutionId(7L, 10L)).thenReturn(Optional.of(draft));
        DocumentImportItem a = new DocumentImportItem();
        a.setId(1L);
        a.setProjects(new java.util.HashSet<>());
        a.setRecordingUnits(new java.util.HashSet<>());
        a.setFinds(new java.util.HashSet<>());
        a.setPlaces(new java.util.HashSet<>());
        draft.setItems(new ArrayList<>(List.of(a)));

        DocumentImportDraft loaded = service.loadDraftWithItems(7L, 10L);

        assertThat(loaded.getItems()).containsExactly(a);
    }

    @Test
    void removeItemAfterSuccessfulSubmit_deletesItemAndFile() {
        when(draftRepository.findByPersonIdAndInstitutionId(7L, 10L)).thenReturn(Optional.of(draft));
        DocumentImportItem item = new DocumentImportItem();
        item.setId(55L);
        item.setDraft(draft);
        item.setStoredFileName("done.pdf");
        when(itemRepository.findByIdAndDraftId(55L, 3L)).thenReturn(Optional.of(item));
        when(draftRepository.save(draft)).thenReturn(draft);

        service.removeItemAfterSuccessfulSubmit(7L, 10L, 55L);

        verify(draftStorage).deleteItem(10L, 3L, "done.pdf");
        verify(itemRepository).delete(item);
    }

    @Test
    void markItemError_setsStatusAndMessage() {
        when(draftRepository.findByPersonIdAndInstitutionId(7L, 10L)).thenReturn(Optional.of(draft));
        DocumentImportItem item = new DocumentImportItem();
        item.setId(55L);
        when(itemRepository.findByIdAndDraftId(55L, 3L)).thenReturn(Optional.of(item));
        when(itemRepository.save(item)).thenReturn(item);
        when(draftRepository.save(draft)).thenReturn(draft);

        service.markItemError(7L, 10L, 55L, "boom");

        assertThat(item.getStatus()).isEqualTo(DocumentImportItemStatus.ERROR);
        assertThat(item.getErrorMessage()).isEqualTo("boom");
    }
}
