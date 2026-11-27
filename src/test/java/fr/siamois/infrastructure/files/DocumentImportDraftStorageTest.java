package fr.siamois.infrastructure.files;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

class DocumentImportDraftStorageTest {

    @TempDir
    Path tempDir;

    private DocumentImportDraftStorage storage;

    @BeforeEach
    void setUp() {
        storage = new DocumentImportDraftStorage();
        ReflectionTestUtils.setField(storage, "documentsPath", tempDir.toString());
    }

    @Test
    void deleteDraftTree_removesEmptyInstitutionAndDraftsRoot() throws Exception {
        String stored = storage.save(10L, 3L, "a.pdf", new ByteArrayInputStream(new byte[]{1, 2, 3}));
        Path draftDir = storage.draftDir(10L, 3L);
        Path institutionDir = draftDir.getParent();
        Path draftsRoot = storage.draftsRoot();

        assertThat(Files.isRegularFile(draftDir.resolve(stored))).isTrue();
        assertThat(Files.isDirectory(institutionDir)).isTrue();
        assertThat(Files.isDirectory(draftsRoot)).isTrue();

        storage.deleteDraftTree(10L, 3L);

        assertThat(Files.exists(draftDir)).isFalse();
        assertThat(Files.exists(institutionDir)).isFalse();
        assertThat(Files.exists(draftsRoot)).isFalse();
    }

    @Test
    void deleteItem_lastFile_prunesEmptyFolders() throws Exception {
        String stored = storage.save(10L, 3L, "only.pdf", new ByteArrayInputStream(new byte[]{9}));
        Path draftDir = storage.draftDir(10L, 3L);

        storage.deleteItem(10L, 3L, stored);

        assertThat(Files.exists(draftDir)).isFalse();
        assertThat(Files.exists(draftDir.getParent())).isFalse();
        assertThat(Files.exists(storage.draftsRoot())).isFalse();
    }

    @Test
    void deleteDraftTree_keepsSiblingInstitutionDraft() throws Exception {
        storage.save(10L, 3L, "a.pdf", new ByteArrayInputStream(new byte[]{1}));
        storage.save(10L, 4L, "b.pdf", new ByteArrayInputStream(new byte[]{2}));

        storage.deleteDraftTree(10L, 3L);

        assertThat(Files.exists(storage.draftDir(10L, 3L))).isFalse();
        assertThat(Files.isDirectory(storage.draftDir(10L, 4L))).isTrue();
        assertThat(Files.isDirectory(storage.draftsRoot().resolve("10"))).isTrue();
        assertThat(Files.isDirectory(storage.draftsRoot())).isTrue();
    }

    @Test
    void openAndReadAllBytes_returnSavedContent() throws Exception {
        String stored = storage.save(10L, 3L, "photo.jpg", new ByteArrayInputStream(new byte[]{7, 8, 9}));

        try (InputStream in = storage.open(10L, 3L, stored)) {
            assertThat(in.readAllBytes()).containsExactly(7, 8, 9);
        }
        assertThat(storage.readAllBytes(10L, 3L, stored)).containsExactly(7, 8, 9);
        assertThat(stored).endsWith(".jpg");
    }

