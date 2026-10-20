package fr.siamois.domain.services.actionunit;

import fr.siamois.dto.FieldQuery;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectListFilter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;

import java.util.Set;

/**
 * A request for the tree view of the accessible projects: who is asking and where, and what is asked.
 *
 * @param personId               who is asking (the projects they can see are the context of the tree)
 * @param accessibleInstitutionIds the organizations they can reach
 * @param organizationId         the one organization asked for, or null for all they can reach
 * @param spatialContextId       the tree of the projects of one place (its "Projets" tab), or null: the place is
 *                               part of the context, not a filter — a project whose parent is in another place
 *                               is a root there
 * @param parentId               the project whose accessible children are wanted, or null for the roots
 * @param selection              the search, filters and order
 */
public record ProjectTreeQuery(Long personId, Set<Long> accessibleInstitutionIds, @Nullable Long organizationId,
                               @Nullable Long spatialContextId, @Nullable Long parentId, Selection selection) {

    /**
     * @param search                  free text, or null
     * @param filter                  the per-column filters (without the place of {@code spatialContextId})
     * @param fieldQuery              the sort and filters on form fields
     * @param pageable                unsorted by the count sort and by a field sort (their orderings are specifications)
     * @param recordingUnitCountOrder the recording-unit-count sort, carried by a specification, or null
     */
    public record Selection(@Nullable String search, ProjectListFilter filter, FieldQuery fieldQuery, Pageable pageable,
                            @Nullable Sort.Direction recordingUnitCountOrder) {
    }
}
