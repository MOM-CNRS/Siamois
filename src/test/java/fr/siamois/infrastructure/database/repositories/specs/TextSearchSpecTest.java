package fr.siamois.infrastructure.database.repositories.specs;

import fr.siamois.domain.models.phase.Phase;
import fr.siamois.dto.FilterDTO;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TextSearchSpecTest {

    @Mock
    private Root<Phase> root;
    @Mock
    private CriteriaQuery<?> query;
    @Mock
    private CriteriaBuilder cb;

    @Test
    @SuppressWarnings("unchecked")
    void startWith_matchesTheStartOfTheLoweredAttribute() {
        Expression<String> lowered = stubLoweredIdentifier();

        TextSearchSpec.<Phase>matching("identifier", "EXEMPLE", FilterDTO.FilterType.START_WITH).toPredicate(root, query, cb);

        verify(cb).like(lowered, "exemple%");
    }

    @Test
    void contains_matchesAnywhereInTheLoweredAttribute() {
        Expression<String> lowered = stubLoweredIdentifier();

        TextSearchSpec.<Phase>matching("identifier", new FilterDTO.FilterInfo("EXEMPLE", FilterDTO.FilterType.CONTAINS))
                .toPredicate(root, query, cb);

        verify(cb).like(lowered, "%exemple%");
    }

    @Test
    void blankTextOrNoFilter_restrictsNothing() {
        assertThat(TextSearchSpec.<Phase>matching("identifier", " ", FilterDTO.FilterType.START_WITH)
                .toPredicate(root, query, cb)).isNull();
        assertThat(TextSearchSpec.<Phase>matching("identifier", null).toPredicate(root, query, cb)).isNull();
        verifyNoInteractions(cb);
    }

    /**
     * Stubs {@code lower(root.identifier)} and returns the lowered expression.
     */
    @SuppressWarnings("unchecked")
    private Expression<String> stubLoweredIdentifier() {
        Path<Object> identifier = mock(Path.class);
        Expression<String> lowered = mock(Expression.class);
        when(root.get("identifier")).thenReturn(identifier);
        when(cb.lower(any())).thenReturn(lowered);
        return lowered;
    }
}
