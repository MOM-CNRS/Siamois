package fr.siamois.utils;

import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.ui.bean.dialog.document.DocumentCreationBean;
import org.apache.commons.codec.digest.DigestUtils;
import org.primefaces.model.file.UploadedFile;
import org.springframework.util.MimeType;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for document-related operations.
 * Provides methods for parsing byte sizes, calculating MD5 checksums,
 * and preparing documents from uploaded files.
 */
public class DocumentUtils {

    private static final long BYTE = 1L;
    private static final long KB = BYTE << 10;
    private static final long MB = KB << 10;
    private static final long GB = MB << 10;
    private static final long TB = GB << 10;

    private DocumentUtils() {
    }

    /**
     * Parses a byte size string into a long value.
     *
     * @param sizeString the size string to parse, e.g., "10MB", "500KB", etc.
     * @return the size in bytes as a long value.
     */
    public static long byteParser(String sizeString) {
        Pattern pattern = Pattern.compile("(\\d+)([KMGTP]B)");
        Matcher matcher = pattern.matcher(sizeString.toUpperCase().trim());

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid size format: " + sizeString);
        }

        long size = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);

        return switch (unit) {
            case "KB" -> size * KB;
            case "MB" -> size * MB;
            case "GB" -> size * GB;
            case "TB" -> size * TB;
            default -> throw new IllegalArgumentException("Invalid unit format: " + unit);
        };
    }

    /**
     * Calculates the MD5 checksum of a file.
     *
     * @param fileInputStream the BufferedInputStream of the file to calculate the checksum for.
     * @return the MD5 checksum as a hexadecimal string.
     * @throws IOException if an I/O error occurs while reading the file.
     */
    public static String md5(BufferedInputStream fileInputStream) throws IOException {
        if (!fileInputStream.markSupported()) {
            throw new IOException("Mark/reset not supported");
        }

        fileInputStream.mark(Integer.MAX_VALUE);

        String sum = DigestUtils.md5Hex(fileInputStream);
        fileInputStream.reset();
        return sum;
    }

    /**
     * Generates a regex pattern for allowed MIME types.
     *
     * @param allowedTypes the list of allowed MIME types.
     * @return a regex string that matches the allowed MIME types.
     */
    public static String allowedTypesRegex(List<MimeType> allowedTypes) {
        List<String> extensions = new ArrayList<>();

        for (MimeType mimeType : allowedTypes) {
            if (mimeType.toString().equalsIgnoreCase("*/*"))
                return "*";

            String subtype = mimeType.getSubtype();
            extensions.add(subtype);
        }

        return String.format("/(\\.|\\/)(%s)$/", String.join("|", extensions));
    }

    /**
     * Generates a string representation of allowed file extensions from MIME types.
     *
     * @param allowedTypes the list of allowed MIME types.
     * @return a string of allowed file extensions, e.g., ".pdf,.jpg".
     */
    public static String allowedExtensionsStringList(List<MimeType> allowedTypes) {
        List<String> extensions = new ArrayList<>();
        for (MimeType type : allowedTypes) {
            if (type.toString().equalsIgnoreCase("*/*"))
                return "*";
            extensions.add("." + type.getSubtype());
        }
        return String.join(",", extensions);
    }

    /**
     * Prepares a Document object from an UploadedFile and DocumentCreationBean.
     *
     * @param conceptService the ConceptService to use for saving concepts.
     * @param uploadedFile   the UploadedFile containing the document data.
     * @param bean           the DocumentCreationBean containing document metadata.
     * @return a Document object populated with the data from the uploaded file and bean.
     */
    public static Document prepareDocumentFrom(ConceptService conceptService, UploadedFile uploadedFile, DocumentCreationBean bean) {
        Document document = new Document();
        document.setTitle(bean.getDocTitle());
        document.setNature(conceptService.saveOrGetConcept(bean.getDocNature().getConceptLabelToDisplay().getConcept()));
        document.setScale(conceptService.saveOrGetConcept(bean.getDocScale().getConceptLabelToDisplay().getConcept()));
        document.setFormat(conceptService.saveOrGetConcept(bean.getDocType().getConceptLabelToDisplay().getConcept()));
        document.setMimeType(uploadedFile.getContentType());
        document.setFileName(uploadedFile.getFileName());
        document.setSize(uploadedFile.getSize());
        document.setDescription(bean.getDocDescription());
        return document;
    }

}
