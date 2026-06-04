package fr.siamois.infrastructure.database.repositories;

import fr.siamois.domain.models.Bookmark;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookmarkRepository extends CrudRepository<Bookmark, Long> {
    List<Bookmark> findByPersonAndInstitution(Person person, Institution institution);
    Long countBookmarkByPersonAndInstitutionAndResourceUri(Person person, Institution institution, String resourceUri);
    void deleteBookmarkByPersonAndInstitutionAndResourceUri(Person person, Institution institution, String resourceUri);
    Page<Bookmark> findByPersonAndInstitution(Person person, Institution institution, Pageable pageable);
}

