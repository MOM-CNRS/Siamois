package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.ui.api.openapi.v1.resource.breadcrumb.BreadcrumbResource;
import fr.siamois.ui.api.openapi.v1.resource.breadcrumb.BreadcrumbsResource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The fil d'Ariane of a project-owned fiche: every path from its project down to it
 * ({@code GET /api/v1/{collection}/{id}/breadcrumbs}).
 *
 * <p>A recording unit or a find can have several parents, so there can be several paths; they are
 * walked depth-first with parents ordered by id, which makes the first path (the default one) stable
 * from one call to the next. A find is also anchored to the recording unit it belongs to, so its paths
 * go project → (recording unit's own paths) → recording unit → (find's own parents) → find. The number
 * of paths is capped so a densely connected graph cannot blow up the response.</p>
 *
 * <p>A document hangs under the fiches it is linked to: project → (a linked fiche's own paths, a place's
 * hierarchy included) → document. The optional {@code via} context moves the paths through the fiche it
 * was opened from to the front.</p>
 *
 * <p>Access: the project's institution must be one the caller can reach, same rule as the entities'
 * own endpoints; anything else is a 404.</p>
 */
@Service
public class EntityBreadcrumbsService {

    /** Hard cap on the paths returned — the overlay lists them all, a hairball is not readable anyway. */
    static final int MAX_PATHS = 50;

    /** Fixed per-kind query parts (never user input) and the front's registry key. */
    public enum Kind {
        RECORDING_UNIT("recordingUnit", "RecordingUnit", "e.fullIdentifier"),
        FIND("find", "Specimen", "e.fullIdentifier"),
        CONTAINER("container", "Container", "e.identifier"),
        PHASE("phase", "Phase", "coalesce(e.identifier, e.title)"),
        DOCUMENT("document", "Document", "coalesce(e.identifier, e.title)"),
        STUDY("study", "Study", "e.identifier");

        private final String type;
        private final String entity;
        private final String label;

        Kind(String type, String entity, String label) {
            this.type = type;
            this.entity = entity;
            this.label = label;
        }

        /** The kind served under {@code /api/v1/{collection}}, or {@code null}. */
        public static Kind ofCollection(String collection) {
            return switch (collection) {
                case "recording-units" -> RECORDING_UNIT;
                case "finds" -> FIND;
                case "containers" -> CONTAINER;
                case "phases" -> PHASE;
                case "documents" -> DOCUMENT;
                case "studies" -> STUDY;
                default -> null;
            };
        }
    }

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public BreadcrumbsResource findBreadcrumbs(Kind kind, long id, Set<Long> accessibleInstitutionIds) {
        return findBreadcrumbs(kind, id, accessibleInstitutionIds, null);
    }

    /**
     * @param via the context the fiche was opened from, as {@code type:id} (a registry key and an id):
     *            the paths going through that fiche come first, so the default path matches the context
     */
    @Transactional(readOnly = true)
    public BreadcrumbsResource findBreadcrumbs(Kind kind, long id, Set<Long> accessibleInstitutionIds, String via) {
        List<Object[]> own = entityManager.createQuery(
                        "select e.actionUnit.id, e.actionUnit.createdByInstitution.id from " + kind.entity // NOSONAR S2077: constants of the Kind enum
                                + " e where e.id = :id", Object[].class)
                .setParameter("id", id)
                .getResultList();
        if (own.isEmpty() || own.get(0)[0] == null) {
            throw notFound();
        }
        Long projectId = (Long) own.get(0)[0];
        Long institutionId = (Long) own.get(0)[1];
        if (accessibleInstitutionIds == null || institutionId == null || !accessibleInstitutionIds.contains(institutionId)) {
            throw notFound();
        }

        BreadcrumbsResource built = buildFor(kind, id, projectCrumb(projectId));
        return viaFirst(built, via);
    }

    /** Stable: the paths holding the {@code via} crumb move up front, the others keep their order. */
    private static BreadcrumbsResource viaFirst(BreadcrumbsResource built, String via) {
        if (via == null) {
            return built;
        }
        int sep = via.indexOf(':');
        if (sep <= 0 || sep == via.length() - 1) {
            return built;
        }
        String type = via.substring(0, sep);
        String viaId = via.substring(sep + 1);
        List<List<BreadcrumbResource>> through = new ArrayList<>();
        List<List<BreadcrumbResource>> others = new ArrayList<>();
        for (List<BreadcrumbResource> path : built.paths()) {
            boolean holds = path.stream().limit(Math.max(0, path.size() - 1L))
                    .anyMatch(c -> c.type().equals(type) && c.id().equals(viaId));
            (holds ? through : others).add(path);
        }
        through.addAll(others);
        return new BreadcrumbsResource(through, built.truncated());
    }

