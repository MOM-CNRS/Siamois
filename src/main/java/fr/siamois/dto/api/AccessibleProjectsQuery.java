package fr.siamois.dto.api;

import fr.siamois.dto.FieldQuery;
import fr.siamois.dto.FilterDTO;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectListFilter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * What a page of the accessible projects asks for, beyond who asks: the search and how it matches,
 * the page, the per-column filters and the sort/filters on form fields.
 *
 * @param search                  the searched text, null or blank for none
 * @param searchType              how {@code search} matches: {@link FilterDTO.FilterType#START_WITH} (a
 *                                project picker) or {@link FilterDTO.FilterType#CONTAINS} (the list's search box)
 * @param pageable                the page, and its sort
 * @param recordingUnitCountOrder when non-null, order by the number of attached recording units instead
 *                                of by {@code pageable}'s own sort — {@code recordingUnitCount} is not a
 *                                JPA-mapped path, so the caller must have stripped it from {@code pageable}
 * @param filter                  per-column filters, {@link ProjectListFilter#EMPTY} for none
 * @param fieldQuery              sort/filters on form fields; when it orders, {@code pageable} must be unsorted
 */
public record AccessibleProjectsQuery(
        @Nullable String search,
        @NonNull FilterDTO.FilterType searchType,
        @NonNull Pageable pageable,
        @Nullable Sort.Direction recordingUnitCountOrder,
        @NonNull ProjectListFilter filter,
        @NonNull FieldQuery fieldQuery
) {

    /**
     * A page of the projects containing {@code search}, with no other filter nor special sort.
     */
    @NonNull
    public static AccessibleProjectsQuery of(@Nullable String search, @NonNull Pageable pageable) {
        return new AccessibleProjectsQuery(search, FilterDTO.FilterType.CONTAINS, pageable, null,
                ProjectListFilter.EMPTY, FieldQuery.NONE);
    }

    /**
     * The same query, {@code search} matching the way {@code searchType} says.
     */
    @NonNull
    public AccessibleProjectsQuery withSearchType(@NonNull FilterDTO.FilterType searchType) {
        return new AccessibleProjectsQuery(search, searchType, pageable, recordingUnitCountOrder, filter, fieldQuery);
    }

    /**
     * The same query, ordered by the number of recording units.
     */
    @NonNull
    public AccessibleProjectsQuery withRecordingUnitCountOrder(@Nullable Sort.Direction order) {
        return new AccessibleProjectsQuery(search, searchType, pageable, order, filter, fieldQuery);
    }

    /**
     * The same query, with these per-column filters.
     */
    @NonNull
    public AccessibleProjectsQuery withFilter(@NonNull ProjectListFilter filter) {
        return new AccessibleProjectsQuery(search, searchType, pageable, recordingUnitCountOrder, filter, fieldQuery);
    }

    /**
     * The same query, with these sort/filters on form fields.
     */
    @NonNull
    public AccessibleProjectsQuery withFieldQuery(@NonNull FieldQuery fieldQuery) {
        return new AccessibleProjectsQuery(search, searchType, pageable, recordingUnitCountOrder, filter, fieldQuery);
    }
}
