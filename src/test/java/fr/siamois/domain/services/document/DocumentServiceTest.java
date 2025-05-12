package fr.siamois.domain.services.document;

import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.document.DocumentParent;
import fr.siamois.domain.models.exceptions.InvalidFileSizeException;
import fr.siamois.domain.models.exceptions.InvalidFileTypeException;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.document.compressor.FileCompressor;
import fr.siamois.domain.utils.DocumentUtils;
import fr.siamois.infrastructure.database.repositories.DocumentRepository;
import fr.siamois.infrastructure.files.DocumentStorage;
import org.apache.tomcat.util.http.fileupload.InvalidFileNameException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.MimeType;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentStorage documentStorage;

    @Mock
    private FileCompressor fileCompressor;

    @InjectMocks
    private DocumentService documentService;

    private final List<MimeType> mimeTypes = List.of(MimeType.valueOf("application/pdf"), MimeType.valueOf("image/png"));

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(documentRepository, documentStorage, List.of(fileCompressor));
    }

    @Test
    void findWithoutArk() {
        Institution institution = new Institution();
        institution.setId(1L);
        Document document = new Document();

        when(documentRepository.findAllByArkIsNullAndCreatedByInstitution(institution))
                .thenReturn(Collections.singletonList(document));

        List<Document> result = documentService.findWithoutArk(institution);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(documentRepository, times(1))
                .findAllByArkIsNullAndCreatedByInstitution(institution);
    }

    @Test
    void save() {
        Document document = new Document();

        when(documentRepository.save(document)).thenReturn(document);

        Document result = (Document) documentService.save(document);

        assertNotNull(result);
        assertEquals(document, result);
        verify(documentRepository, times(1)).save(document);
    }

    @Test
    void supportedMimeTypes() {
        when(documentStorage.supportedMimeTypes()).thenReturn(mimeTypes);

        List<MimeType> result = documentService.supportedMimeTypes();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(MimeType.valueOf("application/pdf"), result.get(0));
        assertEquals(MimeType.valueOf("image/png"), result.get(1));
    }

    @Test
    void saveFile() throws Exception {
        UserInfo userInfo = new UserInfo(new Institution(), new Person(), "fr");
        userInfo.getInstitution().setId(1L);
        userInfo.getInstitution().setIdentifier("fr");
        Document document = new Document();
        document.setMimeType("application/pdf"); // Set MIME type
        document.setFileName("test.pdf"); // Set file name
        document.setSize(1024L); // Set file size
        InputStream inputStream = new ByteArrayInputStream("test data".getBytes());

        when(documentRepository.save(document)).thenReturn(document);
        when(documentStorage.supportedMimeTypes()).thenReturn(mimeTypes);
        when(documentStorage.getMaxUploadSize()).thenReturn("10MB");

        Document result = documentService.saveFile(userInfo, document, inputStream, "/context");

        assertNotNull(result);
        assertEquals(document, result);
        verify(documentRepository, times(1)).save(document);
    }

    @Test
    void findFile() {
        Document document = new Document();
        document.setId(1L);
        document.setFileName("test.pdf");
        document.setMimeType("application/pdf");
        document.setSize(1024L);
        File file = new File("");

        when(documentStorage.find(document)).thenReturn(Optional.of(file));

        Optional<File> result = documentService.findFile(document);

        assertTrue(result.isPresent());
        assertEquals(file, result.get());
        verify(documentStorage, times(1)).find(document);
    }

    @Test
    void findByFileCode() {
        Document document = new Document();
        when(documentRepository.findByFileCode("code")).thenReturn(Optional.of(document));

        Optional<Document> result = documentService.findByFileCode("code");

        assertTrue(result.isPresent());
        assertEquals(document, result.get());
    }

    @Test
    void findForSpatialUnit() {
        SpatialUnit spatialUnit = new SpatialUnit();
        spatialUnit.setId(1L);
        Document document = new Document();

        when(documentRepository.findDocumentsBySpatialUnit(spatialUnit.getId()))
                .thenReturn(Collections.singletonList(document));

        List<Document> result = documentService.findForSpatialUnit(spatialUnit);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(documentRepository, times(1))
                .findDocumentsBySpatialUnit(spatialUnit.getId());
    }

    @Test
    void addToSpatialUnit() {
        Document document = new Document();
        SpatialUnit spatialUnit = new SpatialUnit();

        documentService.addToSpatialUnit(document, spatialUnit);

        verify(documentRepository, times(1))
                .addDocumentToSpatialUnit(document.getId(), spatialUnit.getId());
    }

    @Test
    void findInputStreamOfDocument() throws IOException {
        Document document = new Document();
        byte[] data = "test data".getBytes();
        when(documentStorage.findStreamOf(document)).thenReturn(Optional.of(data));

        Optional<InputStream> result = documentService.findInputStreamOfDocument(document);

        assertTrue(result.isPresent());
        assertArrayEquals(data, result.get().readAllBytes());
        verify(documentStorage, times(1)).findStreamOf(document);
    }

    @Test
    void maxFileSize() {
        when(documentStorage.getMaxUploadSize()).thenReturn("10MB");

        long result = documentService.maxFileSize();

        assertEquals(10 * 1024 * 1024, result);
    }

    @Test
    void findCompressorOf() {
        Document document = new Document();
        document.setMimeType("application/json");
        when(fileCompressor.isMatchingCompressor(MimeType.valueOf("application/json"))).thenReturn(true);

        FileCompressor result = documentService.findCompressorOf(document);

        assertEquals(fileCompressor, result);
    }

    @Test
    void getMD5Sum() throws IOException {
        InputStream inputStream = new ByteArrayInputStream("test data".getBytes());
        String expectedMd5 = "eb733a00c0c9d336e65691a37ab54293"; // MD5 of "test data"

        String result = documentService.getMD5Sum(inputStream);

        assertEquals(expectedMd5, result);
    }

    @Test
    void existInSpatialUnitByHash() {
        SpatialUnit spatialUnit = new SpatialUnit();
        spatialUnit.setId(1L);
        String hash = "testhash";
        when(documentRepository.existsByHashInSpatialUnit(spatialUnit.getId(), hash)).thenReturn(true);

        boolean result = documentService.existInSpatialUnitByHash(spatialUnit, hash);

        assertTrue(result);
        verify(documentRepository, times(1)).existsByHashInSpatialUnit(spatialUnit.getId(), hash);
    }

    @Test
    void checkFileDataShouldThrowInvalidFileTypeException() {
        Document document = new Document();
        document.setMimeType("application/unsupported");

        when(documentStorage.supportedMimeTypes()).thenReturn(List.of());

        assertThrows(InvalidFileTypeException.class, () -> documentService.checkFileData(document));
    }

    @Test
    void checkFileDataShouldThrowInvalidFileNameException() {
        Document document = new Document();
        document.setFileName("a".repeat(DocumentParent.MAX_FILE_NAME_LENGTH + 1));
        document.setMimeType("application/pdf");

        MimeType mimeType = MimeType.valueOf("application/pdf");

        when(documentStorage.supportedMimeTypes()).thenReturn(List.of(mimeType));

        assertThrows(InvalidFileNameException.class, () -> documentService.checkFileData(document));
    }

    @Test
    void checkFileDataShouldThrowInvalidFileSizeException() {
        Document document = new Document();
        document.setMimeType("application/pdf");
        document.setFileName("Regular name");

        when(documentStorage.getMaxUploadSize()).thenReturn("10KB");

        document.setSize(DocumentUtils.byteParser("10KB") + 1);

        MimeType mimeType = MimeType.valueOf("application/pdf");
        when(documentStorage.supportedMimeTypes()).thenReturn(List.of(mimeType));

        assertThrows(InvalidFileSizeException.class, () -> documentService.checkFileData(document));
    }

    @Test
    void findInputStreamOfDocumentShouldReturnOptional() {
        Document document = new Document();

        Optional<InputStream> result = documentService.findInputStreamOfDocument(document);

        assertTrue(result.isEmpty());
    }

    @Test
    void findCompressorOf_whenNoCompressor() {
        Document document = new Document();
        document.setMimeType("application/pdf");

        when(fileCompressor.isMatchingCompressor(any(MimeType.class))).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> documentService.findCompressorOf(document));
    }

}