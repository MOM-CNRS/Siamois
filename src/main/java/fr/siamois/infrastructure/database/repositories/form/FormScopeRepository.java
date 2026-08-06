package fr.siamois.infrastructure.database.repositories.form;

import fr.siamois.domain.models.form.formscope.FormScope;
import fr.siamois.domain.models.vocabulary.Concept;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FormScopeRepository extends CrudRepository<FormScope, Long> {

    @Query("SELECT fs.type FROM FormScope fs WHERE fs.scopeLevel = fr.siamois.domain.models.form.formscope.FormScope.ScopeLevel.GLOBAL_DEFAULT AND fs.type IS NOT NULL")
    List<Concept> findConfiguredTypesByInstitution();

}
