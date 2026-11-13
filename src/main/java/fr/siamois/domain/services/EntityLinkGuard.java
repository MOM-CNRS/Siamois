package fr.siamois.domain.services;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects the relationships that forbid deleting an entity. Belonging to a project does not count;
 * every link to another entity (hierarchy, stratigraphy, documents, phases, studies, containers,
 * groups, movements, spatial context...) does — except, when several entities of the same kind are
 * deleted together, a link between two of them: the deletion removes it along with them.
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

    /**
     * A blocking link: the label shown to the user, a native count query taking {@code :id} and, for a
     * link to an entity of the same kind, the condition (taking {@code :alongside}) under which the other
     * end is deleted together with it.
     */
    private record Link(String label, String countSql, String otherEndAlongside) {

        /**
         * A link to an entity of another kind: always blocking.
         */
        Link(String label, String countSql) {
            this(label, countSql, null);
        }

        /**
         * The count query of the links that remain once the entities deleted alongside are left out.
         */
        String countSql(Set<Long> alongside) {
            if (otherEndAlongside == null || alongside.isEmpty()) {
                return countSql;
            }
            return countSql + " AND NOT (" + otherEndAlongside + ")";
        }
    }

    private static final String L_SPECIMENS = "des mobiliers";
    private static final String L_DOCUMENTS = "des documents";
    private static final String L_PHASES = "des phases";
    private static final String L_STUDIES = "des études";
    private static final String L_CONTAINERS = "des contenants";
    private static final String L_RECORDING_UNITS = "des unités d'enregistrement";

    private static final String PREFIX = "SELECT COUNT(*) FROM ";
    private static final String PARENT_ALONGSIDE = "fk_parent_id IN (:alongside)";
    private static final String CHILD_ALONGSIDE = "fk_child_id IN (:alongside)";

    private static final Map<Kind, List<Link>> LINKS = Map.of(
            Kind.RECORDING_UNIT, List.of(
                    new Link("des unités parentes", PREFIX + "recording_unit_hierarchy WHERE fk_child_id = :id",
                            PARENT_ALONGSIDE),
                    new Link("des unités filles", PREFIX + "recording_unit_hierarchy WHERE fk_parent_id = :id",
                            CHILD_ALONGSIDE),
                    new Link("des unités en relation stratigraphique", PREFIX + "stratigraphic_relationship "
                            + "WHERE (fk_recording_unit_1_id = :id OR fk_recording_unit_2_id = :id)",
                            "fk_recording_unit_1_id IN (:alongside) AND fk_recording_unit_2_id IN (:alongside)"),
                    new Link(L_SPECIMENS, PREFIX + "specimen WHERE fk_recording_unit_id = :id"),
                    new Link(L_DOCUMENTS, PREFIX + "recording_unit_document WHERE fk_recording_unit_id = :id"),
                    new Link(L_PHASES, PREFIX + "recording_unit_phase WHERE fk_recording_unit_id = :id"),
                    new Link(L_STUDIES, PREFIX + "study_recording_unit WHERE fk_recording_unit_id = :id")),
            Kind.SPECIMEN, List.of(
                    new Link("des mobiliers parents", PREFIX + "specimen_hierarchy WHERE fk_child_id = :id",
                            PARENT_ALONGSIDE),
                    new Link("des mobiliers enfants", PREFIX + "specimen_hierarchy WHERE fk_parent_id = :id",
                            CHILD_ALONGSIDE),
                    new Link("des mouvements", PREFIX + "specimen_movement WHERE fk_specimen_id = :id"),
                    new Link("des groupes", PREFIX + "specimen_group_attribution WHERE fk_specimen_id = :id"),
                    new Link(L_DOCUMENTS, PREFIX + "specimen_document WHERE fk_specimen_id = :id"),
                    new Link(L_CONTAINERS, PREFIX + "specimen_container WHERE fk_specimen_id = :id"),
                    new Link(L_PHASES, PREFIX + "specimen_phase WHERE fk_specimen_id = :id"),
                    new Link(L_STUDIES, PREFIX + "study_specimen WHERE fk_specimen_id = :id")),
            Kind.SPATIAL_UNIT, List.of(
                    new Link("des lieux parents", PREFIX + "spatial_hierarchy WHERE fk_child_id = :id",
                            PARENT_ALONGSIDE),
                    new Link("des lieux enfants", PREFIX + "spatial_hierarchy WHERE fk_parent_id = :id",
                            CHILD_ALONGSIDE),
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
                    new Link("un contenant parent", PREFIX + "container WHERE container_id = :id AND fk_parent_id IS NOT NULL",
                            PARENT_ALONGSIDE),
                    new Link("des contenants enfants", PREFIX + "container WHERE fk_parent_id = :id",
                            "container_id IN (:alongside)"),
                    new Link(L_SPECIMENS, PREFIX + "specimen_container WHERE fk_container_id = :id"),
                    new Link(L_DOCUMENTS, PREFIX + "container_document WHERE fk_container_id = :id"))
    );

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Labels of the links currently attached to the entity (empty when it can be deleted).
     */
    @Transactional(readOnly = true)
    public List<String> blockingLinks(Kind kind, long id) {
        return blockingLinks(kind, id, Set.of());
    }

    /**
     * Labels of the links that forbid deleting the entity together with {@code alongside}: a link to one
     * of those (same kind) does not count.
     *
     * @param kind      the entity's kind
     * @param id        the entity's id
     * @param alongside ids of the entities of the same kind deleted together with it (may contain {@code id})
     * @return the blocking links' labels, empty when it can be deleted
     */
    @Transactional(readOnly = true)
    public List<String> blockingLinks(Kind kind, long id, Set<Long> alongside) {
        List<String> found = new ArrayList<>();
        for (Link link : LINKS.get(kind)) {
            Query query = entityManager.createNativeQuery(link.countSql(alongside)).setParameter("id", id);
            if (link.otherEndAlongside() != null && !alongside.isEmpty()) {
                query.setParameter("alongside", alongside);
            }
            Number count = (Number) query.getSingleResult();
            if (count != null && count.longValue() > 0) {
                found.add(link.label());
            }
        }
        return found;
    }

    /**
     * Fails unless the entity has no link left.
     *
     * @throws IllegalStateException (mapped to HTTP 409) when the entity still has relationships
     */
    @Transactional(readOnly = true)
    public void requireNoLinks(Kind kind, long id, String entityLabel) {
        requireNoLinks(kind, id, entityLabel, Set.of());
    }

    /**
     * Fails unless the entity has no link left but to the entities deleted together with it.
     *
     * @param kind        the entity's kind
     * @param id          the entity's id
     * @param entityLabel the entity named in the message ("l'unité d'enregistrement", "le lieu"…)
     * @param alongside   ids of the entities of the same kind deleted together with it
     * @throws IllegalStateException (mapped to HTTP 409) when the entity still has other relationships
     */
    @Transactional(readOnly = true)
    public void requireNoLinks(Kind kind, long id, String entityLabel, Set<Long> alongside) {
        List<String> links = blockingLinks(kind, id, alongside);
        if (!links.isEmpty()) {
            throw new IllegalStateException(blockedMessage(entityLabel, links, !alongside.isEmpty()));
        }
    }

    /**
     * Why the entity can't be deleted: what it is still linked to and what to do about it.
     */
    static String blockedMessage(String entityLabel, List<String> links, boolean withSelection) {
        String outside = withSelection ? " en dehors de la sélection" : "";
        return "Impossible de supprimer " + entityLabel + ", qui a encore des liens avec "
                + enumerate(links) + outside + ". Retirez d'abord ces relations.";
    }

    /**
     * The labels as a French enumeration: "a", "a et b", "a, b et c".
     */
    private static String enumerate(List<String> labels) {
        if (labels.size() == 1) {
            return labels.get(0);
        }
        return String.join(", ", labels.subList(0, labels.size() - 1)) + " et " + labels.get(labels.size() - 1);
    }
}
