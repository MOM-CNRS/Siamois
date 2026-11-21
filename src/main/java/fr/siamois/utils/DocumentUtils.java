package fr.siamois.utils;

import org.apache.commons.codec.digest.DigestUtils;
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
     * Content fingerprint for document duplicate detection (stored in {@code md5_sum}).
     * Not used for passwords, tokens or cryptographic integrity — MD5 is kept for
     * compatibility with existing rows and {@link fr.siamois.domain.services.document.DocumentService}.
     *
     * @param content file bytes
     * @return lowercase hex MD5 fingerprint
     */
    public static String md5(byte[] content) {
        return DigestUtils.md5Hex(content);
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
        List<String> extensions = fileExtensions(allowedTypes);
        if (extensions == null || extensions.isEmpty()) {
            return "/.*/";
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
        List<String> extensions = fileExtensions(allowedTypes);
        if (extensions == null || extensions.isEmpty()) {
            return "";
        }
        return extensions.stream().map(ext -> "." + ext).reduce((a, b) -> a + "," + b).orElse("");
    }

    static List<String> fileExtensions(List<MimeType> allowedTypes) {
        if (allowedTypes == null || allowedTypes.isEmpty()) {
            return null;
        }
        List<String> extensions = new ArrayList<>();
        for (MimeType type : allowedTypes) {
            if (type.toString().equalsIgnoreCase("*/*")) {
                return null;
            }
            for (String ext : extensionsForMime(type)) {
                if (!extensions.contains(ext)) {
                    extensions.add(ext);
                }
            }
        }
        return extensions;
    }

    private static List<String> extensionsForMime(MimeType type) {
        String full = type.toString().toLowerCase();
        return switch (full) {
            case "image/jpeg" -> List.of("jpg", "jpeg");
            case "image/png" -> List.of("png");
            case "image/gif" -> List.of("gif");
            case "image/webp" -> List.of("webp");
            case "image/tiff" -> List.of("tif", "tiff");
            case "image/svg+xml" -> List.of("svg");
            case "application/pdf" -> List.of("pdf");
            default -> {
                String subtype = type.getSubtype();
                int plus = subtype.indexOf('+');
                yield List.of(plus >= 0 ? subtype.substring(0, plus) : subtype);
            }
        };
    }

}
