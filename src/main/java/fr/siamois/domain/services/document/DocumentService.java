package fr.siamois.domain.services.document;

import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.InvalidFileSizeException;
import fr.siamois.domain.models.exceptions.InvalidFileTypeException;
import fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.services.ArkEntityService;
import fr.siamois.domain.services.document.compressor.FileCompressor;
import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.domain.services.identifier.EntityIdentifierGenerator;
import fr.siamois.domain.services.identifier.IdentifierGenerationSpec;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.DocumentRepository;
import fr.siamois.infrastructure.database.repositories.specs.DocumentSpec;
import fr.siamois.infrastructure.files.DocumentStorage;
import fr.siamois.mapper.DocumentMapper;
import fr.siamois.mapper.InstitutionMapper;
import fr.siamois.mapper.PersonMapper;
import fr.siamois.ui.viewmodel.fieldanswer.CustomFieldAnswerViewModel;
import fr.siamois.utils.CodeUtils;
import fr.siamois.utils.DocumentUtils;
import fr.siamois.utils.context.ExecutionContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.InvalidFileNameException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeType;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Service for managing documents in the system.
 * This service provides methods to save, retrieve, and validate documents,
 * as well as to handle file uploads and storage.
 */
@Slf4j
@Service
@Setter
@RequiredArgsConstructor
public class DocumentService implements ArkEntityService {


    private final DocumentRepository documentRepository;


    private final PersonMapper personMapper;
    private final InstitutionMapper institutionMapper;
    private final DocumentMapper documentMapper;
    private final EntityIdentifierGenerator identifierGenerator;
    private final ProfilePermissionService profilePermissionService;
    private final CustomFieldAnswerService customFieldAnswerService;
    private final TableFieldConfigService tableFieldConfigService;

    private static final int MAX_GENERATIONS = 100;

    /** Stands for the identifier of a document saved before its id is known (see {@link #allocateIdentifierIfNew}). */
    private static final String PENDING_IDENTIFIER_PREFIX = "DOC-pending-";
    private static final BigDecimal BYTES_PER_MB = BigDecimal.valueOf(1024L * 1024L);
    private final DocumentStorage documentStorage;
    private final Collection<FileCompressor> fileCompressors;



    @Override
    public List<Document> findWithoutArk(Institution institution) {
        return documentRepository.findAllByArkIsNullAndCreatedByInstitution(institution);
    }

    public ArkEntity save(ArkEntity toSave) {
        return documentRepository.save((Document) toSave);
    }

    /**
     * Saves a document that has no associated uploaded file (e.g. it references an external or physical file).
     *
     * @param userInfo the user information for the operation
     * @param document the document to save
     * @return the saved document
     */
    public Document saveWithoutFile(UserInfo userInfo, Document document) {
        document.setCreatedBy(personMapper.invertConvert(userInfo.getUser()));
        document.setCreatedByInstitution(institutionMapper.invertConvert(userInfo.getInstitution()));
        allocateIdentifierIfNew(document);
        return persist(document);
    }

    @Override
    @Transactional
    public AbstractEntityDTO save(AbstractEntityDTO toSave) {
        if (toSave instanceof DocumentDTO dto) {
            return doSave(dto);
        }
        throw new UnsupportedOperationException("DocumentService only saves documents");
    }

    private Specification<Document> userFilterSpecs(FilterDTO filters) {
        // The list's per-field sort/filters (FieldQuery), then the named ones.
        Specification<Document> specs = filters.getFieldQuery().specificationFor();

        FilterDTO.FilterInfo globalFilter = filters.filterOf(DocumentSpec.GLOBAL_FILTER);
        FilterDTO.FilterInfo identifierFilter = filters.filterOf(DocumentSpec.IDENTIFIER_FILTER);

        if (identifierFilter != null && identifierFilter.getType() == FilterDTO.FilterType.CONTAINS) {
            specs = specs.and(DocumentSpec.identifierContaining(identifierFilter.valueAsString()));
        } else if (globalFilter != null && globalFilter.getType() == FilterDTO.FilterType.CONTAINS) {
            specs = specs.and(DocumentSpec.identifierContaining(globalFilter.valueAsString()));
        }

        if (filters.containsColumn(DocumentSpec.ACTION_UNIT_FILTER)) {
            specs = specs.and(DocumentSpec.isInActionUnit(filters.valueAsIdListOf(DocumentSpec.ACTION_UNIT_FILTER)));
        }
        return specs;
    }

