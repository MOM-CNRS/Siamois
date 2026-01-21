package fr.siamois.domain.services.recordingunit.identifier;

import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdInfo;
import fr.siamois.domain.services.recordingunit.identifier.generic.RuNumericalIdentifierResolver;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Service
public class RuNumSpatialUnitResolver extends RuNumericalIdentifierResolver {

    public RuNumSpatialUnitResolver() {
        super("NUM_USPATIAL", "ru.identifier.description.num_uspatial", "ru.identifier.title.num_uspatial");
    }

    @Override
    protected int numericalValue(@NonNull RecordingUnitIdInfo info) {
        if (info.getSpatialUnitNumber() == null) {
            return 0;
        }
        return info.getSpatialUnitNumber();
    }
}
