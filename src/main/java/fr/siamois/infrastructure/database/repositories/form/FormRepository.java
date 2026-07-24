package fr.siamois.infrastructure.database.repositories.form;

import fr.siamois.domain.models.form.customform.CustomForm;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
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
        SELECT p.form_id
        FROM picked p
        """,
            nativeQuery = true
    )
    Optional<Long> findEffectiveFormIdByTypeAndInstitution(
            @Param("conceptId") Long conceptId,
            @Param("institutionId") Long institutionId
    );

    /**
     * The form to display for a type in the context of an institution.
     */
    default Optional<CustomForm> findEffectiveFormByTypeAndInstitution(Long conceptId, Long institutionId) {
        return findEffectiveFormIdByTypeAndInstitution(conceptId, institutionId).flatMap(this::findById);
    }

    /**
     * The fields a form lays out, in layout order, as {@code [fieldId, isRequired]} rows.
     * <p>
     * Reads the layout as JSON rather than through the {@link CustomForm} entity on purpose: the
     * layout converter reloads every field and then <em>detaches</em> it, which is harmless while
     * reading but leaves a detached entity behind that breaks the next flush of a read-write
     * transaction. Callers load the fields themselves, and get managed ones.
     *
     * @param formId the form whose layout is read
     * @return one row per column bound to a field, ordered by panel, row then column
     */
    @Query(
            value = """
        SELECT (form_col.item ->> 'fieldId')::bigint AS field_id,
               COALESCE((form_col.item ->> 'isRequired')::boolean, FALSE) AS is_required
        FROM custom_form f
        CROSS JOIN LATERAL jsonb_array_elements(f.layout)
             WITH ORDINALITY AS form_panel(item, panel_index)
        CROSS JOIN LATERAL jsonb_array_elements(form_panel.item -> 'rows')
             WITH ORDINALITY AS form_row(item, row_index)
        CROSS JOIN LATERAL jsonb_array_elements(form_row.item -> 'columns')
             WITH ORDINALITY AS form_col(item, col_index)
        WHERE f.form_id = :formId
          AND form_col.item ->> 'fieldId' IS NOT NULL
        ORDER BY form_panel.panel_index, form_row.row_index, form_col.col_index
        """,
            nativeQuery = true
    )
    List<Object[]> findLayoutFieldsByFormId(@Param("formId") Long formId);

}
