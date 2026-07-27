package fr.siamois.infrastructure.database.repositories.form;

import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerLegacy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomFieldAnswerRepository extends CrudRepository<CustomFieldAnswerLegacy, Long> {

    @Query(value = """
        SELECT * FROM custom_field_answer
        WHERE fk_form_response = :formResponseId
        AND fk_field_id = :fieldId
    """, nativeQuery = true)
    Optional<CustomFieldAnswerLegacy> findByFormResponseIdAndFieldId(
            @Param("formResponseId") Long formResponseId,
            @Param("fieldId") Long fieldId
    );

}