    private BreadcrumbsResource buildFor(Kind kind, long id, BreadcrumbResource project) {
        List<List<BreadcrumbResource>> result = new ArrayList<>();
        boolean truncated;
        if (kind == Kind.DOCUMENT) {
            truncated = documentPaths(id, project, result);
        } else {
            Chains own = below(kind, id);
            truncated = own.truncated();
            own.paths().forEach(p -> result.add(prefixed(project, p)));
        }
        return new BreadcrumbsResource(result, truncated);
    }

    /** The fiche's own paths under its project (project excluded), itself last. */
    private Chains below(Kind kind, long id) {
        Function<Long, BreadcrumbResource> crumbOf = nodeId -> crumb(kind, nodeId);
        switch (kind) {
            case RECORDING_UNIT:
                return chains(id, this::recordingUnitParents, crumbOf);
            case FIND: {
                Long ruId = one("select e.recordingUnit.id from Specimen e where e.id = :id", id);
                Chains ruChains = ruId == null
                        ? new Chains(List.of(List.of()), false)
                        : chains(ruId, this::recordingUnitParents, nodeId -> crumb(Kind.RECORDING_UNIT, nodeId));
                Chains findChains = chains(id, this::findParents, crumbOf);
                boolean truncated = ruChains.truncated() || findChains.truncated();
                List<List<BreadcrumbResource>> out = new ArrayList<>();
                for (List<BreadcrumbResource> ru : ruChains.paths()) {
                    for (List<BreadcrumbResource> find : findChains.paths()) {
                        if (out.size() >= MAX_PATHS) {
                            truncated = true;
                            break;
                        }
                        List<BreadcrumbResource> path = new ArrayList<>(ru);
                        path.addAll(find);
                        out.add(path);
                    }
                }
                return new Chains(out, truncated);
            }
            case CONTAINER:
                return chains(id, this::containerParent, crumbOf);
            default:
                return new Chains(List.of(List.of(crumb(kind, id))), false);
        }
    }

    /**
     * A document sits under whichever fiche it is linked to: project → (that fiche's own hierarchy) →
     * document. One group of paths per link, linked fiches ordered by kind then id; a document linked
     * to nothing hangs straight under its project.
     *
     * @return true when the cap cut the paths short
     */
    private boolean documentPaths(long id, BreadcrumbResource project, List<List<BreadcrumbResource>> result) {
        BreadcrumbResource self = crumb(Kind.DOCUMENT, id);
        boolean truncated = false;
        List<Chains> groups = new ArrayList<>();
        for (Kind linked : List.of(Kind.RECORDING_UNIT, Kind.FIND, Kind.CONTAINER, Kind.PHASE, Kind.STUDY)) {
            for (Long linkedId : documentLinks(DOCUMENT_LINKS.get(linked), id)) {
                groups.add(below(linked, linkedId));
            }
        }
        for (Long placeId : documentLinks(DOCUMENT_LINKS.get(null), id)) {
            groups.add(chains(placeId, this::placeParents, this::placeCrumb));
        }
        for (Chains group : groups) {
            truncated |= group.truncated();
            for (List<BreadcrumbResource> path : group.paths()) {
                if (result.size() >= MAX_PATHS) {
                    return true;
                }
                List<BreadcrumbResource> full = new ArrayList<>(path);
                full.add(self);
                result.add(prefixed(project, full));
            }
        }
        if (result.isEmpty()) {
            result.add(prefixed(project, List.of(self)));
        }
        return truncated;
    }

    /** Document → linked fiche join, by linked kind ({@code null}: places, which belong to no project). */
    private static final Map<Kind, String> DOCUMENT_LINKS = new HashMap<>();

    static {
        DOCUMENT_LINKS.put(Kind.RECORDING_UNIT, "select l.id from Document d join d.recordingUnits l where d.id = :id order by l.id");
        DOCUMENT_LINKS.put(Kind.FIND, "select l.id from Document d join d.finds l where d.id = :id order by l.id");
        DOCUMENT_LINKS.put(Kind.CONTAINER, "select l.id from Document d join d.containers l where d.id = :id order by l.id");
        DOCUMENT_LINKS.put(Kind.PHASE, "select l.id from Document d join d.phases l where d.id = :id order by l.id");
        DOCUMENT_LINKS.put(Kind.STUDY, "select l.id from Document d join d.studies l where d.id = :id order by l.id");
        DOCUMENT_LINKS.put(null, "select l.id from Document d join d.places l where d.id = :id order by l.id");
    }

    private List<Long> documentLinks(String jpql, long id) {
        return entityManager.createQuery(jpql, Long.class).setParameter("id", id).getResultList(); // NOSONAR S2077: constants
    }

