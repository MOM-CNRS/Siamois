package fr.siamois.infrastructure.database.repositories.form;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.vocabulary.Concept;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomFieldRepository extends CrudRepository<CustomField, Long> {

    @Query(
            nativeQuery = true,
            value = """
                        SELECT DISTINCT cfield.*
                        FROM recording_unit ru
                        JOIN action_unit au ON ru.fk_action_unit_id = au.action_unit_id
                        JOIN custom_form_response cfr ON ru.fk_custom_form_response = cfr.custom_form_response_id
                        JOIN custom_form cf ON cfr.fk_custom_form_id = cf.form_id
                        JOIN LATERAL jsonb_array_elements(cf.layout) AS layout_item ON TRUE
                        JOIN LATERAL jsonb_array_elements(layout_item->'fields') AS field_id ON TRUE
                        JOIN custom_field cfield ON cfield.custom_field_id = (field_id)::int
                        WHERE su.spatial_unit_id = :spatialUnitId
                    """
    )
    List<CustomField> findAllFieldsBySpatialUnitId(@Param("spatialUnitId" ) Long spatialUnitId);

    @Query("""
              select f
              from CustomField f
              where type(f) = :clazz
                and f.isSystemField = :isSystemField
                and f.valueBinding = :valueBinding
                and f.concept = :concept
            """)
    Optional<CustomField> findByTypeAndSystemAndBindingAndConcept(
            @Param("clazz" ) Class<? extends CustomField> clazz,
            @Param("isSystemField" ) Boolean isSystemField,
            @Param("valueBinding" ) String valueBinding,
            @Param("concept" ) Concept concept);

    /**
     * Every system field of the instance. System fields are defined by the application itself (see
     * {@code SystemFieldCatalog}) and shared by every project of every institution, so they are read
     * as a whole and matched against their definition rather than queried one institution at a time.
     *
     * @return the system custom fields
     */
    @Query("""
            select f
            from CustomField f
            where f.isSystemField = true
            """)
    List<CustomField> findAllSystemFields();

    /**
     * The non-system custom fields an institution can reuse, i.e. the ones at least one of its form
     * configurations references. A custom field carries no institution of its own, so the
     * institution it belongs to is the one of the configurations it is linked to; a field linked
     * nowhere is reachable by nobody and is deliberately left out.
     *
     * @param institutionId the institution whose reusable fields are read
     * @return the institution's non-system custom fields, ordered by label
     */
    @Query("""
            select distinct f
            from FieldFormConfig ffc
            join ffc.field f
            where ffc.formConfig.institution.id = :institutionId
              and f.isSystemField = false
            order by f.label
            """)
    List<CustomField> findAllReusableByInstitution(@Param("institutionId") Long institutionId);
}
