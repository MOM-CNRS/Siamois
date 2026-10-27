package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.ui.api.openapi.v1.resource.breadcrumb.BreadcrumbResource;
import fr.siamois.ui.api.openapi.v1.resource.breadcrumb.BreadcrumbsResource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class EntityBreadcrumbsServiceTest {

    private static BreadcrumbResource crumb(Long id) {
        return new BreadcrumbResource("recordingUnit", String.valueOf(id), "UE" + id);
    }

    private static List<String> ids(List<BreadcrumbResource> path) {
        return path.stream().map(BreadcrumbResource::id).toList();
    }

    @Test
    void chains_withoutParent_isJustTheNode() {
        var result = EntityBreadcrumbsService.chains(1, id -> List.of(), EntityBreadcrumbsServiceTest::crumb);

        assertThat(result.paths()).hasSize(1);
        assertThat(ids(result.paths().get(0))).containsExactly("1");
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void chains_diamond_returnsOnePathPerParentInOrder() {
        // 4 -> parents 2,3 ; 2 and 3 -> parent 1
        Map<Long, List<Long>> parents = Map.of(4L, List.of(2L, 3L), 2L, List.of(1L), 3L, List.of(1L));

        var result = EntityBreadcrumbsService.chains(4, id -> parents.getOrDefault(id, List.of()),
                EntityBreadcrumbsServiceTest::crumb);

        assertThat(result.paths().stream().map(EntityBreadcrumbsServiceTest::ids).toList())
                .containsExactly(List.of("1", "2", "4"), List.of("1", "3", "4"));
    }

    @Test
    void chains_cycle_terminates() {
        Map<Long, List<Long>> parents = Map.of(1L, List.of(2L), 2L, List.of(1L));

        var result = EntityBreadcrumbsService.chains(1, id -> parents.getOrDefault(id, List.of()),
                EntityBreadcrumbsServiceTest::crumb);

        assertThat(result.paths().stream().map(EntityBreadcrumbsServiceTest::ids).toList())
                .containsExactly(List.of("2", "1"));
    }

    @Test
    void chains_manyParents_isCapped() {
        List<Long> many = java.util.stream.LongStream.rangeClosed(100, 200).boxed().toList();

        var result = EntityBreadcrumbsService.chains(1, id -> id == 1L ? many : List.of(),
                EntityBreadcrumbsServiceTest::crumb);

        assertThat(result.paths()).hasSize(EntityBreadcrumbsService.MAX_PATHS);
        assertThat(result.truncated()).isTrue();
    }

    // ---- findBreadcrumbs, against a fake EntityManager answering from an in-memory graph ----

    private static final Set<Long> ACCESSIBLE = Set.of(10L);

    private final EntityBreadcrumbsService service = new EntityBreadcrumbsService();
    private final Map<Long, List<Long>> ruParents = new HashMap<>();
    private final Map<Long, List<Long>> findParents = new HashMap<>();
    private final Map<Long, Long> containerParent = new HashMap<>();
    private final Map<Long, Long> findRu = new HashMap<>();
    private final Map<Long, List<Long>> placeParents = new HashMap<>();
    // "recordingUnits", "places"... -> document id -> linked ids
    private final Map<String, Map<Long, List<Long>>> documentLinks = new HashMap<>();
    private Long projectId = 1L;
    private Long institutionId = 10L;
    private boolean entityExists = true;
    private String projectName = "Fouille";
    private Long unlabelledId;

    @BeforeEach
    void wireFakeEntityManager() {
        EntityManager em = mock(EntityManager.class);
        lenient().when(em.createQuery(anyString(), any(Class.class))).thenAnswer(invocation -> {
            String jpql = invocation.getArgument(0);
            TypedQuery<?> query = mock(TypedQuery.class);
            long[] id = new long[1];
            lenient().when(query.setParameter(eq("id"), any())).thenAnswer(inv -> {
                id[0] = (Long) inv.getArgument(1);
                return query;
            });
            lenient().when(query.getResultList()).thenAnswer(inv -> answer(jpql, id[0]));
            lenient().when(query.getSingleResult()).thenAnswer(inv -> answer(jpql, id[0]).get(0));
            return query;
        });
        ReflectionTestUtils.setField(service, "entityManager", em);
    }

    private List<?> answer(String jpql, long id) {
        if (jpql.startsWith("select e.actionUnit.id, e.actionUnit.createdByInstitution.id")) {
            return entityExists ? List.<Object[]>of(new Object[]{projectId, institutionId}) : List.of();
        }
        if (jpql.contains("from ActionUnit a")) {
            return List.<Object[]>of(new Object[]{id, projectName});
        }
        if (jpql.startsWith("select l.id from Document d join d.")) {
            String link = jpql.substring("select l.id from Document d join d.".length(), jpql.indexOf(" l "));
            return documentLinks.getOrDefault(link, Map.of()).getOrDefault(id, List.of());
        }
        if (jpql.contains("from SpatialUnit s join s.parents")) {
            return placeParents.getOrDefault(id, List.of());
        }
        if (jpql.startsWith("select e.createdByInstitution.id from SpatialUnit")) {
            return entityExists ? List.of(institutionId) : List.of();
        }
        if (jpql.contains("from SpatialUnit e")) {
            return List.<Object[]>of(new Object[]{id, "L" + id});
        }
        if (jpql.startsWith("select e.recordingUnit.id from Specimen")) {
            return findRu.containsKey(id) ? List.of(findRu.get(id)) : List.of();
        }
        if (jpql.contains("join r.parents")) {
            return ruParents.getOrDefault(id, List.of());
        }
        if (jpql.contains("join s.parents")) {
            return findParents.getOrDefault(id, List.of());
        }
        if (jpql.startsWith("select c.parent.id")) {
            return containerParent.containsKey(id) ? List.of(containerParent.get(id)) : List.of();
        }
        String prefix = jpql.contains("from RecordingUnit e") ? "UE" : jpql.contains("from Specimen e") ? "M"
                : jpql.contains("from Container e") ? "C" : jpql.contains("from Phase e") ? "PH"
                : jpql.contains("from Document e") ? "D" : "S";
        return List.<Object[]>of(new Object[]{id, unlabelledId != null && unlabelledId == id ? null : prefix + id});
    }

    private static List<List<String>> labels(BreadcrumbsResource resource) {
        return resource.paths().stream().map(p -> p.stream().map(BreadcrumbResource::label).toList()).toList();
    }

    @Test
    void ofCollection_mapsEveryCollectionAndRejectsOthers() {
        assertThat(EntityBreadcrumbsService.Kind.ofCollection("recording-units")).isEqualTo(EntityBreadcrumbsService.Kind.RECORDING_UNIT);
        assertThat(EntityBreadcrumbsService.Kind.ofCollection("finds")).isEqualTo(EntityBreadcrumbsService.Kind.FIND);
        assertThat(EntityBreadcrumbsService.Kind.ofCollection("containers")).isEqualTo(EntityBreadcrumbsService.Kind.CONTAINER);
        assertThat(EntityBreadcrumbsService.Kind.ofCollection("phases")).isEqualTo(EntityBreadcrumbsService.Kind.PHASE);
        assertThat(EntityBreadcrumbsService.Kind.ofCollection("documents")).isEqualTo(EntityBreadcrumbsService.Kind.DOCUMENT);
        assertThat(EntityBreadcrumbsService.Kind.ofCollection("studies")).isEqualTo(EntityBreadcrumbsService.Kind.STUDY);
        assertThat(EntityBreadcrumbsService.Kind.ofCollection("places")).isEqualTo(EntityBreadcrumbsService.Kind.PLACE);
        assertThat(EntityBreadcrumbsService.Kind.ofCollection("nope")).isNull();
    }

    @Test
    void findBreadcrumbs_unknownFiche_isNotFound() {
        entityExists = false;

        assertThatThrownBy(() -> service.findBreadcrumbs(EntityBreadcrumbsService.Kind.RECORDING_UNIT, 5, ACCESSIBLE))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void findBreadcrumbs_ficheWithoutProject_isNotFound() {
        projectId = null;

        assertThatThrownBy(() -> service.findBreadcrumbs(EntityBreadcrumbsService.Kind.RECORDING_UNIT, 5, ACCESSIBLE))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void findBreadcrumbs_outOfScopeInstitution_isNotFound() {
        Set<Long> otherInstitutions = Set.of(99L);
        assertThatThrownBy(() -> service.findBreadcrumbs(EntityBreadcrumbsService.Kind.RECORDING_UNIT, 5, otherInstitutions))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.findBreadcrumbs(EntityBreadcrumbsService.Kind.RECORDING_UNIT, 5, null))
                .isInstanceOf(ResponseStatusException.class);
        institutionId = null;
        assertThatThrownBy(() -> service.findBreadcrumbs(EntityBreadcrumbsService.Kind.RECORDING_UNIT, 5, ACCESSIBLE))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void findBreadcrumbs_recordingUnit_listsOnePathPerParentUnderTheProject() {
        ruParents.put(5L, List.of(2L, 3L));
        ruParents.put(2L, List.of(1L));

        BreadcrumbsResource result = service.findBreadcrumbs(EntityBreadcrumbsService.Kind.RECORDING_UNIT, 5, ACCESSIBLE);

        assertThat(labels(result)).containsExactly(
                List.of("Fouille", "UE1", "UE2", "UE5"),
                List.of("Fouille", "UE3", "UE5"));
        assertThat(result.truncated()).isFalse();
        assertThat(result.paths().get(0).get(0).type()).isEqualTo("project");
        assertThat(result.paths().get(0).get(1).type()).isEqualTo("recordingUnit");
    }

    @Test
    void findBreadcrumbs_projectWithoutName_fallsBackToItsId() {
        projectName = null;

        BreadcrumbsResource result = service.findBreadcrumbs(EntityBreadcrumbsService.Kind.PHASE, 8, ACCESSIBLE);

        assertThat(labels(result)).containsExactly(List.of("1", "PH8"));
    }

    @Test
    void findBreadcrumbs_find_putsItsRecordingUnitPathsBeforeItsOwnParents() {
        findRu.put(9L, 5L);
        ruParents.put(5L, List.of(2L));
        findParents.put(9L, List.of(7L));

        BreadcrumbsResource result = service.findBreadcrumbs(EntityBreadcrumbsService.Kind.FIND, 9, ACCESSIBLE);

        assertThat(labels(result)).containsExactly(List.of("Fouille", "UE2", "UE5", "M7", "M9"));
    }

    @Test
    void findBreadcrumbs_findWithoutRecordingUnit_goesStraightFromTheProject() {
        BreadcrumbsResource result = service.findBreadcrumbs(EntityBreadcrumbsService.Kind.FIND, 9, ACCESSIBLE);

        assertThat(labels(result)).containsExactly(List.of("Fouille", "M9"));
    }

    @Test
    void findBreadcrumbs_findPathsAreCappedWhenTheCrossProductIsTooLarge() {
        findRu.put(9L, 5L);
        ruParents.put(5L, LongStream.rangeClosed(100, 107).boxed().toList());
        findParents.put(9L, LongStream.rangeClosed(200, 207).boxed().toList());

        BreadcrumbsResource result = service.findBreadcrumbs(EntityBreadcrumbsService.Kind.FIND, 9, ACCESSIBLE);

        assertThat(result.paths()).hasSize(EntityBreadcrumbsService.MAX_PATHS);
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void findBreadcrumbs_container_followsItsParentChain() {
        containerParent.put(4L, 3L);
        containerParent.put(3L, 2L);

        BreadcrumbsResource result = service.findBreadcrumbs(EntityBreadcrumbsService.Kind.CONTAINER, 4, ACCESSIBLE);

        assertThat(labels(result)).containsExactly(List.of("Fouille", "C2", "C3", "C4"));
    }

    @Test
    void findBreadcrumbs_projectOwnedFiches_sitDirectlyUnderTheProject() {
        assertThat(labels(service.findBreadcrumbs(EntityBreadcrumbsService.Kind.PHASE, 8, ACCESSIBLE)))
                .containsExactly(List.of("Fouille", "PH8"));
        assertThat(labels(service.findBreadcrumbs(EntityBreadcrumbsService.Kind.DOCUMENT, 6, ACCESSIBLE)))
                .containsExactly(List.of("Fouille", "D6"));
        assertThat(labels(service.findBreadcrumbs(EntityBreadcrumbsService.Kind.STUDY, 3, ACCESSIBLE)))
                .containsExactly(List.of("Fouille", "S3"));
    }

    @Test
    void findBreadcrumbs_crumbWithoutLabel_usesItsId() {
        unlabelledId = 8L;

        assertThat(labels(service.findBreadcrumbs(EntityBreadcrumbsService.Kind.PHASE, 8, ACCESSIBLE)))
                .containsExactly(List.of("Fouille", "8"));
    }

    @Test
    void chains_capIsAppliedWhileMergingAParentsOwnChains() {
        Map<Long, List<Long>> parents = new HashMap<>();
        parents.put(1L, List.of(2L, 3L));
        parents.put(2L, LongStream.rangeClosed(100, 129).boxed().toList());
        parents.put(3L, LongStream.rangeClosed(200, 229).boxed().toList());

        var result = EntityBreadcrumbsService.chains(1, id -> parents.getOrDefault(id, List.of()),
                EntityBreadcrumbsServiceTest::crumb);

        assertThat(result.paths()).hasSize(EntityBreadcrumbsService.MAX_PATHS);
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void findBreadcrumbs_document_hangsUnderEachLinkedFicheAndItsHierarchy() {
        ruParents.put(5L, List.of(2L));
        documentLinks.put("recordingUnits", Map.of(6L, List.of(5L)));
        documentLinks.put("phases", Map.of(6L, List.of(8L)));
        documentLinks.put("places", Map.of(6L, List.of(30L)));
        placeParents.put(30L, List.of(20L));

        assertThat(labels(service.findBreadcrumbs(EntityBreadcrumbsService.Kind.DOCUMENT, 6, ACCESSIBLE)))
                .containsExactly(
                        List.of("Fouille", "UE2", "UE5", "D6"),
                        List.of("Fouille", "PH8", "D6"),
                        List.of("Fouille", "L20", "L30", "D6"));
    }

    @Test
    void findBreadcrumbs_via_putsThePathsThroughThatFicheFirst() {
        documentLinks.put("recordingUnits", Map.of(6L, List.of(5L)));
        documentLinks.put("phases", Map.of(6L, List.of(8L)));

        BreadcrumbsResource result = service.findBreadcrumbs(
                EntityBreadcrumbsService.Kind.DOCUMENT, 6, ACCESSIBLE, "phase:8");

        assertThat(labels(result)).containsExactly(List.of("Fouille", "PH8", "D6"), List.of("Fouille", "UE5", "D6"));
    }

    @Test
    void findBreadcrumbs_viaMalformedOrUnmatched_keepsTheDefaultOrder() {
        documentLinks.put("recordingUnits", Map.of(6L, List.of(5L)));
        documentLinks.put("phases", Map.of(6L, List.of(8L)));
        List<List<String>> expected = List.of(List.of("Fouille", "UE5", "D6"), List.of("Fouille", "PH8", "D6"));

        for (String via : new String[]{"nonsense", ":3", "phase:", "study:99"}) {
            assertThat(labels(service.findBreadcrumbs(EntityBreadcrumbsService.Kind.DOCUMENT, 6, ACCESSIBLE, via)))
                    .containsExactlyElementsOf(expected);
        }
    }

    @Test
    void findBreadcrumbs_place_followsItsHierarchyWithoutAProject() {
        placeParents.put(30L, List.of(20L, 21L));
        placeParents.put(20L, List.of(10L));

        assertThat(labels(service.findBreadcrumbs(EntityBreadcrumbsService.Kind.PLACE, 30, ACCESSIBLE)))
                .containsExactly(List.of("L10", "L20", "L30"), List.of("L21", "L30"));
    }

    @Test
    void findBreadcrumbs_place_outOfScopeOrUnknown_isNotFound() {
        Set<Long> otherInstitutions = Set.of(99L);
        assertThatThrownBy(() -> service.findBreadcrumbs(EntityBreadcrumbsService.Kind.PLACE, 30, otherInstitutions))
                .isInstanceOf(ResponseStatusException.class);
        entityExists = false;
        assertThatThrownBy(() -> service.findBreadcrumbs(EntityBreadcrumbsService.Kind.PLACE, 30, ACCESSIBLE))
                .isInstanceOf(ResponseStatusException.class);
    }
}
