package fr.siamois.infrastructure.database.repositories.form.config;

import fr.siamois.domain.models.form.config.ConceptFieldFormConfig;
import fr.siamois.domain.models.form.config.FieldFormConfig;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FieldFormConfigRepository extends JpaRepository<FieldFormConfig, FieldFormConfig.FieldFormConfigId> {

    /**
     * The field configurations of one form configuration. The custom field is fetched along, since
     * every caller reads its label and type to build the display model.
     *
     * @param formConfigId the form configuration whose fields are read
     * @return the field configurations, custom field included
     */
    @Query("""
            select ffc
            from FieldFormConfig ffc
            join fetch ffc.field
            where ffc.formConfig.id = :formConfigId
            order by ffc.position
            """)
    List<FieldFormConfig> findAllByFormConfigId(@Param("formConfigId") Long formConfigId);

    /**
     * How many configurations reference a custom field. Used before deleting an additional field,
     * so a field still configured on another type is not dropped along with the link.
     *
     * @param customFieldId the custom field to count the references of
     * @return the number of field configurations pointing at that custom field
     */
    @Query("""
            select count(ffc)
            from FieldFormConfig ffc
            where ffc.field.id = :customFieldId
            """)
    long countByFieldId(@Param("customFieldId") Long customFieldId);

    Optional<FieldFormConfig> findByFormConfigAndField(FormConfig formConfig, CustomField field);

    /**
     * Turns a plain {@link FieldFormConfig} row into a {@link ConceptFieldFormConfig} in place, by
     * adding its row to the subtype table. The JOINED subtype of an existing row can't be changed
     * through the entity API, and deleting then re-inserting the row in one transaction makes Envers
     * write two audit rows under the same (id, revision) key. The persistence context is flushed
     * before and cleared after, so the next read of the row instantiates the subtype.
     *
     * @param customFieldId the custom field of the row to promote
     * @param formConfigId  the form configuration of the row to promote
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO field_form_config_concept (fk_custom_field_id, fk_form_config_id)
            VALUES (:customFieldId, :formConfigId)
            """, nativeQuery = true)
    void promoteToConceptConfig(@Param("customFieldId") Long customFieldId, @Param("formConfigId") Long formConfigId);

    /**
     * The field's own restriction (branch/collection) for one specific value of the entity's
     * "scope" field (e.g. Type = Céramique) in a project. A {@link FormConfig} exists per
     * (actionUnit, fieldConcept, valueConcept) triple, so this must be scoped by valueConceptId
     * too, not just field + actionUnit — otherwise, when the same field is configured differently
     * under two value concepts of the same project, the query would match both rows.
     *
     * @param conceptField   the concept field to look the restriction up for
     * @param actionUnitId   the project the configuration is scoped to
     * @param valueConceptId the concept of the entity's current scope-field value
     * @return the value-specific configuration, or empty when that value has none yet
     */
    @Query(
            """
            SELECT ffc
            FROM ConceptFieldFormConfig ffc
            WHERE ffc.field = :conceptField
              AND ffc.formConfig.actionUnit.id = :actionUnitId
              AND ffc.formConfig.valueConcept.id = :valueConceptId
            """
    )
    Optional<ConceptFieldFormConfig> findByFieldAndActionUnitAndValue(@Param("conceptField") CustomFieldConcept conceptField,
                                                                      @Param("actionUnitId") Long actionUnitId,
                                                                      @Param("valueConceptId") Long valueConceptId);

    /**
     * The field's own default restriction (branch/collection) for a project, i.e. the one carrying
     * no value concept — see {@link #findByFieldAndActionUnitAndValue}.
     *
     * @param conceptField the concept field to look the restriction up for
     * @param actionUnitId the project the configuration is scoped to
     * @return the default configuration, or empty when the project has none yet
     */
    @Query(
            """
            SELECT ffc
            FROM ConceptFieldFormConfig ffc
            WHERE ffc.field = :conceptField
              AND ffc.formConfig.actionUnit.id = :actionUnitId
              AND ffc.formConfig.valueConcept IS NULL
            """
    )
    Optional<ConceptFieldFormConfig> findDefaultByFieldAndActionUnit(@Param("conceptField") CustomFieldConcept conceptField,
                                                                     @Param("actionUnitId") Long actionUnitId);

    /**
     * The field code, branch and collection sources configured for a project: one row per field and per
     * entity type, with the source they draw their list from.
     *
     * @param actionUnitId the project
     * @return the vocabulary-restricted field configurations of the project
     */
    @Query("""
            SELECT ffc
            FROM ConceptFieldFormConfig ffc
            JOIN FETCH ffc.field
            JOIN FETCH ffc.formConfig fc
            LEFT JOIN FETCH ffc.branchTopTerm
            LEFT JOIN FETCH ffc.collection
            LEFT JOIN FETCH ffc.vocabulary
            LEFT JOIN FETCH fc.valueConcept
            WHERE fc.actionUnit.id = :actionUnitId
              AND (ffc.branchTopTerm IS NOT NULL OR ffc.collection IS NOT NULL OR ffc.vocabulary IS NOT NULL)
            """)
    List<ConceptFieldFormConfig> findVocabularyRestrictionsOfActionUnit(@Param("actionUnitId") Long actionUnitId);

    /**
     * Same as {@link #findVocabularyRestrictionsOfActionUnit} for a whole organisation: the configurations of each of
     * its projects, and those set at the organisation level (carrying no project), if any.
     *
     * @param institutionId the organisation
     * @return the vocabulary-restricted field configurations of the organisation and of its projects
     */
    @Query("""
            SELECT ffc
            FROM ConceptFieldFormConfig ffc
            JOIN FETCH ffc.field
            JOIN FETCH ffc.formConfig fc
            LEFT JOIN FETCH ffc.branchTopTerm
            LEFT JOIN FETCH ffc.collection
            LEFT JOIN FETCH ffc.vocabulary
            LEFT JOIN FETCH fc.valueConcept
            LEFT JOIN FETCH fc.actionUnit au
            WHERE ((fc.actionUnit IS NULL AND fc.institution.id = :institutionId)
                   OR au.createdByInstitution.id = :institutionId)
              AND (ffc.branchTopTerm IS NOT NULL OR ffc.collection IS NOT NULL OR ffc.vocabulary IS NOT NULL)
            """)
    List<ConceptFieldFormConfig> findVocabularyRestrictionsOfInstitution(@Param("institutionId") Long institutionId);
}
