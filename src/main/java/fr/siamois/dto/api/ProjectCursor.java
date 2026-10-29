package fr.siamois.dto.api;

import org.springframework.data.domain.Sort;

/**
 * Where a project stands in a list order, to find its neighbours: the order's field and direction,
 * and the project's own value of that field and id.
 *
 * @param sortField     a real, non-null JPA path — {@code recordingUnitCount} and the other
 *                      Specification-only synthetic sorts have no cursor
 * @param sortDirection the direction of the list order on {@code sortField}
 * @param currentValue  the project's own value of {@code sortField}
 * @param currentId     the project's id, which breaks ties
 */
public record ProjectCursor(
        String sortField,
        Sort.Direction sortDirection,
        Comparable<?> currentValue,
        Long currentId
) {
}
