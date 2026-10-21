package fr.siamois.domain.services.document;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.document.DocumentImportDraft;
import fr.siamois.domain.models.document.DocumentImportItem;
import fr.siamois.domain.models.document.DocumentImportItemStatus;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.document.DocumentImportDraftRepository;
import fr.siamois.infrastructure.database.repositories.document.DocumentImportItemRepository;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import fr.siamois.infrastructure.database.repositories.specimen.SpecimenRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.files.DocumentImportDraftStorage;
import fr.siamois.utils.DocumentUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Persistable bulk-import draft: one active draft per person+institution, files on disk.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentImportDraftService {

    public static final int MAX_ITEMS = 50;

    private final DocumentImportDraftRepository draftRepository;
    private final DocumentImportItemRepository itemRepository;
    private final DocumentImportDraftStorage draftStorage;
    private final PersonRepository personRepository;
    private final InstitutionRepository institutionRepository;
    private final ConceptRepository conceptRepository;
    private final ActionUnitRepository actionUnitRepository;
    private final RecordingUnitRepository recordingUnitRepository;
    private final SpecimenRepository specimenRepository;
    private final SpatialUnitRepository spatialUnitRepository;

    @Transactional
    public DocumentImportDraft getOrCreateActiveDraft(long personId, long institutionId) {
        return draftRepository.findByPersonIdAndInstitutionId(personId, institutionId)
                .orElseGet(() -> createDraft(personId, institutionId));
    }

    private DocumentImportDraft createDraft(long personId, long institutionId) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new IllegalArgumentException("Person not found: " + personId));
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new IllegalArgumentException("Institution not found: " + institutionId));
        DocumentImportDraft draft = new DocumentImportDraft();
        draft.setPerson(person);
        draft.setInstitution(institution);
        return draftRepository.save(draft);
    }

    @Transactional
    public DocumentImportDraft loadDraftWithItems(long personId, long institutionId) {
        DocumentImportDraft draft = getOrCreateActiveDraft(personId, institutionId);
        Hibernate.initialize(draft.getItems());
        draft.getItems().forEach(this::initializeTargets);
        return draft;
    }

    private void initializeTargets(DocumentImportItem item) {
        Hibernate.initialize(item.getProjects());
        Hibernate.initialize(item.getRecordingUnits());
        Hibernate.initialize(item.getFinds());
        Hibernate.initialize(item.getPlaces());
    }

    @Transactional
    public void rememberDefaultTarget(long personId, long institutionId, String targetType, Long targetId) {
        DocumentImportDraft draft = getOrCreateActiveDraft(personId, institutionId);
        draft.setDefaultTargetType(targetType);
        draft.setDefaultTargetId(targetId);
        draftRepository.save(draft);
    }

    @Transactional
    public DocumentImportItem addFile(long personId,
                                      long institutionId,
                                      String originalFileName,
                                      String contentType,
                                      InputStream content,
                                      ItemDefaults defaults) throws IOException {
        DocumentImportDraft draft = getOrCreateActiveDraft(personId, institutionId);
        long count = itemRepository.countByDraftId(draft.getId());
        if (count >= MAX_ITEMS) {
            throw new IllegalStateException("MAX_ITEMS");
        }

        byte[] bytes = content.readAllBytes();
        if (bytes.length == 0) {
            throw new IllegalArgumentException("EMPTY_FILE");
        }
        String md5 = DocumentUtils.md5(bytes);
        String storedName;
        try (InputStream in = new java.io.ByteArrayInputStream(bytes)) {
            storedName = draftStorage.save(institutionId, draft.getId(), originalFileName, in);
        }

        DocumentImportItem item = new DocumentImportItem();
        item.setDraft(draft);
        item.setClientKey(UUID.randomUUID().toString());
        item.setOriginalFileName(originalFileName);
        item.setContentType(contentType);
        item.setFileSize((long) bytes.length);
        item.setMd5Sum(md5);
        item.setStoredFileName(storedName);
        String title = originalFileName;
        if (title != null && title.contains(".")) {
            title = title.substring(0, title.lastIndexOf('.'));
        }
        item.setTitle(title);
        item.setStatus(DocumentImportItemStatus.DRAFT);

        if (defaults != null) {
            applyDefaultTargets(item, defaults);
        }

        DocumentImportItem saved = itemRepository.save(item);
        draftRepository.save(draft); // touch updated_at
        initializeTargets(saved);
        return saved;
    }

    private void applyDefaultTargets(DocumentImportItem item, ItemDefaults defaults) {
        defaults.projectIds().stream()
                .map(id -> actionUnitRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .forEach(item.getProjects()::add);
        defaults.recordingUnitIds().stream()
                .map(id -> recordingUnitRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .forEach(item.getRecordingUnits()::add);
        defaults.findIds().stream()
                .map(id -> specimenRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .forEach(item.getFinds()::add);
        defaults.placeIds().stream()
                .map(id -> spatialUnitRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .forEach(item.getPlaces()::add);
    }

    @Transactional
    public DocumentImportItem updateItemMetadata(long personId,
                                                   long institutionId,
                                                   long itemId,
                                                   ItemMetadata metadata) {
        DocumentImportDraft draft = requireDraft(personId, institutionId);
        DocumentImportItem item = itemRepository.findByIdAndDraftId(itemId, draft.getId())
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));

        item.setTitle(metadata.title());
        item.setDescription(metadata.description());
        item.setNature(resolveConcept(metadata.natureConceptId()));
        item.setFormatConcept(resolveConcept(metadata.formatConceptId()));
        item.setScale(resolveConcept(metadata.scaleConceptId()));
        item.setStatus(DocumentImportItemStatus.DRAFT);
        item.setErrorMessage(null);

        replaceTargets(item, metadata);

        DocumentImportItem saved = itemRepository.save(item);
        draftRepository.save(draft);
        initializeTargets(saved);
        return saved;
    }

    private void replaceTargets(DocumentImportItem item, ItemMetadata metadata) {
        item.getProjects().clear();
        item.getRecordingUnits().clear();
        item.getFinds().clear();
        item.getPlaces().clear();
        applyDefaultTargets(item, new ItemDefaults(
                metadata.projectIds(),
                metadata.recordingUnitIds(),
                metadata.findIds(),
                metadata.placeIds()));
    }

    private Concept resolveConcept(Long conceptId) {
        if (conceptId == null) {
            return null;
        }
        return conceptRepository.findById(conceptId).orElse(null);
    }

    @Transactional
    public void removeItem(long personId, long institutionId, long itemId) {
        DocumentImportDraft draft = requireDraft(personId, institutionId);
        DocumentImportItem item = itemRepository.findByIdAndDraftId(itemId, draft.getId())
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));
        draftStorage.deleteItem(institutionId, draft.getId(), item.getStoredFileName());
        itemRepository.delete(item);
        draft.getItems().removeIf(i -> itemId == Objects.requireNonNullElse(i.getId(), -1L));
        draftRepository.save(draft);
    }

    @Transactional
    public void abandonDraft(long personId, long institutionId) {
        draftRepository.findByPersonIdAndInstitutionId(personId, institutionId).ifPresent(draft -> {
            draftStorage.deleteDraftTree(institutionId, draft.getId());
            draftRepository.delete(draft);
        });
    }

    @Transactional(readOnly = true)
    public byte[] readFileBytes(long personId, long institutionId, long itemId) throws IOException {
        DocumentImportDraft draft = requireDraft(personId, institutionId);
        DocumentImportItem item = itemRepository.findByIdAndDraftId(itemId, draft.getId())
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));
        return draftStorage.readAllBytes(institutionId, draft.getId(), item.getStoredFileName());
    }

    @Transactional
    public void markItemError(long personId, long institutionId, long itemId, String message) {
        DocumentImportDraft draft = requireDraft(personId, institutionId);
        DocumentImportItem item = itemRepository.findByIdAndDraftId(itemId, draft.getId())
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));
        item.setStatus(DocumentImportItemStatus.ERROR);
        item.setErrorMessage(truncate(message, 2000));
        itemRepository.save(item);
        draftRepository.save(draft);
    }

    @Transactional
    public void removeItemAfterSuccessfulSubmit(long personId, long institutionId, long itemId) {
        removeItem(personId, institutionId, itemId);
    }

    private DocumentImportDraft requireDraft(long personId, long institutionId) {
        return draftRepository.findByPersonIdAndInstitutionId(personId, institutionId)
                .orElseThrow(() -> new IllegalStateException("No draft for person/institution"));
    }

    private static String truncate(String message, int max) {
        if (message == null) {
            return null;
        }
        return message.length() <= max ? message : message.substring(0, max);
    }

    /** Default targets applied only to newly uploaded files. */
    public record ItemDefaults(
            Set<Long> projectIds,
            Set<Long> recordingUnitIds,
            Set<Long> findIds,
            Set<Long> placeIds
    ) {
        public ItemDefaults {
            projectIds = projectIds == null ? Set.of() : Set.copyOf(projectIds);
            recordingUnitIds = recordingUnitIds == null ? Set.of() : Set.copyOf(recordingUnitIds);
            findIds = findIds == null ? Set.of() : Set.copyOf(findIds);
            placeIds = placeIds == null ? Set.of() : Set.copyOf(placeIds);
        }

        public static ItemDefaults empty() {
            return new ItemDefaults(Set.of(), Set.of(), Set.of(), Set.of());
        }
    }

    /** Full metadata snapshot for one item (replace semantics for targets). */
    public record ItemMetadata(
            String title,
            String description,
            Long natureConceptId,
            Long formatConceptId,
            Long scaleConceptId,
            Set<Long> projectIds,
            Set<Long> recordingUnitIds,
            Set<Long> findIds,
            Set<Long> placeIds
    ) {
        public ItemMetadata {
            projectIds = projectIds == null ? Set.of() : Set.copyOf(projectIds);
            recordingUnitIds = recordingUnitIds == null ? Set.of() : Set.copyOf(recordingUnitIds);
            findIds = findIds == null ? Set.of() : Set.copyOf(findIds);
            placeIds = placeIds == null ? Set.of() : Set.copyOf(placeIds);
            title = StringUtils.hasText(title) ? title.trim() : title;
            description = description == null ? null : description.trim();
        }
    }
}
