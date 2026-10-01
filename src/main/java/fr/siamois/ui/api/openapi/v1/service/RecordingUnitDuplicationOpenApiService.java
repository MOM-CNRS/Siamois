package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException;
import fr.siamois.domain.models.exceptions.recordingunit.RecordingUnitIdentifierAlreadyExistsException;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.recordingunit.RecordingUnitStructureDuplicationResult;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiExecutionContext;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitDuplicationResource;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitStructureResource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "Dupliquer la structure" of a recording unit: the tree the user picks from, and the duplication
 * of the picked part ({@link RecordingUnitService#duplicateStructure}, the rule JSF's dialog used).
 */
@Service
@RequiredArgsConstructor
public class RecordingUnitDuplicationOpenApiService {

    /** Nodes the picking tree shows at most, as in JSF's dialog. */
    static final int MAX_NODES = 500;
    static final int MAX_COPIES = 50;
    /** Units one request may create in all (copies × selected units). */
    static final int MAX_CREATED = 500;

    private final RecordingUnitService recordingUnitService;
    private final ProfilePermissionService profilePermissionService;

    /** {@code GET /recording-units/{id}/structure}: the unit and its descendants. */
    public RecordingUnitStructureResource structure(String key, Set<Long> accessibleInstitutionIds) {
        RecordingUnitDTO root = recordingUnitService.findAccessibleRecordingUnitWithEntity(key, accessibleInstitutionIds, null).dto();

        List<RecordingUnitStructureResource.Node> descendants = new ArrayList<>();
        Set<Long> visited = new HashSet<>(Set.of(root.getId()));
        Deque<RecordingUnitDTO> queue = new ArrayDeque<>(List.of(root));
        boolean truncated = false;
        while (!queue.isEmpty() && !truncated) {
            RecordingUnitDTO parent = queue.poll();
            for (RecordingUnitDTO child : recordingUnitService.findAllByParentRecordingUnit(parent.getId())) {
                if (visited.add(child.getId())) {
                    truncated = descendants.size() >= MAX_NODES;
                    if (truncated) break;
                    descendants.add(new RecordingUnitStructureResource.Node(child.getId(), child.getFullIdentifier(), parent.getId()));
                    queue.add(child);
                }
            }
        }
        return new RecordingUnitStructureResource(
                new RecordingUnitStructureResource.Node(root.getId(), root.getFullIdentifier(), null), descendants, truncated);
    }

    /** {@code POST /recording-units/{id}/duplicate-structure}. */
    public RecordingUnitDuplicationResource duplicateStructure(String key, Integer copies, List<Long> descendantIds,
                                                               PersonDTO person, Set<Long> accessibleInstitutionIds, String lang) {
        int count = copies == null ? 1 : copies;
        if (count < 1 || count > MAX_COPIES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "copies doit être compris entre 1 et " + MAX_COPIES);
        }
        RecordingUnitDTO root = recordingUnitService.findAccessibleRecordingUnitWithEntity(key, accessibleInstitutionIds, null).dto();
        InstitutionDTO institution = root.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UE sans organisation");
        }
        UserInfo userInfo = new UserInfo(institution, person, lang);
        if (!profilePermissionService.hasRecordingUnitWritePermission(userInfo, root)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Duplication non autorisée");
        }

        Set<Long> selected = descendantIds == null ? Set.of() : new HashSet<>(descendantIds);
        if ((long) (1 + selected.size()) * count > MAX_CREATED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Trop d'UE à créer en une fois (maximum " + MAX_CREATED + ")");
        }

        RecordingUnitStructureDuplicationResult result;
        try {
            result = OpenApiExecutionContext.callWithUserInfo(userInfo,
                    () -> recordingUnitService.duplicateStructure(root, selected, count));
        } catch (RecordingUnitIdentifierAlreadyExistsException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Identifiant généré déjà attribué : " + e.getIdentifier(), e);
        } catch (ForbiddenOperationException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Duplication non autorisée", e);
        }
        List<RecordingUnitDuplicationResource.Copy> rootCopies = result.rootCopies().stream()
                .map(copy -> new RecordingUnitDuplicationResource.Copy(copy.getId(), copy.getFullIdentifier()))
                .toList();
        return new RecordingUnitDuplicationResource(rootCopies, result.allCreated().size());
    }
}
