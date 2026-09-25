package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.services.BookmarkService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.resource.BookmarkableResource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Fills {@code bookmarked} on any {@link BookmarkableResource} — the generic counterpart of
 * {@link ProjectApiService#bookmarkedResourceUris}, for every other entity type. A bookmark row is
 * {@code (person, institution, resourceUri)}, so the lookup is scoped to the institution the resources
 * belong to: the list's own organization, or the entity's own institution for a detail.
 */
@Service
@RequiredArgsConstructor
public class ResourceBookmarkService {

    private final BookmarkService bookmarkService;

    /** One query for a whole list page. Resources without a resourceUri stay {@code bookmarked=false}. */
    public void markBookmarked(PersonDTO person, InstitutionDTO institution,
                               Collection<? extends BookmarkableResource> resources, String lang) {
        if (person == null || institution == null || resources == null || resources.isEmpty()) {
            return;
        }
        List<String> uris = resources.stream()
                .map(BookmarkableResource::getResourceUri)
                .filter(Objects::nonNull)
                .toList();
        Set<String> bookmarked = bookmarkService.findBookmarkedResourceUris(new UserInfo(institution, person, lang), uris);
        for (BookmarkableResource resource : resources) {
            resource.setBookmarked(resource.getResourceUri() != null && bookmarked.contains(resource.getResourceUri()));
        }
    }

    /** Detail variant, for callers that already built the {@link UserInfo} for their permission check. */
    public void markBookmarked(UserInfo userInfo, BookmarkableResource resource) {
        if (userInfo != null) {
            markBookmarked(userInfo.getUser(), userInfo.getInstitution(), resource, userInfo.getLang());
        }
    }

    public void markBookmarked(PersonDTO person, InstitutionDTO institution, BookmarkableResource resource, String lang) {
        if (resource != null) {
            markBookmarked(person, institution, List.of(resource), lang);
        }
    }
}
