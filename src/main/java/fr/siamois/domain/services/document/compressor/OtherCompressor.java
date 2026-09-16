package fr.siamois.domain.services.document.compressor;

import fr.siamois.domain.models.document.Document;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Service
@Order
public class OtherCompressor implements FileCompressor {

    @Override
    public boolean isMatchingCompressor(MimeType mimeType) {
        return true;
    }

    /**
     * Use GZIP compression for any other file
     * @param inputStream The file input stream
     * @return The compressed byte array
     */
    @Override
    public byte[] compress(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                gzipOutputStream.write(buffer, 0, len);
            }
        }
        return byteArrayOutputStream.toByteArray();
    }

    @Override
    public String encodingTypes() {
        return "gzip";
    }

    @Override
    public InputStream decompress(InputStream inputStream) throws IOException {
        return new GZIPInputStream(inputStream);
    }


    @Override
    public void updateStoredFilename(Document document) {
        document.setStoredFileName(document.getFileCode() + ".gzip");
    }

}
