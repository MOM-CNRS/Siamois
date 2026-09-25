package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.ui.api.openapi.v1.resource.sibling.SiblingResource;
import fr.siamois.ui.api.openapi.v1.resource.sibling.SiblingsResource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitySiblingsServiceTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Object[]> query;

    @InjectMocks
    private EntitySiblingsService service;

    private static List<Object[]> rows(Object[]... rows) {
        return List.of(rows);
    }

    @Test
    void pick_returnsBothNeighbours_inTheMiddle() {
        SiblingsResource result = EntitySiblingsService.pick(EntitySiblingsService.Kind.RECORDING_UNIT,
                rows(new Object[]{1L, "UE-1"}, new Object[]{2L, "UE-2"}, new Object[]{3L, "UE-3"}), 2L);

        assertThat(result.previous()).isEqualTo(new SiblingResource("1", "UE-1", "/recording-unit/1"));
        assertThat(result.next()).isEqualTo(new SiblingResource("3", "UE-3", "/recording-unit/3"));
    }

    @Test
    void pick_loopsAtBothEnds() {
        List<Object[]> rows = rows(new Object[]{1L, "A"}, new Object[]{2L, "B"}, new Object[]{3L, "C"});

        SiblingsResource first = EntitySiblingsService.pick(EntitySiblingsService.Kind.PLACE, rows, 1L);
        SiblingsResource last = EntitySiblingsService.pick(EntitySiblingsService.Kind.PLACE, rows, 3L);

        assertThat(first.previous().id()).isEqualTo("3");
        assertThat(first.next().id()).isEqualTo("2");
        assertThat(last.next().id()).isEqualTo("1");
        assertThat(last.previous().resourceUri()).isEqualTo("/spatial-unit/2");
    }

    @Test
    void pick_noOtherEntityOrUnknownCurrent_returnsNulls() {
        SiblingsResource alone = EntitySiblingsService.pick(EntitySiblingsService.Kind.PHASE,
                rows(new Object[]{1L, "P1"}), 1L);
        SiblingsResource unknown = EntitySiblingsService.pick(EntitySiblingsService.Kind.PHASE,
                rows(new Object[]{1L, "P1"}, new Object[]{2L, "P2"}), 9L);

        assertThat(alone.previous()).isNull();
        assertThat(alone.next()).isNull();
        assertThat(unknown.previous()).isNull();
        assertThat(unknown.next()).isNull();
    }

    @Test
    void pick_fallsBackToTheIdWhenTheLabelIsNull() {
        SiblingsResource result = EntitySiblingsService.pick(EntitySiblingsService.Kind.CONTAINER,
                rows(new Object[]{1L, null}, new Object[]{2L, "C2"}), 2L);

        assertThat(result.previous().label()).isEqualTo("1");
    }

    @Test
    void findSiblings_queriesTheScopeInCreationOrder() {
        when(entityManager.createQuery(anyString(), eq(Object[].class))).thenReturn(query);
        when(query.setParameter("scope", 5L)).thenReturn(query);
        when(query.getResultList()).thenReturn(rows(new Object[]{10L, "M-10"}, new Object[]{11L, "M-11"}));

        SiblingsResource result = service.findSiblings(EntitySiblingsService.Kind.FIND, 5L, 10L);

        verify(entityManager).createQuery(
                "select e.id, e.fullIdentifier from Specimen e where e.actionUnit.id = :scope order by e.creationTime asc, e.id asc",
                Object[].class);
        assertThat(result.next().resourceUri()).isEqualTo("/specimen/11");
    }

    @Test
    void findSiblings_withoutScope_doesNotQuery() {
        SiblingsResource result = service.findSiblings(EntitySiblingsService.Kind.RECORDING_UNIT, null, 1L);

        assertThat(result.previous()).isNull();
        verify(entityManager, never()).createQuery(anyString(), eq(Object[].class));
    }
}
