package fr.siamois.domain.services;

import fr.siamois.domain.models.Bookmark;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.dto.entity.BookmarkDTO;
import fr.siamois.infrastructure.database.repositories.BookmarkRepository;
import fr.siamois.mapper.BookmarkMapper;
import fr.siamois.mapper.InstitutionMapper;
import fr.siamois.mapper.PersonMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing bookmarks.
 * This service provides methods to find, save, and delete bookmarks for a user.
 */
@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final PersonMapper personMapper;
    private final InstitutionMapper institutionMapper;
    private final BookmarkMapper bookmarkMapper;


    /**
     * Finds a paginated slice of bookmarks converted to DTOs for a user.
     *
     * @param userInfo the user information containing the user and institution
     * @param pageable pagination and sorting configuration rules
     * @return a Page wrapper of BookmarkDTOs
     */
    @Transactional(readOnly = true)
    public Page<BookmarkDTO> findAll(UserInfo userInfo, Pageable pageable) {
        Person person = personMapper.invertConvert(userInfo.getUser());
        Institution institution = institutionMapper.invertConvert(userInfo.getInstitution());

        // 1. Fetch the paginated page
        Page<Bookmark> entitiesPage = bookmarkRepository.findByPersonAndInstitution(person, institution, pageable);

        // 2. Map the entities to DTOs utilizing the MapStruct reference
        return entitiesPage.map(bookmarkMapper::toDto);
    }


    @Transactional
    public BookmarkDTO update(BookmarkDTO bookmarkDTO) {
        return bookmarkMapper.toDto(bookmarkRepository.save(bookmarkMapper.invertConvert(bookmarkDTO)));
    }


    /**
     * Deletes a bookmark for a user based on the provided panel.
     *
     * @param userInfo the user information containing the user and institution
     * @param resourceUri    the resource URI to delete
     */
    @Transactional
    public void delete(UserInfo userInfo, String resourceUri) {
        Person person = personMapper.invertConvert(userInfo.getUser());
        Institution institution = institutionMapper.invertConvert(userInfo.getInstitution());

        bookmarkRepository.deleteBookmarkByPersonAndInstitutionAndResourceUri(
                person,
                institution,
                resourceUri
        );
    }

    /**
     * Saves a bookmark for a user with the specified resource URI and title code or title.
     *
     * @param userInfo         the user information containing the user and institution
     * @param ressourceUri     the URI of the resource to bookmark
     * @param titleCodeOrTitle The title code if the panel has a generic name, or the title of the resource
     * @return the saved bookmark
     */
    @Transactional
    public Bookmark save(UserInfo userInfo, String ressourceUri, String titleCodeOrTitle) {
        Person person = personMapper.invertConvert(userInfo.getUser());
        Institution institution = institutionMapper.invertConvert(userInfo.getInstitution());

        Bookmark bookmark = new Bookmark();
        bookmark.setPerson(person);
        bookmark.setInstitution(institution);
        bookmark.setResourceUri(ressourceUri);
        bookmark.setTitleCode(titleCodeOrTitle);
        return bookmarkRepository.save(bookmark);
    }

    /**
     * Checks if a resource is bookmarked by a user.
     *
     * @param userInfo     the user information containing the user and institution
     * @param ressourceUri the URI of the resource to check
     * @return true if the resource is bookmarked by the user, false otherwise
     */
    @Transactional(readOnly = true)
    public Boolean isRessourceBookmarkedByUser(UserInfo userInfo, String ressourceUri) {
        Person person = personMapper.invertConvert(userInfo.getUser());
        Institution institution = institutionMapper.invertConvert(userInfo.getInstitution());

        return bookmarkRepository.countBookmarkByPersonAndInstitutionAndResourceUri(
                person,
                institution,
                ressourceUri) > 0;
    }

    /**
     * Bulk version of {@link #isRessourceBookmarkedByUser} : which of the given resource URIs are
     * bookmarked by the user, in one query instead of one per URI — used for a whole list page (plan
     * §5/§6) rather than a row at a time. Institution-scoped like every other method on this service
     * (a {@code Bookmark} is {@code (person, institution, resourceUri)}, not just
     * {@code (person, resourceUri)}) — a caller whose page spans several institutions calls this once
     * per institution, same principle as {@code ProfilePermissionService}'s per-institution grouping.
     *
     * @param userInfo     the user information containing the user and institution
     * @param resourceUris the resource URIs to check
     * @return the subset of {@code resourceUris} bookmarked by the user in this institution
     */
    @Transactional(readOnly = true)
    public Set<String> findBookmarkedResourceUris(UserInfo userInfo, Collection<String> resourceUris) {
        if (resourceUris == null || resourceUris.isEmpty()) {
            return Set.of();
        }
        Person person = personMapper.invertConvert(userInfo.getUser());
        Institution institution = institutionMapper.invertConvert(userInfo.getInstitution());
        return bookmarkRepository.findByPersonAndInstitutionAndResourceUriIn(person, institution, resourceUris)
                .stream()
                .map(Bookmark::getResourceUri)
                .collect(Collectors.toSet());
    }

    /**
     * Deletes a bookmark for a user based on the resource URI.
     *
     * @param userInfo     the user information containing the user and institution
     * @param ressourceUri the URI of the resource to delete the bookmark for
     */
    @Transactional
    public void deleteBookmark(UserInfo userInfo, String ressourceUri) {
        Person person = personMapper.invertConvert(userInfo.getUser());
        Institution institution = institutionMapper.invertConvert(userInfo.getInstitution());

        bookmarkRepository.deleteBookmarkByPersonAndInstitutionAndResourceUri(
                person,
                institution,
                ressourceUri
        );
    }
}
