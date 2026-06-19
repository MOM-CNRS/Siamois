package fr.siamois.infrastructure.database.repositories.form;

import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.formscope.FormScope;
import fr.siamois.domain.models.vocabulary.Concept;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FormScopeRepository extends CrudRepository<FormScope, Long> {

    Optional<FormScope> findByTypeAndFormAndScopeLevel(Concept type, CustomForm form, FormScope.ScopeLevel scopeLevel);

    @Query("SELECT fs.type FROM FormScope fs WHERE fs.scopeLevel = fr.siamois.domain.models.form.formscope.FormScope.ScopeLevel.ORG_WIDE AND fs.institution.id = :institutionId AND fs.type IS NOT NULL")
    List<Concept> findConfiguredTypesByInstitution(@Param("institutionId") Long institutionId);

    /**
     * Méthode pratique pour récupérer un scope global (GLOBAL_DEFAULT) par type et formulaire.
     */
    default Optional<FormScope> findGlobalByTypeAndForm(Concept type, CustomForm form) {
        return findByTypeAndFormAndScopeLevel(type, form, FormScope.ScopeLevel.GLOBAL_DEFAULT);
    }

}
