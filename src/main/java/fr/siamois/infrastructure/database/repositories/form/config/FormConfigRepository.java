package fr.siamois.infrastructure.database.repositories.form.config;

import fr.siamois.domain.models.form.config.FormConfig;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * A {@link FormConfig} is looked up by the triple that identifies it: the project (action unit) it
 * is scoped to, the field it configures ({@code fieldConcept}) and the value of that field it
 * applies to ({@code valueConcept}). A null {@code valueConcept} is the default configuration, so
 * it needs its own query — JPQL cannot compare a parameter to null with {@code =}.
 */
@Repository
public interface FormConfigRepository extends CrudRepository<FormConfig, Long> {

    /**
     * The default configuration of a field for a project, i.e. the one carrying no value concept.
     *
     * @param actionUnitId   the project the configuration is scoped to
     * @param fieldConceptId the concept of the configured field
     * @return the default configuration, or empty when the project has none yet
     */
    @Query("""
            select fc
            from FormConfig fc
            where fc.actionUnit.id = :actionUnitId
              and fc.fieldConcept.id = :fieldConceptId
              and fc.valueConcept is null
            """)
    Optional<FormConfig> findDefaultByActionUnitAndField(@Param("actionUnitId") Long actionUnitId,
                                                         @Param("fieldConceptId") Long fieldConceptId);

    /**
     * Every configuration a project holds for a field, the default one included.
     *
     * @param actionUnitId   the project the configurations are scoped to
     * @param fieldConceptId the concept of the configured field
     * @return the project's configurations of that field
     */
    @Query("""
            select fc
            from FormConfig fc
            where fc.actionUnit.id = :actionUnitId
              and fc.fieldConcept.id = :fieldConceptId
            """)
    List<FormConfig> findAllByActionUnitAndField(@Param("actionUnitId") Long actionUnitId,
                                                 @Param("fieldConceptId") Long fieldConceptId);

    /**
     * The configuration of a field for one specific value of that field, in a project.
     *
     * @param actionUnitId   the project the configuration is scoped to
     * @param fieldConceptId the concept of the configured field
     * @param valueConceptId the concept of the field value the configuration applies to
     * @return the configuration, or empty when that value has none yet
     */
    @Query("""
            select fc
            from FormConfig fc
            where fc.actionUnit.id = :actionUnitId
              and fc.fieldConcept.id = :fieldConceptId
              and fc.valueConcept.id = :valueConceptId
            """)
    Optional<FormConfig> findByActionUnitAndFieldAndValue(@Param("actionUnitId") Long actionUnitId,
                                                          @Param("fieldConceptId") Long fieldConceptId,
                                                          @Param("valueConceptId") Long valueConceptId);
}
