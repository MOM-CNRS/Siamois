package fr.siamois.infrastructure.database.repositories.vocabulary;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.ConceptHierarchy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ConceptHierarchyRepository extends CrudRepository<ConceptHierarchy, Long> {
    List<ConceptHierarchy> findAllByChildAndParentFieldContext(Concept child, Concept parentFieldContext);

    /**
     * The links outside any field context below several parents, as (parent id, child id) pairs, so an
     * import only adds the missing ones — ids only, the entities would load their child eagerly.
     */
    @Query("SELECT h.parent.id, h.child.id FROM ConceptHierarchy h WHERE h.parent IN :parents AND h.parentFieldContext IS NULL")
    List<Object[]> findLinkIdsBelowOutsideFieldContext(@Param("parents") Collection<Concept> parents);
}
