package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.ui.api.openapi.v1.resource.breadcrumb.BreadcrumbResource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
}
