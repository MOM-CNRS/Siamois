package fr.siamois.domain.services;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntityLinkGuardTest {

    @Mock
    private EntityManager entityManager;
    @Mock
    private Query query;

    @InjectMocks
    private EntityLinkGuard guard;

    private void everyLinkCount(long count) {
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter("id", 7L)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(count);
    }

    @Test
    void noLinks_returnsEmpty_forEveryKind() {
        everyLinkCount(0L);
        for (EntityLinkGuard.Kind kind : EntityLinkGuard.Kind.values()) {
            assertTrue(guard.blockingLinks(kind, 7L).isEmpty(), kind.name());
        }
    }

    @Test
    void links_areListedByLabel() {
        everyLinkCount(2L);

        List<String> links = guard.blockingLinks(EntityLinkGuard.Kind.PHASE, 7L);

        assertEquals(List.of("des unités d'enregistrement", "des mobiliers", "des documents"), links);
    }

    @Test
    void requireNoLinks_throwsWithLabelAndAdvice_whenLinked() {
        everyLinkCount(1L);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard.requireNoLinks(EntityLinkGuard.Kind.STUDY, 7L, "l'étude"));

        assertTrue(ex.getMessage().contains("l'étude"));
        assertTrue(ex.getMessage().contains("Retirez d'abord"));
    }

    @Test
    void requireNoLinks_passes_whenUnlinked() {
        everyLinkCount(0L);

        assertDoesNotThrow(() -> guard.requireNoLinks(EntityLinkGuard.Kind.CONTAINER, 7L, "le contenant"));
    }

    /**
     * The message names the entity and enumerates its links.
     */
    @Test
    void requireNoLinks_namesEveryLinkInOneSentence() {
        everyLinkCount(1L);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard.requireNoLinks(EntityLinkGuard.Kind.PHASE, 7L, "la phase"));

        assertEquals("Impossible de supprimer la phase, qui a encore des liens avec des unités d'enregistrement, "
                + "des mobiliers et des documents. Retirez d'abord ces relations.", ex.getMessage());
    }

    /**
     * A link to an entity of the same kind deleted together is left out of the count.
     */
    @Test
    void alongside_leavesOutTheLinksToEntitiesDeletedTogether() {
        Query sameKind = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(entityManager.createNativeQuery(contains("IN (:alongside)"))).thenReturn(sameKind);
        when(query.setParameter("id", 7L)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(0L);
        when(sameKind.setParameter("id", 7L)).thenReturn(sameKind);
        when(sameKind.setParameter("alongside", Set.of(7L, 8L))).thenReturn(sameKind);
        when(sameKind.getSingleResult()).thenReturn(0L);

        assertDoesNotThrow(() -> guard.requireNoLinks(EntityLinkGuard.Kind.RECORDING_UNIT, 7L,
                "l'unité d'enregistrement", Set.of(7L, 8L)));

        // hierarchy (parents, children) and stratigraphy
        verify(entityManager, times(3)).createNativeQuery(contains("AND NOT ("));
        verify(query, never()).setParameter(eq("alongside"), org.mockito.ArgumentMatchers.<Object>any());
    }

    /**
     * Links to other entities still block, and the message says they lie outside the selection.
     */
    @Test
    void alongside_stillBlocksOnOtherLinks_andSaysSo() {
        everyLinkCount(1L);
        lenient().when(query.setParameter(eq("alongside"), org.mockito.ArgumentMatchers.<Object>any())).thenReturn(query);

        Set<Long> ids = Set.of(7L, 8L);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard.requireNoLinks(EntityLinkGuard.Kind.DOCUMENT, 7L, "le document", ids));

        assertTrue(ex.getMessage().contains("en dehors de la sélection"));
    }
}
