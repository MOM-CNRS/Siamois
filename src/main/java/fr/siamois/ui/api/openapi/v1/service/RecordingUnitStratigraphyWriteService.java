package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationEndKind;
import fr.siamois.domain.models.relation.RelationLink;
import fr.siamois.domain.models.relation.RelationSide;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.relation.RelationLinkService;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.relation.RelationDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationField;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkRepository;
import fr.siamois.ui.api.openapi.v1.request.recordingunit.RecordingUnitStratigraphicRelationshipRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * The stratigraphic relationships of the bulk actions ("add the same relationship to each selected unit"), on the
 * configurable relations (docs/reprise-stratigraphie.md, §7), through {@link RelationLinkService}; the fiche writes
 * its links through /api/v1/relation-links. A relationship is named by
 * the other unit's position relative to the owner: {@code anterior} is an order link entered from the owner's field
 * -328 (the owner is posterior, the link's source), {@code posterior} one entered from its mirror -329 (the owner is
 * anterior), {@code synchronous} a synchrony link. The type concept is read from the owner, so a type picked from
 * the mirror is stored as the qualifier it mirrors. All the checks are the link service's.
 * <p>
 * A relationship is named by its pair of units: when an order has several links between two units, the first one is
 * the one changed or removed.
 */
@Service
@RequiredArgsConstructor
public class RecordingUnitStratigraphyWriteService {

    public static final String ANTERIOR = "anterior";
    public static final String POSTERIOR = "posterior";
    public static final String SYNCHRONOUS = "synchronous";

    private static final String NOT_FOUND = "Relation stratigraphique introuvable";

    private final RecordingUnitService recordingUnitService;
    private final RelationLinkService relationLinkService;
    private final RelationDefinitionRepository definitionRepository;
    private final RelationLinkRepository linkRepository;

    @Transactional
    public void add(String recordingUnitKey, RecordingUnitStratigraphicRelationshipRequest body,
                    PersonDTO person, Set<Long> accessibleInstitutionIds) {
        String position = requirePosition(body);
        RecordingUnit owner = accessibleUnit(recordingUnitKey, accessibleInstitutionIds);
        long otherId = body.getRelatedRecordingUnitId();
        if (!linkRepository.findStratigraphicBetween(owner.getId(), otherId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ces deux unités sont déjà reliées");
        }
        boolean synchronous = SYNCHRONOUS.equals(position);
        RelationDefinition definition = definition(synchronous ? RelationField.Stratigraphy.SYNCHRONY_FIELD
                : RelationField.Stratigraphy.ORDER_SOURCE_FIELD);
        RelationSide side = POSTERIOR.equals(position) ? RelationSide.TARGET : RelationSide.SOURCE;
        relationLinkService.create(new RelationLinkService.CreateRequest(definition.getId(), side,
                unit(owner.getId()), unit(otherId), body.getTypeConceptId(), Boolean.TRUE.equals(body.getUncertain())),
                person, accessibleInstitutionIds);
    }

    @Transactional
    public void remove(String recordingUnitKey, long relatedRecordingUnitId,
                       PersonDTO person, Set<Long> accessibleInstitutionIds) {
        RecordingUnit owner = accessibleUnit(recordingUnitKey, accessibleInstitutionIds);
        RelationLink link = linkBetween(owner.getId(), relatedRecordingUnitId);
        relationLinkService.delete(link.getId(), sideOf(link, owner.getId()), person, accessibleInstitutionIds);
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

    /** The side of the owner's field: the source of an order link it is the posterior unit of, else its mirror. */
    private static RelationSide sideOf(RelationLink link, long ownerId) {
        return link.getSourceId().equals(ownerId) ? RelationSide.SOURCE : RelationSide.TARGET;
    }

    private RelationLink linkBetween(long ownerId, long otherId) {
        return linkRepository.findStratigraphicBetween(ownerId, otherId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND));
    }

    private RecordingUnit accessibleUnit(String key, Set<Long> accessibleInstitutionIds) {
        return recordingUnitService.findAccessibleRecordingUnitWithEntity(key, accessibleInstitutionIds, null).entity();
    }

    private RelationDefinition definition(long sourceField) {
        return definitionRepository.findByField(sourceField).orElseThrow(() ->
                new IllegalStateException("La relation stratigraphique du champ " + sourceField + " n'est pas définie"));
    }

    private static RelationLinkService.End unit(long id) {
        return new RelationLinkService.End(RelationEndKind.RECORDING_UNIT, id);
    }
}