    private List<Long> placeParents(Long id) {
        return entityManager.createQuery(
                        "select p.id from SpatialUnit s join s.parents p where s.id = :id order by p.id", Long.class)
                .setParameter("id", id).getResultList();
    }

    private BreadcrumbResource placeCrumb(Long id) {
        Object[] row = entityManager.createQuery(
                        "select s.id, s.name from SpatialUnit s where s.id = :id", Object[].class)
                .setParameter("id", id)
                .getSingleResult();
        return toCrumb("place", row);
    }

    /** Result of a same-kind ancestor walk: root-first paths ending with the node itself. */
    record Chains(List<List<BreadcrumbResource>> paths, boolean truncated) {
    }

    /**
     * Every root-first chain of same-kind ancestors ending at {@code id} (itself included), parents
     * visited in the order {@code parentsOf} returns them, capped at {@link #MAX_PATHS}. A node already
     * on the current chain is skipped, so an accidental cycle ends instead of looping.
     */
    static Chains chains(long id, Function<Long, List<Long>> parentsOf, Function<Long, BreadcrumbResource> crumbOf) {
        List<List<BreadcrumbResource>> out = new ArrayList<>();
        boolean truncated = walk(id, new HashSet<>(), parentsOf, crumbOf, out);
        return new Chains(out, truncated);
    }

    /** @return true when the cap stopped the walk */
    private static boolean walk(long id, Set<Long> onChain, Function<Long, List<Long>> parentsOf,
                                Function<Long, BreadcrumbResource> crumbOf, List<List<BreadcrumbResource>> out) {
        onChain.add(id);
        BreadcrumbResource self = crumbOf.apply(id);
        List<Long> parents = parentsOf.apply(id).stream().filter(p -> !onChain.contains(p)).toList();
        boolean truncated = false;
        if (parents.isEmpty()) {
            out.add(new ArrayList<>(List.of(self)));
        } else {
            for (Long parent : parents) {
                if (out.size() >= MAX_PATHS) {
                    truncated = true;
                    break;
                }
                List<List<BreadcrumbResource>> parentChains = new ArrayList<>();
                truncated |= walk(parent, onChain, parentsOf, crumbOf, parentChains);
                for (List<BreadcrumbResource> chain : parentChains) {
                    if (out.size() >= MAX_PATHS) {
                        truncated = true;
                        break;
                    }
                    chain.add(self);
                    out.add(chain);
                }
            }
        }
        onChain.remove(id);
        return truncated;
    }

    private static List<BreadcrumbResource> prefixed(BreadcrumbResource project, List<BreadcrumbResource> path) {
        List<BreadcrumbResource> full = new ArrayList<>(path.size() + 1);
        full.add(project);
        full.addAll(path);
        return full;
    }

    private BreadcrumbResource projectCrumb(Long projectId) {
        Object[] row = entityManager.createQuery(
                        "select a.id, coalesce(a.name, a.fullIdentifier, a.identifier) from ActionUnit a where a.id = :id", Object[].class)
                .setParameter("id", projectId)
                .getSingleResult();
        return toCrumb("project", row);
    }

    private BreadcrumbResource crumb(Kind kind, Long id) {
        Object[] row = entityManager.createQuery(
                        "select e.id, " + kind.label + " from " + kind.entity + " e where e.id = :id", Object[].class) // NOSONAR S2077: constants of the Kind enum
                .setParameter("id", id)
                .getSingleResult();
        return toCrumb(kind.type, row);
    }

    private static BreadcrumbResource toCrumb(String type, Object[] row) {
        String id = String.valueOf(row[0]);
        return new BreadcrumbResource(type, id, row[1] != null ? String.valueOf(row[1]) : id);
    }

    private List<Long> recordingUnitParents(Long id) {
        return entityManager.createQuery(
                        "select p.id from RecordingUnit r join r.parents p where r.id = :id order by p.id", Long.class)
                .setParameter("id", id).getResultList();
    }

    private List<Long> findParents(Long id) {
        return entityManager.createQuery(
                        "select p.id from Specimen s join s.parents p where s.id = :id order by p.id", Long.class)
                .setParameter("id", id).getResultList();
    }

    private List<Long> containerParent(Long id) {
        Long parent = one("select c.parent.id from Container c where c.id = :id", id);
        return parent == null ? List.of() : List.of(parent);
    }

    private Long one(String jpql, long id) {
        return entityManager.createQuery(jpql, Long.class).setParameter("id", id) // NOSONAR S2077: constant queries
                .getResultList().stream().findFirst().orElse(null);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Fiche introuvable ou hors périmètre");
    }
}
