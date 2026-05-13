package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.services.document.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.util.Set;

/**
 * Téléchargement, suppression et accès métadonnée OpenAPI des {@link Document} : contrôle du périmètre institutions (JWT).
 */
@Service
@RequiredArgsConstructor
public class DocumentContentOpenApiService {

    private final DocumentService documentService;

    public record DocumentFilePayload(InputStream inputStream, MediaType mediaType, String fileName) {
    }

    /**
     * Document existant dont l'institution de création est dans le périmètre JWT (même règle que téléchargement / suppression).
     */
    @Transactional(readOnly = true)
    public Document requireAccessibleDocument(long documentId, Set<Long> accessibleInstitutionIds) {
        if (accessibleInstitutionIds == null || accessibleInstitutionIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
        Document doc = documentService.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        Long instId = doc.getCreatedByInstitution() == null ? null : doc.getCreatedByInstitution().getId();
        if (instId == null || !accessibleInstitutionIds.contains(instId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
        return doc;
    }

    @Transactional(readOnly = true)
    public DocumentFilePayload requireDownloadableContent(long documentId, Set<Long> accessibleInstitutionIds) {
        Document doc = requireAccessibleDocument(documentId, accessibleInstitutionIds);
        InputStream stream = documentService.findInputStreamOfDocument(doc)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
        MediaType mediaType = resolveMediaType(doc.getMimeType());
        return new DocumentFilePayload(stream, mediaType, doc.contentFileName());
    }

    private static MediaType resolveMediaType(String rawMime) {
        if (!StringUtils.hasText(rawMime)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(rawMime);
        } catch (RuntimeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    /**
     * Supprime le document et son fichier si l'institution de création est dans le périmètre JWT.
     */
    @Transactional
    public void deleteAccessibleDocument(long documentId, Set<Long> accessibleInstitutionIds) {
        Document doc = requireAccessibleDocument(documentId, accessibleInstitutionIds);
        documentService.deleteDocument(doc);
    }
}
