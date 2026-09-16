package fr.siamois.domain.services.document.compressor;

import fr.siamois.domain.models.document.Document;
import org.springframework.util.MimeType;

import java.io.IOException;
import java.io.InputStream;

/**
 * Interface for file compression services.
 * This interface defines methods for compressing files based on their MIME type,
 * updating stored filenames, and retrieving encoding types.
 */
public interface FileCompressor {

    /**
     * Checks if the compressor can handle the given MIME type.
     *
     * @param mimeType the MIME type to check
     * @return true if the compressor can handle the MIME type, false otherwise
     */
    boolean isMatchingCompressor(MimeType mimeType);

    /**
     * Compresses the content of the given InputStream.
     *
     * @param inputStream the InputStream containing the file content to compress
     * @return a byte array containing the compressed content
     * @throws IOException if an I/O error occurs during compression
     */
    byte[] compress(InputStream inputStream) throws IOException;

    /**
     * Returns the encoding types supported by this compressor.
     *
     * @return a string representing the encoding types, typically a MIME type
     */
    String encodingTypes();

    /**
     * Reverses {@link #compress(InputStream)}: turns a stored file's raw bytes back into
     * the original content. The default is a no-op passthrough for compressors that don't
     * actually transform the bytes (e.g. {@link BrowserDisplayableCompressor}).
     *
     * @param inputStream the stream of stored (possibly compressed) bytes
     * @return a stream of the original, uncompressed content
     */
    default InputStream decompress(InputStream inputStream) throws IOException {
        return inputStream;
    }

    /**
     * Updates the stored filename of the given document.
     *
     * @param document the document whose stored filename needs to be updated
     */
    void updateStoredFilename(Document document);
}