    @Test
    void open_missingFile_throws() {
        assertThatThrownBy(() -> storage.open(10L, 3L, "missing.pdf"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Draft file missing");
    }

    @Test
    void deleteItem_blankOrNullName_isNoOp() throws Exception {
        String stored = storage.save(10L, 3L, "keep.pdf", new ByteArrayInputStream(new byte[]{1}));

        storage.deleteItem(10L, 3L, null);
        storage.deleteItem(10L, 3L, "  ");

        assertThat(Files.isRegularFile(storage.draftDir(10L, 3L).resolve(stored))).isTrue();
    }

    @Test
    void deleteDraftTree_missingDirectory_isNoOp() {
        storage.deleteDraftTree(99L, 1L);
        assertThat(Files.exists(storage.draftDir(99L, 1L))).isFalse();
    }

    @Test
    void pruneEmptyAncestors_nullOrOutsideRoot_isNoOp() throws Exception {
        storage.pruneEmptyAncestors(null);
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(outside);
        storage.pruneEmptyAncestors(outside);
        assertThat(Files.isDirectory(outside)).isTrue();
    }

    @Test
    void save_extensionEdgeCases() throws Exception {
        assertThat(storage.save(1L, 1L, null, new ByteArrayInputStream(new byte[]{1})))
                .doesNotContain(".");
        assertThat(storage.save(1L, 1L, "noext", new ByteArrayInputStream(new byte[]{1})))
                .doesNotContain(".");
        assertThat(storage.save(1L, 1L, "ends.", new ByteArrayInputStream(new byte[]{1})))
                .doesNotContain("ends");
        assertThat(storage.save(1L, 1L, "bad." + "x".repeat(40), new ByteArrayInputStream(new byte[]{1})))
                .doesNotContain("xxxxxxxxxx");
        assertThat(storage.save(1L, 1L, "bad./x", new ByteArrayInputStream(new byte[]{1})))
                .doesNotContain("/x");
        assertThat(storage.save(1L, 1L, "ok.PDF", new ByteArrayInputStream(new byte[]{1})))
                .endsWith(".PDF");
    }

    @Test
    void deleteItem_deleteFailure_isLoggedAndIgnored() throws Exception {
        String stored = storage.save(10L, 3L, "a.pdf", new ByteArrayInputStream(new byte[]{1}));
        Path path = storage.draftDir(10L, 3L).resolve(stored);

        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.deleteIfExists(any(Path.class))).thenThrow(new IOException("locked"));
            storage.deleteItem(10L, 3L, stored);
        }

        assertThat(Files.isRegularFile(path)).isTrue();
    }

    @Test
    void deleteDraftTree_walkFailure_isLoggedAndIgnored() throws Exception {
        storage.save(10L, 3L, "a.pdf", new ByteArrayInputStream(new byte[]{1}));
        Path dir = storage.draftDir(10L, 3L);

        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.walk(any(Path.class))).thenThrow(new IOException("walk failed"));
            storage.deleteDraftTree(10L, 3L);
        }

        assertThat(Files.isDirectory(dir)).isTrue();
    }

    @Test
    void deleteDraftTree_fileDeleteFailure_continues() throws Exception {
        String stored = storage.save(10L, 3L, "a.pdf", new ByteArrayInputStream(new byte[]{1}));
        Path path = storage.draftDir(10L, 3L).resolve(stored);
        Path dir = storage.draftDir(10L, 3L);

        assertThat(Files.isRegularFile(path)).isTrue();

        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.deleteIfExists(any(Path.class))).thenThrow(new IOException("busy"));
            assertThatCode(() -> storage.deleteDraftTree(10L, 3L)).doesNotThrowAnyException();
        }

        assertThat(Files.isRegularFile(path)).isTrue();
        assertThat(Files.isDirectory(dir)).isTrue();
    }

    @Test
    void pruneEmptyAncestors_deleteFailure_stopsWalking() throws Exception {
        Path empty = storage.draftDir(10L, 3L);
        Files.createDirectories(empty);

        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.deleteIfExists(any(Path.class))).thenThrow(new IOException("denied"));
            storage.pruneEmptyAncestors(empty);
        }

        assertThat(Files.isDirectory(empty)).isTrue();
    }

    @Test
    void pruneEmptyAncestors_directoryStreamFailure_treatsAsNonEmpty() throws Exception {
        Path empty = storage.draftDir(10L, 3L);
        Files.createDirectories(empty);

        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.newDirectoryStream(any(Path.class))).thenThrow(new IOException("nope"));
            storage.pruneEmptyAncestors(empty);
        }

        assertThat(Files.isDirectory(empty)).isTrue();
    }
}
