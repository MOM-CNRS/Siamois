package fr.siamois.domain.services;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Detects the relationships that forbid deleting an entity. Belonging to a project does not count;
 * every link to another entity (hierarchy, stratigraphy, documents, phases, studies, containers,
 * groups, movements, spatial context...) does.
 */
@Service
public class EntityLinkGuard {

    /** Entity kinds that can be deleted. */
    public enum Kind {
        RECORDING_UNIT,
        SPECIMEN,
        SPATIAL_UNIT,
        DOCUMENT,
        PHASE,
        STUDY,
        CONTAINER
    }

    /** A blocking link: the label shown to the user and a native count query taking {@code :id}. */
    private record Link(String label, String countSql) {
    }

    private static final String L_SPECIMENS = "des mobiliers";
    private static final String L_DOCUMENTS = "des documents";
    private static final String L_PHASES = "des phases";
    private static final String L_STUDIES = "des études";
    private static final String L_CONTAINERS = "des contenants";
    private static final String L_RECORDING_UNITS = "des unités d'enregistrement";

    private static final String PREFIX = "SELECT COUNT(*) FROM ";

    private static final Map<Kind, List<Link>> LINKS = Map.of(
            Kind.RECORDING_UNIT, List.of(
                    new Link("des unités parentes", PREFIX + "recording_unit_hierarchy WHERE fk_child_id = :id"),
                    new Link("des unités filles", PREFIX + "recording_unit_hierarchy WHERE fk_parent_id = :id"),
                    new Link("des relations stratigraphiques", PREFIX + "stratigraphic_relationship "
                            + "WHERE fk_recording_unit_1_id = :id OR fk_recording_unit_2_id = :id"),
                    new Link(L_SPECIMENS, PREFIX + "specimen WHERE fk_recording_unit_id = :id"),
                    new Link(L_DOCUMENTS, PREFIX + "recording_unit_document WHERE fk_recording_unit_id = :id"),
                    new Link(L_PHASES, PREFIX + "recording_unit_phase WHERE fk_recording_unit_id = :id"),
                    new Link(L_STUDIES, PREFIX + "study_recording_unit WHERE fk_recording_unit_id = :id")),
            Kind.SPECIMEN, List.of(
                    new Link("des mobiliers parents", PREFIX + "specimen_hierarchy WHERE fk_child_id = :id"),
                    new Link("des mobiliers enfants", PREFIX + "specimen_hierarchy WHERE fk_parent_id = :id"),
                    new Link("des mouvements", PREFIX + "specimen_movement WHERE fk_specimen_id = :id"),
                    new Link("des groupes", PREFIX + "specimen_group_attribution WHERE fk_specimen_id = :id"),
                    new Link(L_DOCUMENTS, PREFIX + "specimen_document WHERE fk_specimen_id = :id"),
                    new Link(L_CONTAINERS, PREFIX + "specimen_container WHERE fk_specimen_id = :id"),
                    new Link(L_PHASES, PREFIX + "specimen_phase WHERE fk_specimen_id = :id"),
                    new Link(L_STUDIES, PREFIX + "study_specimen WHERE fk_specimen_id = :id")),
            Kind.SPATIAL_UNIT, List.of(
                    new Link("des lieux parents", PREFIX + "spatial_hierarchy WHERE fk_child_id = :id"),
                    new Link("des lieux enfants", PREFIX + "spatial_hierarchy WHERE fk_parent_id = :id"),
                    new Link(L_RECORDING_UNITS, PREFIX + "recording_unit WHERE fk_spatial_unit_id = :id"),
                    new Link("le contexte spatial d'un projet",
                            PREFIX + "action_unit_spatial_context WHERE fk_spatial_unit_id = :id"),
                    new Link("des projets (lieu principal)", PREFIX + "action_unit WHERE fk_main_location = :id"),
                    new Link(L_CONTAINERS, PREFIX + "container WHERE fk_spatial_unit_id = :id"),
                    new Link(L_DOCUMENTS, PREFIX + "spatial_unit_document WHERE fk_spatial_unit_id = :id")),
            Kind.DOCUMENT, List.of(
                    new Link(L_RECORDING_UNITS, PREFIX + "recording_unit_document WHERE fk_document_id = :id"),
                    new Link(L_SPECIMENS, PREFIX + "specimen_document WHERE fk_document_id = :id"),
                    new Link("des lieux", PREFIX + "spatial_unit_document WHERE fk_document_id = :id"),
                    new Link(L_PHASES, PREFIX + "phase_document WHERE fk_document_id = :id"),
                    new Link(L_CONTAINERS, PREFIX + "container_document WHERE fk_document_id = :id"),
                    new Link(L_STUDIES, PREFIX + "study_document WHERE fk_document_id = :id")),
            Kind.PHASE, List.of(
                    new Link(L_RECORDING_UNITS, PREFIX + "recording_unit_phase WHERE fk_phase_id = :id"),
                    new Link(L_SPECIMENS, PREFIX + "specimen_phase WHERE fk_phase_id = :id"),
                    new Link(L_DOCUMENTS, PREFIX + "phase_document WHERE fk_phase_id = :id")),
            Kind.STUDY, List.of(
                    new Link(L_RECORDING_UNITS, PREFIX + "study_recording_unit WHERE fk_study_id = :id"),
                    new Link(L_SPECIMENS, PREFIX + "study_specimen WHERE fk_study_id = :id"),
                    new Link(L_DOCUMENTS, PREFIX + "study_document WHERE fk_study_id = :id")),
            Kind.CONTAINER, List.of(
                    new Link("un contenant parent", PREFIX + "container WHERE container_id = :id AND fk_parent_id IS NOT NULL"),
                    new Link("des contenants enfants", PREFIX + "container WHERE fk_parent_id = :id"),
                    new Link(L_SPECIMENS, PREFIX + "specimen_container WHERE fk_container_id = :id"),
                    new Link(L_DOCUMENTS, PREFIX + "container_document WHERE fk_container_id = :id"))
    );

    @PersistenceContext
    private EntityManager entityManager;

    /** Labels of the links currently attached to the entity (empty when it can be deleted). */
    @Transactional(readOnly = true)
    public List<String> blockingLinks(Kind kind, long id) {
        return findBlockingLinks(kind, id);
    }

    private List<String> findBlockingLinks(Kind kind, long id) {
        List<String> found = new ArrayList<>();
        for (Link link : LINKS.get(kind)) {
            Number count = (Number) entityManager.createNativeQuery(link.countSql())
                    .setParameter("id", id)
                    .getSingleResult();
            if (count != null && count.longValue() > 0) {
                found.add(link.label());
            }
        }
        return found;
    }

    /**
     * @throws IllegalStateException (mapped to HTTP 409) when the entity still has relationships
     */
    @Transactional(readOnly = true)
    public void requireNoLinks(Kind kind, long id, String entityLabel) {
        List<String> links = findBlockingLinks(kind, id);
        if (!links.isEmpty()) {
            throw new IllegalStateException("Impossible de supprimer : " + entityLabel
                    + " est lié à " + String.join(", ", links)
                    + ". Retirez d'abord ces relations.");
        }
    }
}
