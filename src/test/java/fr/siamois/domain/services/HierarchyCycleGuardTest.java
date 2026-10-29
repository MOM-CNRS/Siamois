package fr.siamois.domain.services;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HierarchyCycleGuardTest {

    @Mock
    private EntityManager entityManager;
    @Mock
    private Query query;

    @InjectMocks
    private HierarchyCycleGuard guard;

    private void childFoundAmongAncestors(long count) {
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(count);
    }

    @Test
    void requireNoCycle_throws_whenChildIsAnAncestorOfParent() {
        childFoundAmongAncestors(1L);

        assertThrows(IllegalStateException.class,
                () -> guard.requireNoCycle(HierarchyCycleGuard.Kind.RECORDING_UNIT, 2L, 1L));
    }

    @Test
    void requireNoCycle_passes_whenChildIsNotAnAncestor() {
        childFoundAmongAncestors(0L);

        assertDoesNotThrow(() -> guard.requireNoCycle(HierarchyCycleGuard.Kind.SPATIAL_UNIT, 2L, 1L));
    }

    @Test
    void requireNoCycle_throwsOnSelfLink_withoutQuerying() {
        assertThrows(IllegalStateException.class,
                () -> guard.requireNoCycle(HierarchyCycleGuard.Kind.SPECIMEN, 3L, 3L));

        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    void requireNoCycleThrough_checksEachNewLink() {
        childFoundAmongAncestors(0L);

        assertDoesNotThrow(() -> guard.requireNoCycleThrough(
                HierarchyCycleGuard.Kind.CONTAINER, 5L, Set.of(1L), Set.of(2L)));

        // parent->self, parent->child, self->child
        verify(entityManager, org.mockito.Mockito.times(3)).createNativeQuery(anyString());
    }

    @Test
    void requireNoCycleThrough_newEntity_checksParentAgainstChildOnly() {
        childFoundAmongAncestors(1L);

        assertThrows(IllegalStateException.class, () -> guard.requireNoCycleThrough(
                HierarchyCycleGuard.Kind.SPATIAL_UNIT, null, Set.of(1L), Set.of(2L)));
        verify(entityManager, org.mockito.Mockito.times(1)).createNativeQuery(anyString());
    }
}