    private Specification<Document> prepareSpecs(InstitutionDTO institutionDTO, FilterDTO filters) {
        return DocumentSpec.belongsToInstitution(institutionDTO.getId()).and(userFilterSpecs(filters));
    }

    @Transactional(readOnly = true)
    public Page<DocumentDTO> searchDocuments(InstitutionDTO institutionDTO, FilterDTO filters, Pageable pageable) {
        return documentRepository.findAll(prepareSpecs(institutionDTO, filters), pageable)
                .map(documentMapper::convert);
    }

    /** The documents linked to one entity (a recording unit, a find…), searched and paged like the project's. */
    @Transactional(readOnly = true)
    public Page<DocumentDTO> searchDocumentsLinkedTo(InstitutionDTO institutionDTO, DocumentLinkKind kind, long targetId,
                                                     FilterDTO filters, Pageable pageable) {
        return documentRepository.findAll(prepareSpecs(institutionDTO, filters).and(kind.documentsLinkedTo(targetId)), pageable)
                .map(documentMapper::convert);
    }

    /** How many documents are linked to one entity — the badge of its Documents tab. */
    @Transactional(readOnly = true)
    public long countLinkedTo(DocumentLinkKind kind, long targetId) {
        return documentRepository.count(kind.documentsLinkedTo(targetId));
    }

    @Transactional(readOnly = true)
    public int countSearchResults(InstitutionDTO institutionDTO, FilterDTO filters) {
        return Math.toIntExact(documentRepository.count(prepareSpecs(institutionDTO, filters)));
    }

    public int countByActionContext(ActionUnitDTO actionUnit) {
        return documentRepository.countByActionUnitId(actionUnit.getId());
    }

    @Transactional(readOnly = true)
    public boolean identifierAlreadyExistInProject(DocumentDTO document) {
        if (document.getActionUnit() == null) {
            return false;
        }
        return documentRepository.findByIdentifierAndActionUnitId(document.getIdentifier(), document.getActionUnit().getId())
                .filter(existing -> !Objects.equals(existing.getId(), document.getId()))
                .isPresent();
    }

    /**
     * Saves the document, then its additional (non-system) field answers — the counterpart of
     * {@code PhaseService.save(PhaseDTO, Map)}.
     *
     * @param dto                    the document to save
     * @param additionalFieldAnswers answers to the type's additional fields, keyed by field
     * @return the saved document
     */
    @Transactional
    public DocumentDTO save(DocumentDTO dto, Map<CustomField, CustomFieldAnswerViewModel> additionalFieldAnswers) {
        DocumentDTO saved = doSave(dto);
        customFieldAnswerService.saveAdditionalFieldAnswers(saved, additionalFieldAnswers);
        return saved;
    }

    /**
     * Saves the document's own fields and its links. The stored file's columns are never written from
     * here: they belong to the upload ({@link #saveFile}).
     *
     * @throws IllegalArgumentException if the document has no project
     * @throws ForbiddenOperationException if the user cannot edit documents of the project
     * @throws IllegalStateException if the identifier is already used in the project
     */
    @Transactional
    public DocumentDTO save(DocumentDTO dto) {
        return doSave(dto);
    }

