package fr.siamois.infrastructure.database.repositories.form;

import fr.siamois.domain.models.form.config.FormConfigAnswer;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomFieldAnswerRepository extends CrudRepository<CustomFieldAnswer, CustomFieldAnswer.CustomFieldAnswerId> {
    Optional<CustomFieldAnswer> findByFormConfigAnswerAndCustomField(FormConfigAnswer formConfigAnswer, CustomField customField);

    @Query("""
            select count(a)
            from CustomFieldAnswer a
            where a.customField.id = :customFieldId
              and a.formConfigAnswer.formConfig.actionUnit.id = :projectId
            """)
    long countByFieldIdAndProjectId(@Param("customFieldId") Long customFieldId, @Param("projectId") Long projectId);

    /** The answers of a page of recordingUnits to some of their additional fields, with their answer set (for the owner id). */
    @Query("""
            select a
            from CustomFieldAnswer a
            join fetch a.formConfigAnswer s
            where s.recordingUnit.id in :ownerIds
              and a.customField.id in :fieldIds
            """)
    List<CustomFieldAnswer> findAnswersOfRecordingUnits(@Param("ownerIds") Collection<Long> ownerIds,
                                               @Param("fieldIds") Collection<Long> fieldIds);

    /** The answers of a page of specimens to some of their additional fields, with their answer set (for the owner id). */
    @Query("""
            select a
            from CustomFieldAnswer a
            join fetch a.formConfigAnswer s
            where s.specimen.id in :ownerIds
              and a.customField.id in :fieldIds
            """)
    List<CustomFieldAnswer> findAnswersOfSpecimens(@Param("ownerIds") Collection<Long> ownerIds,
                                               @Param("fieldIds") Collection<Long> fieldIds);

    /** The answers of a page of phases to some of their additional fields, with their answer set (for the owner id). */
    @Query("""
            select a
            from CustomFieldAnswer a
            join fetch a.formConfigAnswer s
            where s.phase.id in :ownerIds
              and a.customField.id in :fieldIds
            """)
    List<CustomFieldAnswer> findAnswersOfPhases(@Param("ownerIds") Collection<Long> ownerIds,
                                               @Param("fieldIds") Collection<Long> fieldIds);

    /** The answers of a page of containers to some of their additional fields, with their answer set (for the owner id). */
    @Query("""
            select a
            from CustomFieldAnswer a
            join fetch a.formConfigAnswer s
            where s.container.id in :ownerIds
              and a.customField.id in :fieldIds
            """)
    List<CustomFieldAnswer> findAnswersOfContainers(@Param("ownerIds") Collection<Long> ownerIds,
                                               @Param("fieldIds") Collection<Long> fieldIds);
}
