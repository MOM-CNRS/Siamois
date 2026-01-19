package fr.siamois.domain.services.recordingunit.identifier;

import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdInfo;
import fr.siamois.domain.services.recordingunit.identifier.generic.RuNumericalIdentifierResolver;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
public class RuNumSpatialUnitResolver extends RuNumericalIdentifierResolver {

    @Override
    protected int numericalValue(@NonNull RecordingUnitIdInfo info) {
        if (info.getSpatialUnitNumber() == null) {
            return 0;
        }
        return info.getSpatialUnitNumber();
    }

    @NonNull
    @Override
    public String getCode() {
        return "NUM_USPATIAL";
    }

    @Nullable
    @Override
    public String getDescriptionLanguageCode() {
        return "ru.identifier.description.num_uspatial";
    }
}
