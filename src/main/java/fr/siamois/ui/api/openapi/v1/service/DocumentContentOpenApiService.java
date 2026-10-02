package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.document.compressor.FileCompressor;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.InstitutionDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import fr.siamois.domain.models.exceptions.InvalidFileSizeException;
import fr.siamois.domain.models.exceptions.InvalidFileTypeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * Téléchargement, suppression et accès métadonnée OpenAPI des {@link Document} : contrôle du périmètre institutions (JWT).
 */
@Service
@RequiredArgsConstructor
public class DocumentContentOpenApiService {

    private final DocumentService documentService;
    private final ProfilePermissionService profilePermissionService;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    public record DocumentFilePayload(InputStream inputStream, MediaType mediaType, String fileName) {
    }

    /**
     * Document existant dont l'institution de création est dans le périmètre JWT (même règle que téléchargement / suppression).
     */
    @Transactional(readOnly = true)
    public Document requireAccessibleDocument(long documentId, Set<Long> accessibleInstitutionIds) {
        return resolveAccessibleDocument(documentId, accessibleInstitutionIds);
    }

    private Document resolveAccessibleDocument(long documentId, Set<Long> accessibleInstitutionIds) {
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
        Document doc = resolveAccessibleDocument(documentId, accessibleInstitutionIds);
        if (!doc.hasFile()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
        }
        InputStream stream = documentService.findInputStreamOfDocument(doc)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
        // Files are stored compressed on disk (see FileCompressor); a REST client expects the
        // original bytes back, not the storage-level encoding, so decompress before serving.
        FileCompressor compressor = documentService.findCompressorOf(doc);
        try {
            stream = compressor.decompress(stream);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read file", e);
        }
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
     * Document que l'appelant peut modifier : dans le périmètre JWT <em>et</em> droit d'édition des documents
     * sur son projet (ou sur l'organisation / l'instance). Un document encore sans projet n'est modifiable que
     * par un droit d'organisation ou d'instance.
     *
     * @throws ResponseStatusException 404 hors périmètre, 403 sans droit d'édition
     */
    @Transactional(readOnly = true)
    public Document requireWritableDocument(long documentId, ProjectApiCaller caller) {
        return resolveWritableDocument(documentId, caller);
    }

    private Document resolveWritableDocument(long documentId, ProjectApiCaller caller) {
        Document doc = resolveAccessibleDocument(documentId, caller.accessibleInstitutionIds());
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(doc.getCreatedByInstitution().getId());
        UserInfo info = new UserInfo(institution, caller.person(), null);
        Long projectId = doc.getActionUnit() == null ? null : doc.getActionUnit().getId();
        if (!profilePermissionService.hasProjectPermission(info, projectId,
                PermissionConstants.INSTANCE_EDIT_DOCUMENTS,
                PermissionConstants.ORGANIZATION_EDIT_DOCUMENTS,
                PermissionConstants.PROJECT_EDIT_DOCUMENTS)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification de document non autorisée");
        }
        return doc;
    }

    /**
     * Remplace (ou pose) le fichier du document : même contrôle de périmètre et de droit d'édition que la
     * suppression ; type et taille sont vérifiés comme à la création.
     */
    @Transactional
    public Document replaceFile(long documentId, MultipartFile file, ProjectApiCaller caller) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file est obligatoire");
        }
        Document doc = resolveWritableDocument(documentId, caller);
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(doc.getCreatedByInstitution().getId());
        UserInfo info = new UserInfo(institution, caller.person(), null);
        String mimeType = StringUtils.hasText(file.getContentType()) ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String name = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "document";
        try {
            return documentService.replaceFile(info, doc, name, mimeType, file.getSize(), file.getInputStream(), contextPath);
        } catch (InvalidFileTypeException | InvalidFileSizeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur lecture fichier", e);
        }
    }

    /** Retire le fichier du document (le document et son URL externe demeurent). */
    @Transactional
    public Document removeFile(long documentId, ProjectApiCaller caller) {
        return documentService.removeFile(resolveWritableDocument(documentId, caller));
    }

    /**
     * Supprime le document et son fichier : l'institution de création doit être dans le périmètre JWT et
     * l'appelant doit pouvoir éditer les documents du projet.
     */
    @Transactional
    public void deleteAccessibleDocument(long documentId, ProjectApiCaller caller) {
        Document doc = resolveWritableDocument(documentId, caller);
        documentService.deleteDocument(doc);
    }
}
