package fr.siamois.infrastructure.database.repositories.form;

import fr.siamois.domain.models.form.customform.CustomForm;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FormRepository extends CrudRepository<CustomForm, Long> {

    Optional<CustomForm> findById(CustomForm form);

    @Query(
            value = """
        WITH candidates AS (
          -- 1) ORG_WIDE avec concept précis
          SELECT fs.fk_custom_form_id AS form_id, 1 AS priority
          FROM form_scopes fs
          WHERE fs.scope_level = 'ORG_WIDE'
            AND fs.fk_institution_id = :institutionId
            AND :conceptId IS NOT NULL
            AND fs.fk_type_id = :conceptId

          UNION ALL

          -- 2) ORG_WIDE générique (fk_type_id NULL)
          SELECT fs.fk_custom_form_id AS form_id, 2 AS priority
          FROM form_scopes fs
          WHERE fs.scope_level = 'ORG_WIDE'
            AND fs.fk_institution_id = :institutionId
            AND fs.fk_type_id IS NULL

          UNION ALL

          -- 3) GLOBAL_DEFAULT avec concept précis
          SELECT fs.fk_custom_form_id AS form_id, 3 AS priority
          FROM form_scopes fs
          WHERE fs.scope_level = 'GLOBAL_DEFAULT'
            AND :conceptId IS NOT NULL
            AND fs.fk_type_id = :conceptId

          UNION ALL

          -- 4) GLOBAL_DEFAULT générique (fk_type_id NULL)
          SELECT fs.fk_custom_form_id AS form_id, 4 AS priority
          FROM form_scopes fs
          WHERE fs.scope_level = 'GLOBAL_DEFAULT'
            AND fs.fk_type_id IS NULL
        ),
        picked AS (
          SELECT form_id
          FROM candidates
          ORDER BY priority ASC, form_id DESC
          LIMIT 1
        )
        SELECT f.*
        FROM custom_form f
        JOIN picked p ON p.form_id = f.form_id
        """,
            nativeQuery = true
    )
    Optional<CustomForm> findEffectiveFormByTypeAndInstitution(
            @Param("conceptId") Long conceptId,
            @Param("institutionId") Long institutionId
    );



}