    private DocumentDTO doSave(DocumentDTO dto) {
        Document entity = documentMapper.invertConvert(dto);
        Document managed = entity.getId() == null
                ? entity
                : documentRepository.findById(entity.getId()).orElse(entity);

        if (managed != entity) {
            copyEditableFields(entity, managed);
        }

        if (managed.getActionUnit() == null) {
            throw new IllegalArgumentException("A project is required to save a document");
        }

        UserInfo info = ExecutionContextHolder.get();
        if (info == null || !profilePermissionService.hasProjectPermission(
                info, managed.getActionUnit().getId(), PermissionConstants.INSTANCE_EDIT_DOCUMENTS,
                PermissionConstants.ORGANIZATION_EDIT_DOCUMENTS, PermissionConstants.PROJECT_EDIT_DOCUMENTS)) {
            throw new ForbiddenOperationException("You are not allowed to edit this document");
        }

        if (managed.getId() == null) {
            managed.setCreatedBy(personMapper.invertConvert(info.getUser()));
            managed.setCreatedByInstitution(institutionMapper.invertConvert(info.getInstitution()));
        }

        allocateIdentifierIfNew(managed);
        if (managed.getIdentifier() == null || managed.getIdentifier().isBlank()) {
            throw new IllegalArgumentException("A document needs an identifier");
        }
        if (documentRepository.findByIdentifierAndActionUnitId(managed.getIdentifier(), managed.getActionUnit().getId())
                .filter(existing -> !Objects.equals(existing.getId(), managed.getId()))
                .isPresent()) {
            throw new IllegalStateException("Identifier " + managed.getIdentifier() + " is already used in this project");
        }

        return documentMapper.convert(persist(managed));
    }

    /** What a form save writes: everything but the stored file's columns and the project (fixed at creation). */
    private static void copyEditableFields(Document from, Document to) {
        to.setIdentifier(from.getIdentifier());
        to.setOtherIdentifiers(from.getOtherIdentifiers());
        to.setType(from.getType());
        to.setDocumentType(from.getDocumentType());
        to.setFormat(from.getFormat());
        to.setTitle(from.getTitle());
        to.setPublisher(from.getPublisher());
        to.setProductionDate(from.getProductionDate());
        to.setDescription(from.getDescription());
        to.setScale(from.getScale());
        to.setLanguage(from.getLanguage());
        to.setRights(from.getRights());
        to.setItemCount(from.getItemCount());
        to.setSizeMb(from.getSizeMb());
        to.setCrs(from.getCrs());
        to.setOriginalPath(from.getOriginalPath());
        to.setComments(from.getComments());
        to.setExternalUrl(from.getExternalUrl());
        synchronizeCollection(to.getSupportNatures(), from.getSupportNatures());
        synchronizeCollection(to.getKeywords(), from.getKeywords());
        synchronizeCollection(to.getAuthors(), from.getAuthors());
        synchronizeCollection(to.getContributors(), from.getContributors());
        synchronizeCollection(to.getRecordingUnits(), from.getRecordingUnits());
        synchronizeCollection(to.getFinds(), from.getFinds());
        synchronizeCollection(to.getPlaces(), from.getPlaces());
        synchronizeCollection(to.getPhases(), from.getPhases());
        synchronizeCollection(to.getContainers(), from.getContainers());
    }

    private static <T> void synchronizeCollection(Collection<T> managed, Collection<T> incoming) {
        if (managed == null) return;
        if (incoming == null || incoming.isEmpty()) {
            managed.clear();
            return;
        }
        managed.retainAll(incoming);
        for (T item : incoming) {
            if (!managed.contains(item)) managed.add(item);
        }
    }

