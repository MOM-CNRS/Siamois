package fr.siamois.infrastructure.database.repositories.form;

import fr.siamois.domain.models.form.customfield.CustomFieldMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomFieldMeasurementRepository extends JpaRepository<CustomFieldMeasurement, Long> {

}
