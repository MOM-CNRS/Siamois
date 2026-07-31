package fr.siamois.domain.services.form;

import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.dto.field.CustomFieldMeasurementDTO;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldMeasurementRepository;
import fr.siamois.mapper.CustomFieldMeasurementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    /**
     * The measurement fields offered as "existing fields" on a recording unit's form: the ones this
     * unit created come first, so a field the unit just created is always offered again no matter
     * where it falls in the global listing, followed by up to {@code limit} other existing fields.
     *
     * @param recordingUnitId the recording unit whose form is being displayed, null when the form
     *                        belongs to another kind of entity
     * @param limit           how many fields to pull from the global listing
     */
    public List<CustomFieldMeasurement> findOptionsForRecordingUnit(Long recordingUnitId, int limit) {
        Set<CustomFieldMeasurement> options = new LinkedHashSet<>();
        if (recordingUnitId != null) {
            options.addAll(repository.findByRecordingUnitId(recordingUnitId));
        }
        options.addAll(find(limit).getContent());
        return new ArrayList<>(options);
    }



}
