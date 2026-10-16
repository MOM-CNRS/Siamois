package fr.siamois.infrastructure.database.repositories.form.config;

import fr.siamois.domain.models.form.config.ConceptFieldState;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.CustomField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConceptFieldStateRepository extends JpaRepository<ConceptFieldState, Long> {

    /**
     * The explicit states of a vocabulary field's concepts in one form configuration. The concept is
     * fetched along, since every caller reads its id.
     *
     * @param field      the vocabulary field
     * @param formConfig the form configuration (project × type) the states belong to
     * @return the states, empty when the field's list has never been customised
     */
    @Query("""
            select s
            from ConceptFieldState s
            join fetch s.concept
            where s.field = :field
              and s.formConfig = :formConfig
            """)
    List<ConceptFieldState> findAllByFieldAndFormConfig(@Param("field") CustomField field,
                                                        @Param("formConfig") FormConfig formConfig);
}
