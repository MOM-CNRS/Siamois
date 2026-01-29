package fr.siamois.ui.redirection;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.document.compressor.FileCompressor;
import fr.siamois.ui.bean.SessionSettingsBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.InputStream;
import java.util.Optional;

@Slf4j
@Controller
@Scope(value = "session")
public class DocumentController {


    private final SessionSettingsBean sessionSettingsBean;
    private final DocumentService documentService;

    public DocumentController(SessionSettingsBean sessionSettingsBean,
                              DocumentService documentService) {
        this.sessionSettingsBean = sessionSettingsBean;
        this.documentService = documentService;
    }

    @GetMapping("/content/{fileCodeName}")
    public ResponseEntity<Resource> download(
            @PathVariable String fileCodeName
    ) {
        UserInfo userInfo = sessionSettingsBean.getUserInfo();
        String[] parts = fileCodeName.split("\\.");
        if (parts.length != 2) {
            return ResponseEntity.badRequest().build();
        }

        String resourceCode = parts[0];
        Optional<Document> opt = documentService.findByFileCode(resourceCode);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Document document = opt.get();
        if (!document.getCreatedByInstitution().getId().equals(userInfo.getInstitution().getId())) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        Optional<InputStream> optInputStream = documentService.findInputStreamOfDocument(document);

        if (optInputStream.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        InputStream fileStream = optInputStream.get();
        ContentDisposition contentDisposition = ContentDisposition.builder("inline")
                .filename(document.contentFileName())
                .build();

        String mimeStringType;
        try {
            FileCompressor compressor = documentService.findCompressorOf(document);
            mimeStringType = compressor.encodingTypes();
        } catch (UnsupportedOperationException e) {
            mimeStringType = document.getMimeType();
        }

        return  ResponseEntity
                .ok()
                .contentType(MediaType.parseMediaType(document.getMimeType()))
                .header(HttpHeaders.CONTENT_ENCODING, mimeStringType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(new InputStreamResource(fileStream));

    }

}
