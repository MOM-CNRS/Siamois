package fr.siamois.domain.services.form;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldMeasurement;
import fr.siamois.dto.field.CustomFieldMeasurementDTO;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldMeasurementRepository;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.mapper.CustomFieldMeasurementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Pageable;
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

    /*
     Find with a limit
    */
    public Page<CustomFieldMeasurement> find(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return repository.findAll(pageable);
    }



}
