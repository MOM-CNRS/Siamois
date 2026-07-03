package fr.siamois.infrastructure.database.repositories.vocabulary;

import fr.siamois.domain.models.vocabulary.Concept;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConceptRepository extends CrudRepository<Concept, Long>, RevisionRepository<Concept, Long, Long> {

    /**
     * Find a concept by its external ids.
     * @param idt The ID of the external vocabulary
     * @param idc The ID of the concept in the external vocabulary
     * @return An optional containing the concept if found
     */
    @Query(
            "SELECT c FROM Concept c " +
                    "JOIN c.vocabulary v " +
                    "WHERE LOWER(v.externalVocabularyId) = LOWER(:idt) AND LOWER(c.externalId) = LOWER(:idc)"
    )
    Optional<Concept> findConceptByExternalIdIgnoreCase(String idt, String idc);

    /**
     * Find the top term configuration for a field code of a user.
     * @param fieldCode The code of the field
     * @return An optional containing the concept if found
     */
    @Query(
            nativeQuery = true,
            value = "SELECT c.* FROM concept c " +
                    "JOIN concept_field_config cfc ON cfc.fk_concept_id = c.concept_id " +
                    "WHERE cfc.fk_institution_id = :institutionId AND " +
                    "cfc.fk_user_id = :userId AND " +
                    "cfc.field_code = :fieldCode"
    )
    Optional<Concept> findTopTermConfigForFieldCodeOfUser(Long institutionId, Long userId, String fieldCode);

    @Query(
            nativeQuery = true,
            value = "SELECT DISTINCT c.* "+
                    "FROM spatial_unit su "+
                    "LEFT JOIN concept c ON su.fk_concept_category_id = c.concept_id "+
                    "WHERE su.fk_institution_id = :institutionId"
    )
    List<Concept> findAllBySpatialUnitOfInstitution(@Param("institutionId") Long institutionId);

    @Query(
            nativeQuery = true,
            value = "SELECT DISTINCT c.* "+
                    "FROM action_unit su "+
                    "LEFT JOIN concept c ON su.fk_type = c.concept_id "+
                    "WHERE su.fk_institution_id = :institutionId"
    )
    List<Concept> findAllByActionUnitOfInstitution(@Param("institutionId") Long institutionId);

    @Query(
            nativeQuery = true,
            value = "SELECT c.* FROM concept c " +
                    "JOIN concept_field_config cfc ON cfc.fk_concept_id = c.concept_id " +
                    "WHERE cfc.fk_institution_id = :institutionId " +
                    "AND cfc.field_code = :fieldCode " +
                    "AND cfc.fk_user_id IS NULL"
    )
    Optional<Concept> findTopTermConfigForFieldCodeOfInstitution(Long institutionId, String fieldCode);

    /**
     * Finds concepts whose pref or alt label exactly matches (case/accent-insensitive) the given
     * label, within the subtree of a field's configured root concept — including the root concept
     * itself, whose own label isn't tagged with fk_field_parent_concept_id pointing to itself
     * (only its descendants are), so it needs a separate match on the concept id.
     * @param fieldConceptId The id of the field's root concept (from ConceptFieldConfig)
     * @param lang The language code of the label
     * @param label The label to match exactly (case/accent-insensitive)
     * @return All concepts matching the label — callers should treat anything other than exactly one as an error
     */
    @Query(
            nativeQuery = true,
            value = "SELECT DISTINCT c.* FROM concept c " +
                    "JOIN concept_label cl ON cl.fk_concept_id = c.concept_id " +
                    "WHERE (cl.fk_field_parent_concept_id = :fieldConceptId OR c.concept_id = :fieldConceptId) " +
                    "AND cl.lang_code = :lang " +
                    "AND NOT c.is_deleted " +
                    "AND unaccent(cl.label) ILIKE unaccent(:label)"
    )
    List<Concept> findAllByFieldContextAndExactLabel(Long fieldConceptId, String lang, String label);

}
