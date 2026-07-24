package fr.siamois.infrastructure.database.repositories.form.config;

import fr.siamois.domain.models.form.config.FieldFormConfig;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FieldFormConfigRepository extends CrudRepository<FieldFormConfig, FieldFormConfig.FieldFormConfigId> {

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
}
