package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationshipId;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.infrastructure.database.repositories.recordingunit.StratigraphicRelationshipRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitStratigraphicRelationshipRequest;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitStratigraphicRelationshipUpdateRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Writes the stratigraphic relationships of a recording unit through the API, on the
 * {@code stratigraphic_relationship} table as it is today: the first unit of a stored relationship
 * is the older one for an asynchronous relationship, and the type concept is read from the unit
 * the call is made for toward the other unit ({@code concept_direction} is true when that unit is
 * the first one).
 */
@Service
@RequiredArgsConstructor
public class RecordingUnitStratigraphyWriteService {

    public static final String ANTERIOR = "anterior";
    public static final String POSTERIOR = "posterior";
    public static final String SYNCHRONOUS = "synchronous";

    private static final String NOT_FOUND = "Relation stratigraphique introuvable";

    private final RecordingUnitService recordingUnitService;
    private final StratigraphicRelationshipRepository relationshipRepository;
    private final ConceptRepository conceptRepository;
    private final ProfilePermissionService profilePermissionService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void add(String recordingUnitKey, RecordingUnitStratigraphicRelationshipRequest body,
                    PersonDTO person, Set<Long> accessibleInstitutionIds) {
        String position = requirePosition(body);
        RecordingUnit owner = writableOwner(recordingUnitKey, person, accessibleInstitutionIds);
        RecordingUnit other = accessibleUnit(body.getRelatedRecordingUnitId(), accessibleInstitutionIds);
        requireLinkable(owner, other);

        // The older unit is the first one; a synchronous relationship is stored the way it was asked.
        boolean ownerIsFirst = !ANTERIOR.equals(position);
        RecordingUnit first = ownerIsFirst ? owner : other;
        RecordingUnit second = ownerIsFirst ? other : owner;
        if (!SYNCHRONOUS.equals(position) && reaches(second, first)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Impossible de créer la relation : cycle stratigraphique détecté");
        }

        StratigraphicRelationship relationship = new StratigraphicRelationship();
        StratigraphicRelationshipId id = new StratigraphicRelationshipId();
        id.setUnit1Id(first.getId());
        id.setUnit2Id(second.getId());
        relationship.setId(id);
        relationship.setUnit1(first);
        relationship.setUnit2(second);
        relationship.setIsAsynchronous(!SYNCHRONOUS.equals(position));
        fill(relationship, body.getTypeConceptId(), body.getUncertain(), ownerIsFirst);
        relationshipRepository.save(relationship);
    }

    @Transactional
    public void update(String recordingUnitKey, long relatedRecordingUnitId,
                       RecordingUnitStratigraphicRelationshipUpdateRequest body,
                       PersonDTO person, Set<Long> accessibleInstitutionIds) {
        RecordingUnit owner = writableOwner(recordingUnitKey, person, accessibleInstitutionIds);
        RecordingUnit other = accessibleUnit(relatedRecordingUnitId, accessibleInstitutionIds);
        StratigraphicRelationship relationship = find(owner, other)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND));
        boolean ownerIsFirst = Objects.equals(relationship.getUnit1().getId(), owner.getId());
        fill(relationship, body == null ? null : body.getTypeConceptId(), body == null ? null : body.getUncertain(), ownerIsFirst);
        relationshipRepository.save(relationship);
    }

    @Transactional
    public void remove(String recordingUnitKey, long relatedRecordingUnitId,
                       PersonDTO person, Set<Long> accessibleInstitutionIds) {
        RecordingUnit owner = writableOwner(recordingUnitKey, person, accessibleInstitutionIds);
        RecordingUnit other = accessibleUnit(relatedRecordingUnitId, accessibleInstitutionIds);
        StratigraphicRelationship relationship = find(owner, other)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND));
        relationshipRepository.delete(relationship);
        entityManager.flush();
    }

    /** The request's position, once the request is known to be complete and well-formed. */
    private static String requirePosition(RecordingUnitStratigraphicRelationshipRequest body) {
        if (body == null || body.getRelatedRecordingUnitId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "relatedRecordingUnitId est obligatoire");
        }
        String position = body.getPosition();
        if (!ANTERIOR.equals(position) && !POSTERIOR.equals(position) && !SYNCHRONOUS.equals(position)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "position doit valoir anterior, posterior ou synchronous");
        }
        return position;
    }

    /** Two distinct units of one operation that no relationship joins yet. */
    private void requireLinkable(RecordingUnit owner, RecordingUnit other) {
        if (Objects.equals(owner.getId(), other.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Une unité ne peut pas être reliée à elle-même");
        }
        if (!sameActionUnit(owner, other)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Les deux unités doivent appartenir à la même opération");
        }
        if (find(owner, other).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ces deux unités sont déjà reliées");
        }
    }

    private void fill(StratigraphicRelationship relationship, Long typeConceptId, Boolean uncertain, boolean ownerIsFirst) {
        Concept concept = null;
        if (typeConceptId != null) {
            concept = conceptRepository.findById(typeConceptId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Concept de type introuvable"));
        }
        relationship.setConcept(concept);
        relationship.setConceptDirection(concept == null ? null : ownerIsFirst);
        relationship.setUncertain(Boolean.TRUE.equals(uncertain));
    }

    private RecordingUnit writableOwner(String key, PersonDTO person, Set<Long> accessibleInstitutionIds) {
        RecordingUnitService.AccessibleRecordingUnit owner =
                recordingUnitService.findAccessibleRecordingUnitWithEntity(key, accessibleInstitutionIds, null);
        assertWritePermission(owner.dto(), person);
        return owner.entity();
    }

    private RecordingUnit accessibleUnit(long id, Set<Long> accessibleInstitutionIds) {
        return recordingUnitService.findAccessibleRecordingUnitWithEntity(
                String.valueOf(id), accessibleInstitutionIds, null).entity();
    }

    private void assertWritePermission(RecordingUnitDTO dto, PersonDTO person) {
        InstitutionDTO institution = dto.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unité sans organisation");
        }
        if (!profilePermissionService.hasRecordingUnitWritePermission(new UserInfo(institution, person, null), dto)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification non autorisée");
        }
    }

    private static boolean sameActionUnit(RecordingUnit a, RecordingUnit b) {
        return a.getActionUnit() != null && b.getActionUnit() != null
                && Objects.equals(a.getActionUnit().getId(), b.getActionUnit().getId());
    }

    /** The relationship between two units, whichever of them is its first one. */
    private Optional<StratigraphicRelationship> find(RecordingUnit a, RecordingUnit b) {
        Optional<StratigraphicRelationship> direct = relationshipRepository.findByUnit1AndUnit2(a, b);
        return direct.isPresent() ? direct : relationshipRepository.findByUnit1AndUnit2(b, a);
    }

    /** Whether {@code target} is reachable from {@code start} going from older to more recent units. */
    private boolean reaches(RecordingUnit start, RecordingUnit target) {
        Set<Long> seen = new HashSet<>();
        Deque<Long> todo = new ArrayDeque<>();
        todo.push(start.getId());
        while (!todo.isEmpty()) {
            Long current = todo.pop();
            if (!seen.add(current)) continue;
            if (Objects.equals(current, target.getId())) return true;
            for (StratigraphicRelationship r : relationshipRepository.findAllInvolvingRecordingUnitId(current)) {
                if (Boolean.TRUE.equals(r.getIsAsynchronous()) && Objects.equals(r.getUnit1().getId(), current)) {
                    todo.push(r.getUnit2().getId());
                }
            }
        }
        return false;
    }
}
