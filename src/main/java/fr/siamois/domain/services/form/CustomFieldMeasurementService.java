package fr.siamois.domain.services.form;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldMeasurement;
import fr.siamois.dto.field.CustomFieldMeasurementDTO;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldMeasurementRepository;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.mapper.CustomFieldMeasurementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for managing measurement field
 */
@Service
@RequiredArgsConstructor
public class CustomFieldMeasurementService {

    private final CustomFieldMeasurementRepository repository;
    private final CustomFieldMeasurementMapper mapper;

    /*
    Save a measurement field
     */
    public CustomFieldMeasurement save(CustomFieldMeasurementDTO fieldMeasurementDTO) {
        return repository.save(mapper.invertConvert(fieldMeasurementDTO));
    }

}
