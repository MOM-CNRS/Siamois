package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.exceptions.InvalidFileSizeException;
import fr.siamois.domain.models.exceptions.InvalidFileTypeException;
import fr.siamois.domain.models.exceptions.spatialunit.SpatialUnitNotFoundException;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.settings.InstitutionSettings;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.ark.ArkService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.*;
import fr.siamois.mapper.ActionUnitMapper;
import fr.siamois.mapper.ActionUnitSummaryMapper;
import fr.siamois.ui.api.openapi.v1.mapper.ProjectDocumentOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
import fr.siamois.utils.context.ExecutionContextHolder;
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

    /** The application's context path: the stored file's {@code /content/...} URL is relative to it. */
    @org.springframework.beans.factory.annotation.Value("${server.servlet.context-path:}")
    private String contextPath;

    private final ProjectApiService projectApiService;
    private final RecordingUnitService recordingUnitService;
    private final SpecimenService specimenService;
    private final SpatialUnitService spatialUnitService;
    private final DocumentContentOpenApiService documentContentOpenApiService;
    private final DocumentService documentService;
    private final ConceptService conceptService;
    private final InstitutionService institutionService;
    private final ProfilePermissionService profilePermissionService;
    private final ArkService arkService;
    private final ProjectDocumentOpenApiMapper projectDocumentOpenApiMapper;
    private final ActionUnitMapper actionUnitMapper;
    private final ActionUnitSummaryMapper actionUnitSummaryMapper;

    @Transactional
    public DocumentResource createForProject(
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
        document.setActionUnit(actionUnitMapper.invertConvert(row.actionUnit()));

        try (InputStream inputStream = file.getInputStream()) {
            Document saved = saveFileInContext(userInfo, document, inputStream);
            return projectDocumentOpenApiMapper.toResource(saved);
        } catch (InvalidFileTypeException | InvalidFileSizeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur lecture fichier", e);
        }
    }

    @Transactional
    public DocumentResource createForRecordingUnit(
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
        if (!profilePermissionService.hasRecordingUnitWritePermission(userInfo, ru)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Création de document non autorisée sur cette UE");
        }

        Document document = buildDocumentShell(
                title, description, natureConceptId, scaleConceptId, formatConceptId, institution, file);
        // the document belongs to the unit's project, and is linked to the unit
        document.setActionUnit(actionUnitSummaryMapper.invertConvert(ru.getActionUnit()));

        try (InputStream inputStream = file.getInputStream()) {
            Document saved = saveFileInContext(userInfo, document, inputStream);
            documentService.addToRecordingUnit(saved, ru);
            return projectDocumentOpenApiMapper.toResource(saved);
        } catch (InvalidFileTypeException | InvalidFileSizeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur lecture fichier", e);
        }
    }

    @Transactional
    public DocumentResource createForFind(
            ProjectApiCaller caller,
            String findIdOrKey,
            String title,
            String description,
            Long natureConceptId,
            Long scaleConceptId,
            Long formatConceptId,
            MultipartFile file,
            String lang) {

        SpecimenDTO specimen = specimenService.findAccessibleByKey(findIdOrKey, caller.accessibleInstitutionIds())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mobilier introuvable ou hors périmètre"));
        InstitutionDTO institution = specimen.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mobilier sans organisation");
        }

        UserInfo userInfo = new UserInfo(institution, caller.person(), lang);
        if (!profilePermissionService.hasSpecimenWritePermission(userInfo, specimen)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Création de document non autorisée sur ce mobilier");
        }

        Document document = buildDocumentShell(
                title, description, natureConceptId, scaleConceptId, formatConceptId, institution, file);

        try (InputStream inputStream = file.getInputStream()) {
            Document saved = saveFileInContext(userInfo, document, inputStream);
            documentService.addToSpecimen(saved, specimen);
            return projectDocumentOpenApiMapper.toResource(saved);
        } catch (InvalidFileTypeException | InvalidFileSizeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur lecture fichier", e);
        }
    }

    @Transactional
    public DocumentResource createForPlace(
            ProjectApiCaller caller,
            long placeId,
            String title,
            String description,
            Long natureConceptId,
            Long scaleConceptId,
            Long formatConceptId,
            MultipartFile file,
            String lang) {

        SpatialUnitDTO place = requireAccessiblePlace(caller, placeId);
        InstitutionDTO institution = place.getCreatedByInstitution();
        UserInfo userInfo = new UserInfo(institution, caller.person(), lang);
        if (!profilePermissionService.hasOrganizationPermission(userInfo, PermissionConstants.ORGANIZATION_MANAGE_PLACES)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Création de document non autorisée sur ce lieu");
        }

        Document document = buildDocumentShell(
                title, description, natureConceptId, scaleConceptId, formatConceptId, institution, file);

        try (InputStream inputStream = file.getInputStream()) {
            Document saved = saveFileInContext(userInfo, document, inputStream);
            documentService.addToSpatialUnit(saved, place);
            return projectDocumentOpenApiMapper.toResource(saved);
        } catch (InvalidFileTypeException | InvalidFileSizeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur lecture fichier", e);
        }
    }

    @Transactional
    public DocumentResource updateDocument(
            ProjectApiCaller caller,
            long documentId,
            String title,
            String description,
            Long natureConceptId,
            Long scaleConceptId,
            Long formatConceptId) {

        Document doc = documentContentOpenApiService.requireWritableDocument(documentId, caller);

        if (StringUtils.hasText(title)) {
            doc.setTitle(title.trim());
        }
        if (description != null) {
            doc.setDescription(description.trim());
        }
        if (natureConceptId != null) {
            doc.replaceNatureBy(resolveConcept(natureConceptId));
        }
        if (scaleConceptId != null) {
            doc.setScale(resolveConcept(scaleConceptId));
        }
        if (formatConceptId != null) {
            applyFormat(doc, resolveConcept(formatConceptId));
        }

        Document saved = (Document) documentService.save(doc);
        return projectDocumentOpenApiMapper.toResource(saved);
    }

    Document saveUploadedDocument(
            UserInfo userInfo,
            String title,
            String description,
            Long natureConceptId,
            Long scaleConceptId,
            Long formatConceptId,
            InstitutionDTO institution,
            MultipartFile file) {

        Document document = buildDocumentShell(
                title, description, natureConceptId, scaleConceptId, formatConceptId, institution, file);
        try (InputStream inputStream = file.getInputStream()) {
            return saveFileInContext(userInfo, document, inputStream);
        } catch (InvalidFileTypeException | InvalidFileSizeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur lecture fichier", e);
        }
    }

    DocumentResource toResource(Document document) {
        return projectDocumentOpenApiMapper.toResource(document);
    }

    AccessibleProjectForApi requireAccessibleProject(ProjectApiCaller caller, String projectIdOrKey) {
        return projectApiService.requireAccessibleProject(caller, projectIdOrKey);
    }

    RecordingUnitDTO requireWritableRecordingUnit(ProjectApiCaller caller, String key, String lang) {
        RecordingUnitDTO ru = recordingUnitService.findAccessibleRecordingUnitByKey(
                key, caller.accessibleInstitutionIds(), null);
        InstitutionDTO institution = ru.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unité d'enregistrement sans organisation");
        }
        UserInfo userInfo = new UserInfo(institution, caller.person(), lang);
        if (!profilePermissionService.hasRecordingUnitWritePermission(userInfo, ru)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Écriture non autorisée sur cette UE");
        }
        return ru;
    }

    SpecimenDTO requireWritableFind(ProjectApiCaller caller, String key, String lang) {
        SpecimenDTO specimen = specimenService.findAccessibleByKey(key, caller.accessibleInstitutionIds())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mobilier introuvable ou hors périmètre"));
        InstitutionDTO institution = specimen.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mobilier sans organisation");
        }
        UserInfo userInfo = new UserInfo(institution, caller.person(), lang);
        if (!profilePermissionService.hasSpecimenWritePermission(userInfo, specimen)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Écriture non autorisée sur ce mobilier");
        }
        return specimen;
    }

    SpatialUnitDTO requireWritablePlace(ProjectApiCaller caller, long placeId, String lang) {
        SpatialUnitDTO place = requireAccessiblePlace(caller, placeId);
        UserInfo userInfo = new UserInfo(place.getCreatedByInstitution(), caller.person(), lang);
        if (!profilePermissionService.hasOrganizationPermission(userInfo, PermissionConstants.ORGANIZATION_MANAGE_PLACES)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Écriture non autorisée sur ce lieu");
        }
        return place;
    }

    boolean linkIfAbsent(Document document, ActionUnitDTO actionUnit) {
        if (document.getMd5Sum() != null && documentService.existInActionUnitByHash(actionUnit, document.getMd5Sum())) {
            return false;
        }
        documentService.addToActionUnit(document, actionUnit);
        return true;
    }

    boolean linkIfAbsent(Document document, RecordingUnitDTO recordingUnit) {
        if (document.getMd5Sum() != null && documentService.existInRecordingUnitByHash(recordingUnit, document.getMd5Sum())) {
            return false;
        }
        documentService.addToRecordingUnit(document, recordingUnit);
        return true;
    }

    boolean linkIfAbsent(Document document, SpecimenDTO specimen) {
        if (document.getMd5Sum() != null && documentService.existInSpecimenByHash(specimen, document.getMd5Sum())) {
            return false;
        }
        documentService.addToSpecimen(document, specimen);
        return true;
    }

    boolean linkIfAbsent(Document document, SpatialUnitDTO place) {
        if (document.getMd5Sum() != null && documentService.existInSpatialUnitByHash(place, document.getMd5Sum())) {
            return false;
        }
        documentService.addToSpatialUnit(document, place);
        return true;
    }

    boolean existsByHash(ActionUnitDTO actionUnit, String hash) {
        return hash != null && documentService.existInActionUnitByHash(actionUnit, hash);
    }

    boolean existsByHash(RecordingUnitDTO recordingUnit, String hash) {
        return hash != null && documentService.existInRecordingUnitByHash(recordingUnit, hash);
    }

    boolean existsByHash(SpecimenDTO specimen, String hash) {
        return hash != null && documentService.existInSpecimenByHash(specimen, hash);
    }

    boolean existsByHash(SpatialUnitDTO place, String hash) {
        return hash != null && documentService.existInSpatialUnitByHash(place, hash);
    }

    private SpatialUnitDTO requireAccessiblePlace(ProjectApiCaller caller, long placeId) {
        SpatialUnitDTO dto;
        try {
            dto = spatialUnitService.findById(placeId);
        } catch (SpatialUnitNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lieu introuvable");
        }
        InstitutionDTO institution = dto.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lieu sans organisation de rattachement");
        }
        projectApiService.assertOrganizationInCallerScope(institution.getId(), caller.accessibleInstitutionIds());
        return dto;
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
        document.replaceNatureBy(resolveConcept(natureConceptId));
        document.setScale(resolveConcept(scaleConceptId));
        applyFormat(document, resolveConcept(formatConceptId));
        document.setFileName(file.getOriginalFilename());
        document.setMimeType(file.getContentType());
        document.setSize(file.getSize());

        InstitutionSettings settings = institutionService.createOrGetSettingsOf(institution);
        if (settings.hasEnabledArkConfig()) {
            Ark ark = arkService.generateAndSave(settings);
            document.setArk(ark);
        }

        return document;
    }

    /**
     * Saves the file in the caller's context: the identifier of a new document is generated from the project's
     * configuration, which is read for the user's institution and language.
     */
    private Document saveFileInContext(UserInfo userInfo, Document document, InputStream inputStream)
            throws InvalidFileTypeException, InvalidFileSizeException, IOException {
        ExecutionContextHolder.set(userInfo);
        try {
            return documentService.saveFile(userInfo, document, inputStream, contextPath);
        } finally {
            ExecutionContextHolder.clear();
        }
    }

    /** The mobile API sends the format as a concept: it goes to the concept column, not to the form's free-text format. */
    private static void applyFormat(Document document, Concept format) {
        document.setFormatConcept(format);
    }

    private Concept resolveConcept(Long id) {
        if (id == null) {
            return null;
        }
        return conceptService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Concept introuvable : " + id));
    }
}
