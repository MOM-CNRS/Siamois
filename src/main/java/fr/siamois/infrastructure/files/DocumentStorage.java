package fr.siamois.infrastructure.files;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.services.document.compressor.FileCompressor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service to manage document storage.
 * Handles saving, finding, and compressing documents.
 */
@Slf4j
@Service
public class DocumentStorage {

    @Value("${siamois.documents.allowed-types:*/*}")
    private String[] mimeTypes;

    @Getter
    @Value("${spring.servlet.multipart.max-file-size}")
    private String maxUploadSize;

    @Value("${siamois.documents.folder-path}")
    private String documentsPath;

    private final List<FileCompressor> compressors;

    public DocumentStorage(List<FileCompressor> compressors) {
        this.compressors = compressors;
    }

    /**
     * Returns the list of supported MIME types for document storage.
     *
     * @return List of supported MIME types.
     */
    public List<MimeType> supportedMimeTypes() {
        List<MimeType> types = new ArrayList<>();
        for (String mimeType : mimeTypes) {
            types.add(MimeType.valueOf(mimeType));
        }
        return types;
    }

    /**
     * Saves a document to the storage.
     *
     * @param userInfo User information for the document creator.
     * @param document Document object containing metadata and file information.
     * @param content  InputStream containing the document content.
     * @throws IOException If an I/O error occurs during saving the document.
     */
    public void save(UserInfo userInfo, Document document, InputStream content) throws IOException {
        byte[] bytes = compressAndSetStoredName(document, content);
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);

        Path path = Paths.get(
                documentsPath,
                userInfo.getInstitution().getId().toString(),
                document.getStoredFileName()
        );

        Files.createDirectories(path.getParent());

        File file = path.toFile();
        if (file.createNewFile()) {
            log.info("File created: {}", file.getAbsolutePath());
        }

        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = bais.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }

        content.close();

    }

    private byte[] compressAndSetStoredName(Document document, InputStream content) throws IOException {
        FileCompressor compressor = findCompressor(document.mimeTypeObject());
        compressor.updateStoredFilename(document);
        return compressor.compress(content);
    }

    private FileCompressor findCompressor(MimeType type) {
        for (FileCompressor compressor : compressors) {
            if (compressor.isMatchingCompressor(type)) {
                return compressor;
            }
        }
        throw new IllegalStateException("Unsupported mime type: " + type);
    }

    /**
     * Finds a document file in the storage based on the Document object.
     *
     * @param document Document object containing metadata to locate the file.
     * @return Optional containing the File if found, otherwise empty.
     */
    public Optional<File> find(Document document) {
        Path filePath = Paths.get(
                documentsPath,
                document.getCreatedByInstitution().getId().toString(),
                document.getStoredFileName()
        );

        File file = filePath.toFile();

        if (file.exists()) {
            return Optional.of(file);
        }

        return Optional.empty();
    }

    /**
     * Finds the byte stream of a file based on its Document object.
     *
     * @param document Document object containing metadata to locate the file.
     * @return Optional containing the byte array of the file if found, otherwise empty.
     */
    public Optional<byte[]> findStreamOf(Document document) {
        Optional<File> file = find(document);
        if (file.isEmpty())
            return Optional.empty();
        try (FileInputStream fileInputStream = new FileInputStream(file.get())) {
            return Optional.of(fileInputStream.readAllBytes());
        } catch (IOException e) {
            log.error("File not found: {}", file.get().getAbsolutePath());
        }
        return Optional.empty();
    }

    /**
     * Supprime le fichier physique associé au document s'il existe (même logique de chemin que {@link #find}).
     */
    public void deleteStoredFile(Document document) {
        find(document).ifPresent(file -> {
            if (!file.delete()) {
                log.warn("Could not delete file: {}", file.getAbsolutePath());
            }
        });
    }

}
