package fr.siamois.ui.api.openapi.v1.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityProjectResolverTest {

    @Mock
    private EntityManager entityManager;
    @Mock
    private TypedQuery<Object[]> query;
    @InjectMocks
    private EntityProjectResolver resolver;

    @Test
    @SuppressWarnings("unchecked")
    void resolve_givesTheProjectOfAProjectOwnedFicheOnly() {
        when(entityManager.createQuery(anyString(), eq(Object[].class))).thenReturn(query);
        when(query.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.<Object[]>of(new Object[]{12L, 3L, "Cuzco"}));

        Map<String, EntityProjectResolver.ProjectRef> result = resolver.resolve(
                List.of("/recording-unit/12", "/action-unit/3", "/spatial-unit/5", "/recording-unit", "/recording-unit/x"),
                Set.of(7L));

        assertThat(result).containsOnlyKeys("/recording-unit/12");
        assertThat(result.get("/recording-unit/12")).isEqualTo(new EntityProjectResolver.ProjectRef(3L, "Cuzco"));
    }

    @Test
    void resolve_withoutAccessibleInstitutionNeverQueries() {
        assertThat(resolver.resolve(List.of("/recording-unit/12"), Set.of())).isEmpty();
        verify(entityManager, never()).createQuery(anyString(), eq(Object[].class));
    }

    @Test
    void resolve_ignoresNothingToResolve() {
        assertThat(resolver.resolve(null, Set.of(7L))).isEmpty();
        assertThat(resolver.resolve(List.of(), Set.of(7L))).isEmpty();
        assertThat(resolver.resolve(List.of("/recording-unit/12"), null)).isEmpty();
        assertThat(resolver.resolve(java.util.Collections.singletonList(null), Set.of(7L))).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void names_readsTheNameOfEachProject() {
        when(entityManager.createQuery(anyString(), eq(Object[].class))).thenReturn(query);
        when(query.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.<Object[]>of(new Object[]{3L, "Cuzco"}, new Object[]{4L, "Chartres"}));

        assertThat(resolver.names(List.of(3L, 4L))).containsEntry(3L, "Cuzco").containsEntry(4L, "Chartres");
    }

    @Test
    void names_withoutProjectsNeverQueries() {
        assertThat(resolver.names(null)).isEmpty();
        assertThat(resolver.names(List.of())).isEmpty();
        verify(entityManager, never()).createQuery(anyString(), eq(Object[].class));
    }

    @Test
    void labelWithProject_putsTheProjectAfterTheNameAndEscapesBoth() {
        assertThat(EntityProjectResolver.labelWithProject("US <1>", "Cuzco & Co"))
                .isEqualTo("<span class=\"sia-entry-title\">US &lt;1&gt;</span>"
                        + "<span class=\"sia-project-name\">Cuzco &amp; Co</span>");
    }

    @Test
    void labelWithProject_withoutProjectIsTheNameOnly() {
        assertThat(EntityProjectResolver.labelWithProject("US 1", null)).isEqualTo("<span class=\"sia-entry-title\">US 1</span>");
        assertThat(EntityProjectResolver.labelWithProject(null, " ")).isEqualTo("<span class=\"sia-entry-title\"></span>");
    }
}
