package fr.siamois.utils;

import org.junit.jupiter.api.Test;
import org.springframework.util.MimeType;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentUtilsTest {

    @Test
    void allowedTypesRegex_acceptsJpgAndJpegForImageJpeg() {
        List<MimeType> types = List.of(MimeType.valueOf("image/jpeg"), MimeType.valueOf("application/pdf"));

        assertThat(DocumentUtils.allowedTypesRegex(types))
                .isEqualTo("/(\\.|\\/)(jpg|jpeg|pdf)$/");
        assertThat(DocumentUtils.allowedExtensionsStringList(types))
                .isEqualTo(".jpg,.jpeg,.pdf");
    }

    @Test
    void allowedTypesRegex_wildcardAllowsAnyFile() {
        List<MimeType> types = List.of(MimeType.valueOf("*/*"));

        assertThat(DocumentUtils.allowedTypesRegex(types)).isEqualTo("/.*/");
        assertThat(DocumentUtils.allowedExtensionsStringList(types)).isEmpty();
    }

    @Test
    void allowedTypesRegex_nullOrEmptyList_allowsAnyFile() {
        assertThat(DocumentUtils.allowedTypesRegex(null)).isEqualTo("/.*/");
        assertThat(DocumentUtils.allowedTypesRegex(List.of())).isEqualTo("/.*/");
        assertThat(DocumentUtils.allowedExtensionsStringList(null)).isEmpty();
        assertThat(DocumentUtils.allowedExtensionsStringList(List.of())).isEmpty();
    }

    @Test
    void allowedTypesRegex_coversCommonImageTypesAndDefaultSubtype() {
        List<MimeType> types = List.of(
                MimeType.valueOf("image/png"),
                MimeType.valueOf("image/gif"),
                MimeType.valueOf("image/webp"),
                MimeType.valueOf("image/tiff"),
                MimeType.valueOf("image/svg+xml"),
                MimeType.valueOf("application/vnd.custom+xml"),
                MimeType.valueOf("text/plain")
        );

        assertThat(DocumentUtils.allowedTypesRegex(types))
                .isEqualTo("/(\\.|\\/)(png|gif|webp|tif|tiff|svg|vnd.custom|plain)$/");
        assertThat(DocumentUtils.allowedExtensionsStringList(types))
                .isEqualTo(".png,.gif,.webp,.tif,.tiff,.svg,.vnd.custom,.plain");
    }

    @Test
    void fileExtensions_dedupesRepeatedMimeTypes() {
        List<MimeType> types = List.of(
                MimeType.valueOf("image/jpeg"),
                MimeType.valueOf("image/jpeg")
        );
        assertThat(DocumentUtils.fileExtensions(types)).containsExactly("jpg", "jpeg");
    }

    @Test
    void byteParser_parsesKnownUnitsAndRejectsInvalid() {
        assertThat(DocumentUtils.byteParser("10KB")).isEqualTo(10L * 1024);
        assertThat(DocumentUtils.byteParser("2MB")).isEqualTo(2L * 1024 * 1024);
        assertThat(DocumentUtils.byteParser("1GB")).isEqualTo(1024L * 1024 * 1024);
        assertThat(DocumentUtils.byteParser("1TB")).isEqualTo(1024L * 1024 * 1024 * 1024);
        assertThatThrownBy(() -> DocumentUtils.byteParser("nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid size format");
    }

    @Test
    void md5_fromBytesAndStream() throws Exception {
        byte[] content = "hello".getBytes();
        String expected = DocumentUtils.md5(content);

        try (BufferedInputStream in = new BufferedInputStream(new ByteArrayInputStream(content))) {
            assertThat(DocumentUtils.md5(in)).isEqualTo(expected);
            assertThat(in.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void md5_streamWithoutMark_throws() {
        FilterInputStream noMark = new FilterInputStream(new ByteArrayInputStream(new byte[]{1})) {
            @Override
            public boolean markSupported() {
                return false;
            }
        };
        assertThatThrownBy(() -> DocumentUtils.md5(new BufferedInputStream(noMark) {
            @Override
            public boolean markSupported() {
                return false;
            }
        })).isInstanceOf(IOException.class)
                .hasMessageContaining("Mark/reset not supported");
    }
}
