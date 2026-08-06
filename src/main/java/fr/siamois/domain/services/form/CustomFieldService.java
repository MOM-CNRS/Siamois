package fr.siamois.domain.services.form;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for managing custom fields in the context of recording units.
 * This service provides methods to retrieve custom fields associated with spatial units.
 */
@Service
@RequiredArgsConstructor
public class CustomFieldService {

    private final CustomFieldRepository customFieldRepository;

    /**
     * Finds all custom fields associated with a given spatial unit ID.
     *
     * @param spatialUnitId the ID of the spatial unit for which to find custom fields
     * @return a list of CustomField objects associated with the specified spatial unit ID
     */
    public List<CustomField> findAllFieldsBySpatialUnitId(Long spatialUnitId) {
        return customFieldRepository.findAllFieldsBySpatialUnitId(spatialUnitId);
    }

}
