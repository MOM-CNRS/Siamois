package fr.siamois.domain.services.document;

import fr.siamois.domain.models.ArkEntity;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.InvalidFileSizeException;
import fr.siamois.domain.models.exceptions.InvalidFileTypeException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.services.ArkEntityService;
import fr.siamois.domain.services.document.compressor.FileCompressor;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.DocumentRepository;
import fr.siamois.infrastructure.files.DocumentStorage;
import fr.siamois.utils.CodeUtils;
import fr.siamois.utils.DocumentUtils;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.InvalidFileNameException;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import java.io.*;
import java.util.Collection;
import java.util.List;
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
    private final ConversionService conversionService;

    private static final int MAX_GENERATIONS = 100;
    private final DocumentStorage documentStorage;
    private final Collection<FileCompressor> fileCompressors;



    @Override
    public List<Document> findWithoutArk(Institution institution) {
        return documentRepository.findAllByArkIsNullAndCreatedByInstitution(institution);
    }

    public ArkEntity save(ArkEntity toSave) {
        return documentRepository.save((Document) toSave);
    }

    @Override
    public AbstractEntityDTO save(AbstractEntityDTO toSave) {
        return null; // TODO IMPLEMENT
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
            document.setCreatedBy(conversionService.convert(userInfo.getUser(), Person.class));
            document.setCreatedByInstitution(conversionService.convert(userInfo.getInstitution(), Institution.class));
            document.setUrl(String.format("%s/content/%s", contextPath, document.contentFileName()));

            documentStorage.save(userInfo, document, bufferedInputStream);
        }

        log.trace("Finished upload document {} to {}", document.getFileName(), userInfo.getInstitution().getId());

        return documentRepository.save(document);
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
     * Finds documents associated with a specific spatial unit.
     *
     * @param spatialUnit the spatial unit for which documents are to be found
     * @return a list of documents associated with the spatial unit
     */
    public List<Document> findForSpatialUnit(SpatialUnitDTO spatialUnit) {
        return documentRepository.findDocumentsBySpatialUnit(spatialUnit.getId());
    }

    /**
     * Finds documents associated with a specific action unit.
     *
     * @param actionUnit the action unit for which documents are to be found
     * @return a list of documents associated with the action unit
     */
    public List<Document> findForActionUnit(ActionUnitDTO actionUnit) {
        return documentRepository.findDocumentsByActionUnit(actionUnit.getId());
    }

    /**
     * Finds documents associated with a specific recording unit.
     *
     * @param recordingUnit the recording unit for which documents are to be found
     * @return a list of documents associated with the recording unit
     */
    public List<Document> findForRecordingUnit(RecordingUnitDTO recordingUnit) {
        return documentRepository.findDocumentsByRecordingUnit(recordingUnit.getId());
    }

    /**
     * Finds documents associated with a specific specimen.
     *
     * @param specimen the specimen for which documents are to be found
     * @return a list of documents associated with the specimen
     */
    public List<Document> findForSpecimen(SpecimenDTO specimen) {
        return documentRepository.findDocumentsBySpecimen(specimen.getId());
    }

    /**
     * Adds a document to a spatial unit.
     *
     * @param document    the document to be added
     * @param spatialUnit the spatial unit to which the document is to be added
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
     * Adds a document to a action unit.
     *
     * @param document    the document to be added
     * @param actionUnit the action unit to which the document is to be added
     */
    public void addToActionUnit(Document document, ActionUnitDTO actionUnit) {
        documentRepository.addDocumentToActionUnit(document.getId(), actionUnit.getId());
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
