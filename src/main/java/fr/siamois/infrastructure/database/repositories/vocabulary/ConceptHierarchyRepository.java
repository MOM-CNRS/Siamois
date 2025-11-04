package fr.siamois.infrastructure.database.repositories.vocabulary;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.ConceptHierarchy;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConceptHierarchyRepository extends CrudRepository<ConceptHierarchy, Long> {
    List<ConceptHierarchy> findAllByChildAndParentFieldContext(Concept child, Concept parentFieldContext);
}