    /**
     * A new document attached to a project gets its identifier from the project's format, unless it brings one.
     * <p>
     * That format lives on the form configuration of the type field ({@link Document#TYPE_FIELD}), which the
     * institution's thesaurus has to declare. Where it does not (documents come from the mobile API too, which
     * does not know about categories), the document gets a provisional identifier, replaced by {@code DOC-<id>}
     * as soon as the row exists, rather than the creation failing.
     */
    private void allocateIdentifierIfNew(Document document) {
        if (document.getId() != null || document.getActionUnit() == null
                || (document.getIdentifier() != null && !document.getIdentifier().isBlank())) {
            return;
        }
        if (tableFieldConfigService.isTypeFieldConfigured(document.getActionUnit().getId(), ConfigurableTable.DOCUMENT)) {
            identifierGenerator.generateIdentifierIfRequired(document, documentIdentifierSpec());
        } else {
            document.setIdentifier(PENDING_IDENTIFIER_PREFIX + java.util.UUID.randomUUID());
        }
    }

    /** Persists the document, giving it its final identifier when it only had a provisional one. */
    private Document persist(Document document) {
        Document saved = documentRepository.save(document);
        if (saved.getIdentifier() != null && saved.getIdentifier().startsWith(PENDING_IDENTIFIER_PREFIX)) {
            saved.setIdentifier("DOC-" + saved.getId());
            saved = documentRepository.save(saved);
        }
        return saved;
    }

    private IdentifierGenerationSpec<Document> documentIdentifierSpec() {
        return IdentifierGenerationSpec.<Document>builder()
                .table(ConfigurableTable.DOCUMENT)
                .entityName("document")
                // A document that already carries an identifier (typed, or migrated) keeps it.
                .generationRequired(document -> document.getId() == null
                        && (document.getIdentifier() == null || document.getIdentifier().isBlank()))
                .actionUnit(Document::getActionUnit)
                .typeId(document -> document.getType() == null ? null : document.getType().getId())
                .displayValue("ID_UA", document -> document.getActionUnit().getFullIdentifier())
                .identifierAlreadyUsed((document, candidate) ->
                        documentRepository.existsByActionUnitIdAndIdentifier(
                                document.getActionUnit().getId(), candidate))
                .numberSetter(Document::setGeneratedNumber)
                .identifierSetter(Document::setIdentifier)
                .build();
    }

    @Transactional(readOnly = true)
    public DocumentDTO findDtoById(Long id) {
        if (id == null) return null;
        return documentRepository.findById(id).map(documentMapper::convert).orElse(null);
    }

    /**
     * Returns a list of supported MIME types for document uploads.
     *
     * @return a list of supported MIME types
     */
    public List<MimeType> supportedMimeTypes() {
        return documentStorage.supportedMimeTypes();
    }

    private String generateFileInternalCode() {
        String code;
        int counter = 0;
        do {
            code = CodeUtils.generateCode(Document.FILE_INTERNAL_CODE_LENGTH);
            counter++;
        } while (counter < MAX_GENERATIONS && documentRepository.existsByFileCode(code));

        if (counter == MAX_GENERATIONS)
            throw new IllegalStateException(String.format("Could not generate unique file code after %s generations.", MAX_GENERATIONS));

        return code;
    }

    /**
     * Saves a file associated with a document.
     *
     * @param userInfo        the user information for the operation
     * @param document        the document to which the file is associated
     * @param fileInputStream the input stream of the file to be saved
     * @param contextPath     the context path for the file URL
     * @return the saved document with updated file information
     * @throws InvalidFileTypeException if the file type is not supported
     * @throws InvalidFileSizeException if the file size exceeds the allowed limit
     * @throws IOException              if an I/O error occurs during file processing
     */
    public Document saveFile(UserInfo userInfo, Document document, InputStream fileInputStream, String contextPath) throws InvalidFileTypeException, InvalidFileSizeException, IOException {
        log.trace("Started to upload document {} to {}", document.getFileName(), userInfo.getInstitution().getId());

        try (BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream)) {
            checkFileData(document);

            document.setMd5Sum(DocumentUtils.md5(bufferedInputStream));
            document.setFileCode(generateFileInternalCode());
            document.setCreatedBy(personMapper.invertConvert(userInfo.getUser()));
            document.setCreatedByInstitution(institutionMapper.invertConvert(userInfo.getInstitution()));
            document.setUrl(String.format("%s/content/%s", contextPath, document.contentFileName()));
            allocateIdentifierIfNew(document);

            documentStorage.save(userInfo, document, bufferedInputStream);
        }

