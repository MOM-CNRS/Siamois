package fr.siamois.infrastructure.files;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;


@Slf4j
@Service
public class DocumentImportDraftStorage {

    public static final String DRAFTS_DIR = "_import_drafts";

    @Value("${siamois.documents.folder-path}")
    private String documentsPath;

    public Path draftDir(long institutionId, long draftId) {
        return Paths.get(documentsPath, DRAFTS_DIR, Long.toString(institutionId), Long.toString(draftId));
    }

    Path draftsRoot() {
        return Paths.get(documentsPath, DRAFTS_DIR).normalize();
    }

    /**
     * Writes the stream to a new file under the draft directory. Returns the stored file name only.
     */
    public String save(long institutionId, long draftId, String originalFileName, InputStream content) throws IOException {
        Path dir = draftDir(institutionId, draftId);
        Files.createDirectories(dir);
        String stored = UUID.randomUUID() + extensionOf(originalFileName);
        Path target = dir.resolve(stored);
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        log.info("Draft file saved: {}", target);
        return stored;
    }

    public InputStream open(long institutionId, long draftId, String storedFileName) throws IOException {
        Path path = draftDir(institutionId, draftId).resolve(storedFileName);
        if (!Files.isRegularFile(path)) {
            throw new IOException("Draft file missing: " + path);
        }
        return Files.newInputStream(path);
    }

    public byte[] readAllBytes(long institutionId, long draftId, String storedFileName) throws IOException {
        try (InputStream in = open(institutionId, draftId, storedFileName)) {
            return in.readAllBytes();
        }
    }

    public void deleteItem(long institutionId, long draftId, String storedFileName) {
        if (storedFileName == null || storedFileName.isBlank()) {
            return;
        }
        Path path = draftDir(institutionId, draftId).resolve(storedFileName);
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Unable to delete draft file {}: {}", path, e.getMessage());
        }
        pruneEmptyAncestors(draftDir(institutionId, draftId));
    }

    /** Deletes the whole draft directory (all item files). */
    public void deleteDraftTree(long institutionId, long draftId) {
        Path dir = draftDir(institutionId, draftId);
        if (Files.isDirectory(dir)) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        log.warn("Unable to delete {}: {}", p, e.getMessage());
                    }
                });
            } catch (IOException e) {
                log.warn("Unable to walk draft dir {}: {}", dir, e.getMessage());
            }
        }
        pruneEmptyAncestors(dir.getParent());
    }

    /**
     * Removes {@code start} and its parents while they stay empty, stopping at (and including)
     * {@code _import_drafts} when that root itself is empty. Never walks above the drafts root.
     */
    void pruneEmptyAncestors(Path start) {
        if (start == null) {
            return;
        }
        Path root = draftsRoot();
        Path current = start.normalize();
        if (!current.startsWith(root)) {
            return;
        }
        while (current != null && current.startsWith(root)) {
            if (!Files.isDirectory(current) || !isEmptyDirectory(current)) {
                break;
            }
            try {
                Files.deleteIfExists(current);
                log.debug("Removed empty draft directory {}", current);
            } catch (IOException e) {
                log.warn("Unable to remove empty draft directory {}: {}", current, e.getMessage());
                break;
            }
            if (current.equals(root)) {
                break;
            }
            current = current.getParent();
        }
    }

    private static boolean isEmptyDirectory(Path dir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            return !stream.iterator().hasNext();
        } catch (IOException e) {
            return false;
        }
    }

    private static String extensionOf(String originalFileName) {
        if (originalFileName == null) {
            return "";
        }
        int i = originalFileName.lastIndexOf('.');
        if (i < 0 || i == originalFileName.length() - 1) {
            return "";
        }
        String ext = originalFileName.substring(i);
        if (ext.length() > 32 || ext.contains("/") || ext.contains("\\")) {
            return "";
        }
        return ext;
    }
}
