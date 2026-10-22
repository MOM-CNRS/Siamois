package fr.siamois.ui.api.openapi.v1.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.document.Document;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.ui.api.openapi.v1.request.document.DocumentBatchItemRequest;
import fr.siamois.ui.api.openapi.v1.request.document.DocumentBatchManifestRequest;
import fr.siamois.ui.api.openapi.v1.request.document.DocumentBatchTargetRequest;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentBatchItemResult;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentBatchResponse;
import fr.siamois.utils.DocumentUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DocumentBulkOpenApiService {

    public static final String TYPE_PROJECTS = "projects";
    public static final String TYPE_RECORDING_UNITS = "recording-units";
    public static final String TYPE_FINDS = "finds";
    public static final String TYPE_PLACES = "places";

    private final DocumentWriteOpenApiService documentWriteOpenApiService;
    private final ObjectMapper objectMapper;

    @Value("${siamois.api.documents.batch.max-items:50}")
    private int maxItems;

    @Transactional
    public DocumentBatchResponse processBatch(
            ProjectApiCaller caller,
            DocumentBatchManifestRequest manifest,
            Map<String, MultipartFile> filesByPartName,
            String lang) {

        if (manifest == null || manifest.getItems() == null || manifest.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "manifest.items est obligatoire");
        }
        if (manifest.getItems().size() > maxItems) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Trop d'items (max " + maxItems + ")");
        }

        DocumentBatchResponse response = new DocumentBatchResponse();
        Set<String> linkedInBatch = new HashSet<>();
        for (int i = 0; i < manifest.getItems().size(); i++) {
            DocumentBatchItemRequest item = manifest.getItems().get(i);
            MultipartFile file = filesByPartName.get("file_" + i);
            response.getResults().add(processItem(caller, item, file, lang, linkedInBatch));
        }
        return response;
    }

    public DocumentBatchManifestRequest parseManifest(String manifestJson) {
        if (!StringUtils.hasText(manifestJson)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "manifest est obligatoire");
        }
        try {
            return objectMapper.readValue(manifestJson, DocumentBatchManifestRequest.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "manifest JSON invalide", e);
        }
    }

    private DocumentBatchItemResult processItem(
            ProjectApiCaller caller,
            DocumentBatchItemRequest item,
            MultipartFile file,
            String lang,
            Set<String> linkedInBatch) {

        DocumentBatchItemResult result = new DocumentBatchItemResult();
        result.setClientKey(item == null ? null : item.getClientKey());
        if (item == null) {
            result.setStatus("failed");
            result.getErrors().add("Item manifeste manquant");
            return result;
        }
        if (item.getTargets() == null || item.getTargets().isEmpty()) {
            result.setStatus("failed");
            result.getErrors().add("Au moins une cible (targets) est obligatoire");
            return result;
        }
        if (file == null || file.isEmpty()) {
            result.setStatus("failed");
            result.getErrors().add("Fichier manquant pour cet item");
            return result;
        }

        List<ResolvedTarget> resolved = new ArrayList<>();
        for (DocumentBatchTargetRequest target : item.getTargets()) {
            try {
                resolved.add(resolveWritableTarget(caller, target, lang));
            } catch (ResponseStatusException ex) {
                result.getErrors().add(formatTarget(target) + ": " + ex.getReason());
            } catch (RuntimeException ex) {
                result.getErrors().add(formatTarget(target) + ": " + ex.getMessage());
            }
        }
        if (resolved.isEmpty()) {
            result.setStatus("failed");
            return result;
        }

        final String md5;
        try {
            md5 = DocumentUtils.md5(file.getBytes());
        } catch (IOException e) {
            result.setStatus("failed");
            result.getErrors().add("Erreur lecture fichier");
            return result;
        }

        List<ResolvedTarget> toLink = new ArrayList<>();
        for (ResolvedTarget target : resolved) {
            String key = targetKey(md5, target);
            DocumentBatchTargetRequest ref = new DocumentBatchTargetRequest(target.resourceType(), target.id());
            if (linkedInBatch.contains(key) || alreadyLinked(md5, target)) {
                result.getSkippedTargets().add(ref);
            } else {
                toLink.add(target);
            }
        }
        if (toLink.isEmpty()) {
            result.setStatus("skipped");
            return result;
        }

        InstitutionDTO institution = toLink.get(0).institution();
        UserInfo userInfo = new UserInfo(institution, caller.person(), lang);
        try {
            Document saved = documentWriteOpenApiService.saveUploadedDocument(
                    userInfo,
                    item.getTitle(),
                    item.getDescription(),
                    item.getNatureConceptId(),
                    item.getScaleConceptId(),
                    item.getFormatConceptId(),
                    institution,
                    file);

            for (ResolvedTarget target : toLink) {
                String key = targetKey(md5, target);
                DocumentBatchTargetRequest ref = new DocumentBatchTargetRequest(target.resourceType(), target.id());
                boolean linked;
                try {
                    linked = linkTarget(saved, target);
                } catch (DataIntegrityViolationException e) {
                    linked = false;
                }
                if (linked) {
                    result.getLinkedTargets().add(ref);
                    linkedInBatch.add(key);
                } else {
                    result.getSkippedTargets().add(ref);
                    linkedInBatch.add(key);
                }
            }
            result.setDocument(documentWriteOpenApiService.toResource(saved));
            result.setStatus(result.getLinkedTargets().isEmpty() ? "skipped" : "created");
        } catch (DataIntegrityViolationException ex) {
            result.setStatus("skipped");
            result.getErrors().add("Document déjà présent pour les cibles sélectionnées");
        } catch (ResponseStatusException ex) {
            result.setStatus("failed");
            result.getErrors().add(ex.getReason() != null ? ex.getReason() : ex.getMessage());
        } catch (RuntimeException ex) {
            result.setStatus("failed");
            result.getErrors().add(ex.getMessage());
        }
        return result;
    }

    private boolean alreadyLinked(String md5, ResolvedTarget target) {
        return switch (target.resourceType()) {
            case TYPE_PROJECTS -> documentWriteOpenApiService.existsByHash(target.actionUnit(), md5);
            case TYPE_RECORDING_UNITS -> documentWriteOpenApiService.existsByHash(target.recordingUnit(), md5);
            case TYPE_FINDS -> documentWriteOpenApiService.existsByHash(target.specimen(), md5);
            case TYPE_PLACES -> documentWriteOpenApiService.existsByHash(target.place(), md5);
            default -> false;
        };
    }

    private static String targetKey(String md5, ResolvedTarget target) {
        return md5 + "|" + target.resourceType() + "/" + target.id();
    }

    private boolean linkTarget(Document document, ResolvedTarget target) {
        return switch (target.resourceType()) {
            case TYPE_PROJECTS -> documentWriteOpenApiService.linkIfAbsent(document, target.actionUnit());
            case TYPE_RECORDING_UNITS -> documentWriteOpenApiService.linkIfAbsent(document, target.recordingUnit());
            case TYPE_FINDS -> documentWriteOpenApiService.linkIfAbsent(document, target.specimen());
            case TYPE_PLACES -> documentWriteOpenApiService.linkIfAbsent(document, target.place());
            default -> false;
        };
    }

    private ResolvedTarget resolveWritableTarget(
            ProjectApiCaller caller,
            DocumentBatchTargetRequest target,
            String lang) {

        if (target == null || !StringUtils.hasText(target.getResourceType()) || !StringUtils.hasText(target.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resourceType et id obligatoires");
        }
        String type = target.getResourceType().trim().toLowerCase(Locale.ROOT);
        String id = target.getId().trim();
        return switch (type) {
            case TYPE_PROJECTS -> {
                AccessibleProjectForApi row = documentWriteOpenApiService.requireAccessibleProject(caller, id);
                ActionUnitDTO au = row.actionUnit();
                InstitutionDTO institution = au.getCreatedByInstitution();
                if (institution == null || institution.getId() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans organisation");
                }
                yield new ResolvedTarget(TYPE_PROJECTS, id, institution, au, null, null, null);
            }
            case TYPE_RECORDING_UNITS -> {
                RecordingUnitDTO ru = documentWriteOpenApiService.requireWritableRecordingUnit(caller, id, lang);
                yield new ResolvedTarget(TYPE_RECORDING_UNITS, id, ru.getCreatedByInstitution(), null, ru, null, null);
            }
            case TYPE_FINDS -> {
                SpecimenDTO specimen = documentWriteOpenApiService.requireWritableFind(caller, id, lang);
                yield new ResolvedTarget(TYPE_FINDS, id, specimen.getCreatedByInstitution(), null, null, specimen, null);
            }
            case TYPE_PLACES -> {
                long placeId;
                try {
                    placeId = Long.parseLong(id);
                } catch (NumberFormatException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id de lieu numérique attendu");
                }
                SpatialUnitDTO place = documentWriteOpenApiService.requireWritablePlace(caller, placeId, lang);
                yield new ResolvedTarget(TYPE_PLACES, id, place.getCreatedByInstitution(), null, null, null, place);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "resourceType inconnu : " + target.getResourceType());
        };
    }

    private static String formatTarget(DocumentBatchTargetRequest target) {
        if (target == null) {
            return "target";
        }
        return (target.getResourceType() == null ? "?" : target.getResourceType())
                + "/" + (target.getId() == null ? "?" : target.getId());
    }

    private record ResolvedTarget(
            String resourceType,
            String id,
            InstitutionDTO institution,
            ActionUnitDTO actionUnit,
            RecordingUnitDTO recordingUnit,
            SpecimenDTO specimen,
            SpatialUnitDTO place
    ) {
    }
}
