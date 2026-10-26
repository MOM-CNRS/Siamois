package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.dto.entity.PersonDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code GET /api/v1/identifier-suggestions}: the identifier a creation form suggests, for every entity
 * whose identifier is generated. Each entity's own service builds the new entity the suggestion is
 * computed for; this one only routes to it.
 */
@Service
@RequiredArgsConstructor
public class IdentifierSuggestionService {

    /** The entities with a generated identifier, named by their collection's path segment ({@code /api/v1/<segment>}). */
    public enum EntityType {
        RECORDING_UNITS("recording-units"),
        PHASES("phases"),
        CONTAINERS("containers"),
        STUDIES("studies"),
        DOCUMENTS("documents"),
        /** Created on a recording unit, not on a project: suggested from {@code recordingUnitId}. */
        FINDS("finds");

        private final String segment;

        EntityType(String segment) {
            this.segment = segment;
        }

        public String segment() {
            return segment;
        }

        /** 400 for a missing or unknown segment. */
        public static EntityType fromSegment(String segment) {
            return Arrays.stream(values())
                    .filter(type -> type.segment.equals(segment))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "entityType inconnu : " + segment + " (attendu : " + Arrays.stream(values())
                                    .map(EntityType::segment).collect(Collectors.joining(", ")) + ")"));
        }
    }

    private final RecordingUnitOpenApiService recordingUnitOpenApiService;
    private final PhaseOpenApiService phaseOpenApiService;
    private final ContainerOpenApiService containerOpenApiService;
    private final StudyOpenApiService studyOpenApiService;
    private final DocumentOpenApiService documentOpenApiService;
    private final FindOpenApiService findOpenApiService;

    /**
     * @param projectId       the project the entity would be created in — every entity but a find
     * @param recordingUnitId the recording unit a find would be created on — finds only
     * @return the suggested identifier, or {@code null} when the project configures no format for it
     */
    public String suggest(String entityType, String projectId, String recordingUnitId, String typeId,
                          PersonDTO personDto, Set<Long> accessibleInstitutionIds, String lang) {
        return switch (EntityType.fromSegment(entityType)) {
            case RECORDING_UNITS -> recordingUnitOpenApiService.suggestIdentifier(
                    projectId, typeId, personDto, accessibleInstitutionIds, lang);
            case PHASES -> phaseOpenApiService.suggestIdentifier(
                    projectId, typeId, personDto, accessibleInstitutionIds, lang);
            case CONTAINERS -> containerOpenApiService.suggestIdentifier(
                    projectId, typeId, personDto, accessibleInstitutionIds, lang);
            case STUDIES -> studyOpenApiService.suggestIdentifier(
                    projectId, typeId, personDto, accessibleInstitutionIds, lang);
            case DOCUMENTS -> documentOpenApiService.suggestIdentifier(
                    projectId, typeId, personDto, accessibleInstitutionIds, lang);
            case FINDS -> findOpenApiService.suggestIdentifier(
                    recordingUnitId, typeId, personDto, accessibleInstitutionIds, lang);
        };
    }
}
