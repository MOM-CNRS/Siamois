package fr.siamois.infrastructure.files;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.services.document.compressor.FileCompressor;
import fr.siamois.dto.entity.InstitutionDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentStorageTest {

    @TempDir
    Path root;

    private DocumentStorage storage;
    private FileCompressor compressor;
    private UserInfo userInfo;

    @BeforeEach
    void setUp() throws IOException {
        compressor = mock(FileCompressor.class);
        when(compressor.isMatchingCompressor(any())).thenReturn(true);
        when(compressor.compress(any())).thenReturn(new byte[]{1, 2, 3});

        storage = new DocumentStorage(List.of(compressor));
        ReflectionTestUtils.setField(storage, "documentsPath", root.toString());

        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(7L);
        userInfo = mock(UserInfo.class);
        when(userInfo.getInstitution()).thenReturn(institution);
    }

    private Document documentStoredAs(String storedName) {
        Document document = mock(Document.class, Mockito.RETURNS_DEEP_STUBS);
        when(document.getStoredFileName()).thenReturn(storedName);
        when(document.mimeTypeObject()).thenReturn(org.springframework.util.MimeType.valueOf("text/plain"));
        return document;
    }

    @Test
    void save_writesInsideInstitutionFolder() throws IOException {
        storage.save(userInfo, documentStoredAs("a.bin"), new ByteArrayInputStream(new byte[0]));

        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(root.resolve("7").resolve("a.bin")));
    }

    @Test
    void save_refusesTraversingName() {
        Document document = documentStoredAs("../../evil.bin");

        assertThrows(IOException.class, () -> storage.save(userInfo, document, new ByteArrayInputStream(new byte[0])));
        assertFalse(Files.exists(root.resolve("evil.bin")));
    }
}