        log.trace("Finished upload document {} to {}", document.getFileName(), userInfo.getInstitution().getId());

        return persist(document);
    }

    /**
     * Replaces the file of an existing document: the previous bytes are removed from the storage, the new
     * ones stored, and the size (in Mo) and format are filled in — the format only when the document has none.
     * Whoever created the document stays its creator.
     */
    @Transactional(rollbackFor = {InvalidFileTypeException.class, InvalidFileSizeException.class, IOException.class})
    public Document replaceFile(UserInfo userInfo, Document document, String fileName, String mimeType, long size,
                                InputStream fileInputStream, String contextPath)
            throws InvalidFileTypeException, InvalidFileSizeException, IOException {
        Document candidate = new Document();
        candidate.setFileName(fileName);
        candidate.setMimeType(mimeType);
        candidate.setSize(size);
        checkFileData(candidate);

        try (BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream)) {
            String md5 = DocumentUtils.md5(bufferedInputStream);
            if (document.hasFile()) {
                documentStorage.deleteStoredFile(document);
            }
            document.setFileName(fileName);
            document.setMimeType(mimeType);
            document.setSize(size);
            document.setMd5Sum(md5);
            document.setFileCode(generateFileInternalCode());
            document.setUrl(contextPath + "/content/" + document.contentFileName());
            document.setSizeMb(BigDecimal.valueOf(size).divide(BYTES_PER_MB, 3, RoundingMode.HALF_UP));
            if (document.getFormat() == null || document.getFormat().isBlank()) {
                document.setFormat(document.fileExtension().toLowerCase(Locale.ROOT));
            }
            documentStorage.save(userInfo, document, bufferedInputStream);
        }
        return persist(document);
    }

    /** Removes the stored file (bytes and metadata); the document and its external URL stay. */
    @Transactional
    public Document removeFile(Document document) {
        if (document.hasFile()) {
            documentStorage.deleteStoredFile(document);
        }
        document.clearFile();
        return persist(document);
    }

    void checkFileData(Document document) throws InvalidFileTypeException, InvalidFileSizeException {
        List<MimeType> supportedMimeTypes = supportedMimeTypes();
        if (allWildCardIsNotInMimetypes(supportedMimeTypes) && documentMimeTypeIsNotSupported(document)) {
            throw new InvalidFileTypeException(String.format("Type %s is not allowed", document.getMimeType()));
        }


        if (document.getFileName().length() > Document.MAX_FILE_NAME_LENGTH) {
            throw new InvalidFileNameException(document.getFileName(), "File name too long");
        }

        final long maxFileSize = DocumentUtils.byteParser(documentStorage.getMaxUploadSize());

        if (document.getSize() > maxFileSize) {
            throw new InvalidFileSizeException(document.getSize(), String.format("Max file size is %s bytes", maxFileSize));
        }
    }

    private boolean documentMimeTypeIsNotSupported(Document document) {
        return supportedMimeTypes().stream().noneMatch(type -> type.toString().equals(document.getMimeType()));
    }

    private static boolean allWildCardIsNotInMimetypes(List<MimeType> supportedMimeTypes) {
        return supportedMimeTypes.stream().noneMatch(type -> type.toString().equals("*/*"));
    }

    /**
     * Finds a file associated with a document.
     *
     * @param document the document for which the file is to be found
     * @return an Optional containing the file if found, or empty if not found
     */
    public Optional<File> findFile(Document document) {
        return documentStorage.find(document);
    }

    /**
     * Finds a document by its file code.
     *
     * @param fileCode the file code of the document to find
     * @return an Optional containing the document if found, or empty if not found
     */
    public Optional<Document> findByFileCode(String fileCode) {
        return documentRepository.findByFileCode(fileCode);
    }

    /**
     * Document persisté par identifiant technique ({@code document_id}).
     */
    public Optional<Document> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return documentRepository.findById(id);
    }

    /**
     * Finds documents associated with a specific spatial unit.
     *
     * @param spatialUnit the spatial unit for which documents are to be found
     * @return a list of documents associated with the spatial unit
     */
    public List<Document> findForSpatialUnit(SpatialUnitDTO spatialUnit) {
        return documentRepository.findAll(DocumentSpec.linkedToPlace(spatialUnit.getId()));
    }

    /**
     * Finds documents associated with a specific action unit.
     *
     * @param actionUnit the action unit for which documents are to be found
     * @return a list of documents associated with the action unit
     */
    public List<Document> findForActionUnit(ActionUnitDTO actionUnit) {
        return documentRepository.findAll(DocumentSpec.belongsToActionUnit(actionUnit.getId()));
    }

    /**
     * Finds documents associated with a specific recording unit.
     *
     * @param recordingUnit the recording unit for which documents are to be found
     * @return a list of documents associated with the recording unit
     */
    public List<Document> findForRecordingUnit(RecordingUnitDTO recordingUnit) {
        return documentRepository.findAll(DocumentSpec.linkedToRecordingUnit(recordingUnit.getId()));
    }

    /**
     * Finds documents associated with a specific specimen.
     *
     * @param specimen the specimen for which documents are to be found
     * @return a list of documents associated with the specimen
     */
    public List<Document> findForSpecimen(SpecimenDTO specimen) {
        return documentRepository.findAll(DocumentSpec.linkedToFind(specimen.getId()));
    }

    /**
     * Links a document to a spatial unit.
     *
     * @param document    the document to be linked
     * @param spatialUnit the spatial unit to which the document is to be linked
     */
    public void addToSpatialUnit(Document document, SpatialUnitDTO spatialUnit) {
        documentRepository.addDocumentToSpatialUnit(document.getId(), spatialUnit.getId());
    }

    /**
     * Adds a document to a spatial unit.
     *
     * @param document    the document to be added
     * @param specimen the spatial unit to which the document is to be added
     */
    public void addToSpecimen(Document document, SpecimenDTO specimen) {
        documentRepository.addDocumentToSpecimen(document.getId(), specimen.getId());
    }

    /**
     * Attaches a document to its project (the document's single mandatory project).
     *
     * @param document   the document to be attached
     * @param actionUnit the project the document belongs to
     */
    public void addToActionUnit(Document document, ActionUnitDTO actionUnit) {
        documentRepository.attachToActionUnit(document.getId(), actionUnit.getId());
    }

    /**
     * Adds a document to a recording unit.
     *
     * @param document    the document to be added
     * @param recordingUnit the recording unit to which the document is to be added
     */
    public void addToRecordingUnit(Document document, RecordingUnitDTO recordingUnit) {
        documentRepository.addDocumentToRecordingUnit(document.getId(), recordingUnit.getId());
    }

    /**
     * Finds an InputStream for a document.
     *
     * @param document the document for which the InputStream is to be found
     * @return an Optional containing the InputStream if found, or empty if not found
     */
    public Optional<InputStream> findInputStreamOfDocument(Document document) {
        Optional<byte[]> result = documentStorage.findStreamOf(document);
        if (result.isEmpty())
            return Optional.empty();

        ByteArrayInputStream bais = new ByteArrayInputStream(result.get());

        return Optional.of(bais);
    }

    /**
     * Supprime un document : liens de liaison (UE, mobilier, lieux, phases, contenants, études), réponses aux champs
     * additionnels, fichier sur disque, ligne {@code siamois_document}.
     */
    @Transactional
    public void deleteDocument(Document document) {
        if (document == null || document.getId() == null) {
            throw new IllegalArgumentException("Document id is required");
        }
        Long id = document.getId();
        documentRepository.deleteSpatialUnitDocumentLinks(id);
        documentRepository.deleteRecordingUnitDocumentLinks(id);
        documentRepository.deleteSpecimenDocumentLinks(id);
        documentRepository.deletePhaseDocumentLinks(id);
        documentRepository.deleteContainerDocumentLinks(id);
        documentRepository.deleteSpecimenStudyDocumentLinks(id);
        // answers to the type's additional fields hold a foreign key to the document
        DocumentDTO idOnly = new DocumentDTO();
        idOnly.setId(id);
        customFieldAnswerService.deleteAdditionalFieldAnswers(idOnly);
        documentStorage.deleteStoredFile(document);
        documentRepository.delete(document);
    }

    /**
     * Returns the maximum file size allowed for uploads. This limit is set in the application properties.
     *
     * @return the maximum file size in bytes
     */
    public long maxFileSize() {
        return DocumentUtils.byteParser(documentStorage.getMaxUploadSize());
    }

    /**
     * Finds the appropriate file compressor for a given document based on its MIME type.
     *
     * @param document the document for which the compressor is to be found
     * @return the FileCompressor that matches the document's MIME type
     */
    public FileCompressor findCompressorOf(Document document) {
        for (FileCompressor fileCompressor : fileCompressors) {
            if (fileCompressor.isMatchingCompressor(document.mimeTypeObject()))
                return fileCompressor;
        }
        throw new IllegalStateException(String.format("No file compressor found for %s", document.getMimeType()));
    }

    /**
     * Calculates the MD5 checksum of the content of an InputStream.
     *
     * @param inputStream the InputStream containing the file content
     * @return the MD5 checksum as a hexadecimal string
     * @throws IOException if an I/O error occurs while reading the InputStream
     */
    public String getMD5Sum(InputStream inputStream) throws IOException {
        BufferedInputStream bis = new BufferedInputStream(inputStream);
        return DocumentUtils.md5(bis);
    }

    /**
     * Checks if a document with a specific hash exists in a given spatial unit.
     *
     * @param spatialUnit the spatial unit in which to check for the document
     * @param hash        the hash of the document to check
     * @return true if the document exists in the spatial unit, false otherwise
     */
    public boolean existInSpatialUnitByHash(SpatialUnitDTO spatialUnit, String hash) {
        return documentRepository.existsByHashInSpatialUnit(spatialUnit.getId(), hash);
    }

    /**
     * Checks if a document with a specific hash exists in a given specimen.
     *
     * @param specimen the specimen in which to check for the document
     * @param hash        the hash of the document to check
     * @return true if the document exists in the spatial unit, false otherwise
     */
    public boolean existInSpecimenByHash(SpecimenDTO specimen, String hash) {
        return documentRepository.existsByHashInSpecimen(specimen.getId(), hash);
    }

    /**
     * Checks if a document with a specific hash exists in a given recording unit.
     *
     * @param recordingUnit the recording unit in which to check for the document
     * @param hash        the hash of the document to check
     * @return true if the document exists in the spatial unit, false otherwise
     */
    public boolean existInRecordingUnitByHash(RecordingUnitDTO recordingUnit, String hash) {
        return documentRepository.existsByHashInRecordingUnit(recordingUnit.getId(), hash);
    }

    /**
     * Checks if a document with a specific hash exists in a given action unit.
     *
     * @param actionUnit the action unit in which to check for the document
     * @param hash        the hash of the document to check
     * @return true if the document exists in the spatial unit, false otherwise
     */
    public boolean existInActionUnitByHash(ActionUnitDTO actionUnit, String hash) {
        return documentRepository.existsByHashInActionUnit(actionUnit.getId(), hash);
    }

}
