package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.InvalidFileSizeException;
import fr.siamois.domain.models.exceptions.InvalidFileTypeException;
import fr.siamois.domain.models.settings.InstitutionSettings;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.authorization.PermissionService;
import fr.siamois.domain.services.ark.ArkService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectDocumentOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.resource.document.ProjectDocumentResource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;

/**
 * Création et mise à jour de documents via l'API OpenAPI (mobile).
 */
@Service
@RequiredArgsConstructor
public class DocumentWriteOpenApiService {

    private static final String API_CONTEXT_PATH = "/siamois";

    private final ProjectApiService projectApiService;
    private final RecordingUnitService recordingUnitService;
    private final DocumentContentOpenApiService documentContentOpenApiService;
    private final DocumentService documentService;
    private final ConceptService conceptService;
    private final InstitutionService institutionService;
    private final PermissionService permissionService;
    private final ArkService arkService;
    private final ProjectDocumentOpenApiMapper projectDocumentOpenApiMapper;

    @Transactional
    public ProjectDocumentResource createForProject(
            ProjectApiCaller caller,
            String projectIdOrKey,
            String title,
            String description,
            Long natureConceptId,
            Long scaleConceptId,
            Long formatConceptId,
            MultipartFile file,
            String lang) {

        AccessibleProjectForApi row = projectApiService.requireAccessibleProject(caller, projectIdOrKey);
        InstitutionDTO institution = row.actionUnit().getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans organisation");
        }

        UserInfo userInfo = new UserInfo(institution, caller.person(), lang);
        Document document = buildDocumentShell(
                title, description, natureConceptId, scaleConceptId, formatConceptId, institution, file);

        try (InputStream inputStream = file.getInputStream()) {
            Document saved = documentService.saveFile(userInfo, document, inputStream, API_CONTEXT_PATH);
            documentService.addToActionUnit(saved, row.actionUnit());
            return projectDocumentOpenApiMapper.toResource(saved);
        } catch (InvalidFileTypeException | InvalidFileSizeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur lecture fichier", e);
        }
    }

    @Transactional
    public ProjectDocumentResource createForRecordingUnit(
            ProjectApiCaller caller,
            String recordingUnitKey,
            String title,
            String description,
            Long natureConceptId,
            Long scaleConceptId,
            Long formatConceptId,
            MultipartFile file,
            String lang) {

        RecordingUnitDTO ru = recordingUnitService.findAccessibleRecordingUnitByKey(
                recordingUnitKey, caller.accessibleInstitutionIds(), null);
        InstitutionDTO institution = ru.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unité d'enregistrement sans organisation");
        }

        UserInfo userInfo = new UserInfo(institution, caller.person(), lang);
        if (!permissionService.hasWritePermission(userInfo, ru)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Création de document non autorisée sur cette UE");
        }

        Document document = buildDocumentShell(
                title, description, natureConceptId, scaleConceptId, formatConceptId, institution, file);

        try (InputStream inputStream = file.getInputStream()) {
            Document saved = documentService.saveFile(userInfo, document, inputStream, API_CONTEXT_PATH);
            documentService.addToRecordingUnit(saved, ru);
            return projectDocumentOpenApiMapper.toResource(saved);
        } catch (InvalidFileTypeException | InvalidFileSizeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur lecture fichier", e);
        }
    }

    @Transactional
    public ProjectDocumentResource updateDocument(
            ProjectApiCaller caller,
            long documentId,
            String title,
            String description,
            Long natureConceptId,
            Long scaleConceptId,
            Long formatConceptId) {

        Document doc = documentContentOpenApiService.requireAccessibleDocument(
                documentId, caller.accessibleInstitutionIds());

        if (StringUtils.hasText(title)) {
            doc.setTitle(title.trim());
        }
        if (description != null) {
            doc.setDescription(description.trim());
        }
        if (natureConceptId != null) {
            doc.setNature(resolveConcept(natureConceptId));
        }
        if (scaleConceptId != null) {
            doc.setScale(resolveConcept(scaleConceptId));
        }
        if (formatConceptId != null) {
            doc.setFormat(resolveConcept(formatConceptId));
        }

        Document saved = (Document) documentService.save(doc);
        return projectDocumentOpenApiMapper.toResource(saved);
    }

    private Document buildDocumentShell(
            String title,
            String description,
            Long natureConceptId,
            Long scaleConceptId,
            Long formatConceptId,
            InstitutionDTO institution,
            MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file est obligatoire");
        }
        String trimmedTitle = title == null ? "" : title.trim();
        if (trimmedTitle.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title est obligatoire");
        }

        Document document = new Document();
        document.setTitle(trimmedTitle);
        document.setDescription(description == null ? null : description.trim());
        document.setNature(resolveConcept(natureConceptId));
        document.setScale(resolveConcept(scaleConceptId));
        document.setFormat(resolveConcept(formatConceptId));
        document.setFileName(file.getOriginalFilename());
        document.setMimeType(file.getContentType());
        document.setSize(file.getSize());

        InstitutionSettings settings = institutionService.createOrGetSettingsOf(institution);
        if (Boolean.TRUE.equals(settings.getArkIsEnabled())) {
            Ark ark = arkService.generateAndSave(settings);
            document.setArk(ark);
        }

        return document;
    }

    private Concept resolveConcept(Long id) {
        if (id == null) {
            return null;
        }
        return conceptService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Concept introuvable : " + id));
    }
}
