package fr.siamois.infrastructure.database.repositories.form;

import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomFieldMeasurementRepository extends JpaRepository<CustomFieldMeasurement, Long> {

    /**
     * The measurement fields created from the given recording unit's form.
     */
    @Query("""
            SELECT m FROM RecordingUnit ru JOIN ru.onTheFlyFields m
            WHERE ru.id = :recordingUnitId
            """)
    List<CustomFieldMeasurement> findByRecordingUnitId(@Param("recordingUnitId") Long recordingUnitId);

}
