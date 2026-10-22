package fr.siamois.dto;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FilterDTO#hasUserFilters()} is what makes a list "narrowed" for the tree view: only then are
 * the roots restricted to the ancestors of the matches and the rows flagged as match or context.
 */
class FilterDTOTest {

    @Test
    void anEmptyFilterIsNotNarrowed() {
        assertFalse(new FilterDTO(true).hasUserFilters());
    }

    @Test
    void aNamedFilterNarrows() {
        FilterDTO filters = new FilterDTO(true);
        filters.add("fullIdentifier", "abc", FilterDTO.FilterType.CONTAINS);

        assertTrue(filters.hasUserFilters());
    }

    @Test
    void aScopeFilterIsTheFixedContextNotAUserFilter() {
        FilterDTO filters = new FilterDTO(true);
        filters.addScopeFilter("actionUnit", List.of(3L), FilterDTO.FilterType.EQUAL);

        assertFalse(filters.hasUserFilters());
    }

    @Test
    void aFilterOnAFormFieldNarrowsEvenThoughItIsNotANamedFilter() {
        FilterDTO filters = new FilterDTO(true);
        filters.setFieldQuery(new FieldQuery(Specification.where(null), false, true));

        assertTrue(filters.hasUserFilters());
    }

    @Test
    void aSortOnAFormFieldAloneDoesNotNarrow() {
        FilterDTO filters = new FilterDTO(true);
        filters.setFieldQuery(new FieldQuery(Specification.where(null), true, false));

        assertFalse(filters.hasUserFilters());
    }
}
