package fr.siamois.infrastructure.database.repositories;

import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.institution.Institution;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends CrudRepository<Document, Long>, RevisionRepository<Document, Long, Long> {
    List<Document> findAllByArkIsNullAndCreatedByInstitution(Institution institution);

    boolean existsByFileCode(String fileCode);

    Optional<Document> findByFileCode(String fileCode);

    @Query(
            nativeQuery = true,
            value = "SELECT d.* FROM siamois_document d " +
                    "JOIN spatial_unit_document sud ON d.document_id = sud.fk_document_id " +
                    "WHERE sud.fk_spatial_unit_id = :spatialUnitId"
    )
    List<Document> findDocumentsBySpatialUnit(Long spatialUnitId);

    @Query(
            nativeQuery = true,
            value = "SELECT d.* FROM siamois_document d " +
                    "JOIN action_unit_document sud ON d.document_id = sud.fk_document_id " +
                    "WHERE sud.fk_action_unit_id = :actionUnitId"
    )
    List<Document> findDocumentsByActionUnit(Long actionUnitId);

    @Query(
            nativeQuery = true,
            value = "SELECT d.* FROM siamois_document d " +
                    "JOIN recording_unit_document sud ON d.document_id = sud.fk_document_id " +
                    "WHERE sud.fk_recording_unit_id = :recordingUnitId"
    )
    List<Document> findDocumentsByRecordingUnit(Long recordingUnitId);

    @Query(
            nativeQuery = true,
            value = "SELECT d.* FROM siamois_document d " +
                    "JOIN specimen_document sud ON d.document_id = sud.fk_document_id " +
                    "WHERE sud.fk_specimen_id = :specimenId"
    )
    List<Document> findDocumentsBySpecimen(Long specimenId);

    @Transactional
    @Modifying
    @Query(
            nativeQuery = true,
            value = "INSERT INTO spatial_unit_document(fk_document_id, fk_spatial_unit_id) " +
                    "VALUES (:documentId, :spatialUnitId)"
    )
    void addDocumentToSpatialUnit(Long documentId, Long spatialUnitId);

    @Transactional
    @Modifying
    @Query(
            nativeQuery = true,
            value = "INSERT INTO action_unit_document(fk_document_id, fk_action_unit_id) " +
                    "VALUES (:documentId, :actionUnitId)"
    )
    void addDocumentToActionUnit(Long documentId, Long actionUnitId);

    @Query(
            nativeQuery = true,
            value = "SELECT COUNT(*) > 0 " +
                    "FROM spatial_unit_document sud " +
                    "JOIN siamois_document sd ON sud.fk_document_id = sd.document_id " +
                    "WHERE sud.fk_spatial_unit_id = :spatialUnitId AND sd.md5_sum = :hash"
    )
    boolean existsByHashInSpatialUnit(Long spatialUnitId, String hash);

    @Transactional
    @Modifying
    @Query(
            nativeQuery = true,
            value = "INSERT INTO recording_unit_document(fk_document_id, fk_recording_unit_id) " +
                    "VALUES (:documentId, :recordingUnitId)"
    )
    void addDocumentToRecordingUnit(Long documentId, Long recordingUnitId);

    @Transactional
    @Modifying
    @Query(
            nativeQuery = true,
            value = "INSERT INTO specimen_document(fk_document_id, fk_specimen_id) " +
                    "VALUES (:documentId, :specimenId)"
    )
    void addDocumentToSpecimen(Long documentId, Long specimenId);

    @Query(
            nativeQuery = true,
            value = "SELECT COUNT(*) > 0 " +
                    "FROM recording_unit_document sud " +
                    "JOIN siamois_document sd ON sud.fk_document_id = sd.document_id " +
                    "WHERE sud.fk_recording_unit_id = :recordingUnitId AND sd.md5_sum = :hash"
    )
    boolean existsByHashInRecordingUnit(Long recordingUnitId, String hash);

    @Query(
            nativeQuery = true,
            value = "SELECT COUNT(*) > 0 " +
                    "FROM action_unit_document sud " +
                    "JOIN siamois_document sd ON sud.fk_document_id = sd.document_id " +
                    "WHERE sud.fk_action_unit_id = :actionUnitId AND sd.md5_sum = :hash"
    )
    boolean existsByHashInActionUnit(Long actionUnitId, String hash);

    @Query(
            nativeQuery = true,
            value = "SELECT COUNT(*) > 0 " +
                    "FROM specimen_document sud " +
                    "JOIN siamois_document sd ON sud.fk_document_id = sd.document_id " +
                    "WHERE sud.fk_specimen_id = :specimenId AND sd.md5_sum = :hash"
    )
    boolean existsByHashInSpecimen(Long specimenId, String hash);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "DELETE FROM action_unit_document WHERE fk_document_id = :documentId")
    void deleteActionUnitDocumentLinks(@Param("documentId") Long documentId);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "DELETE FROM action_unit_document WHERE fk_action_unit_id = :actionUnitId")
    void deleteAllActionUnitDocumentLinksByActionUnitId(@Param("actionUnitId") Long actionUnitId);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "DELETE FROM spatial_unit_document WHERE fk_document_id = :documentId")
    void deleteSpatialUnitDocumentLinks(@Param("documentId") Long documentId);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "DELETE FROM recording_unit_document WHERE fk_document_id = :documentId")
    void deleteRecordingUnitDocumentLinks(@Param("documentId") Long documentId);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "DELETE FROM recording_unit_document WHERE fk_recording_unit_id = :recordingUnitId")
    void deleteAllRecordingUnitDocumentLinksByRecordingUnitId(@Param("recordingUnitId") Long recordingUnitId);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "DELETE FROM specimen_document WHERE fk_document_id = :documentId")
    void deleteSpecimenDocumentLinks(@Param("documentId") Long documentId);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "DELETE FROM specimen_document WHERE fk_specimen_id = :specimenId")
    void deleteAllSpecimenDocumentLinksBySpecimenId(@Param("specimenId") long specimenId);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "DELETE FROM specimen_study_document WHERE fk_document_id = :documentId")
    void deleteSpecimenStudyDocumentLinks(@Param("documentId") Long documentId);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "DELETE FROM ru_study_document WHERE fk_document_id = :documentId")
    void deleteRuStudyDocumentLinks(@Param("documentId") Long documentId);
}
