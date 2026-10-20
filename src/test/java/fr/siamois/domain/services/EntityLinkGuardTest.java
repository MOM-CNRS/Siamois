package fr.siamois.domain.services;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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
}
