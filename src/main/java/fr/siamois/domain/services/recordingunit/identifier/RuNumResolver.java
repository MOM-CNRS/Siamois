package fr.siamois.domain.services.recordingunit.identifier;

import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdInfo;
import fr.siamois.domain.services.recordingunit.identifier.generic.RuNumericalIdentifierResolver;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
public class RuNumResolver extends RuNumericalIdentifierResolver {

    public RuNumResolver() {
        super("NUM_UE", "ru.identifier.description.number", "ru.identifier.title.number");
    }

    @NonNull
    @Override
    public String getButtonStyleClass() {
        return "rounded-button ui-button-danger";
    }

    @Override
    protected int numericalValue(@NonNull RecordingUnitIdInfo info) {
        return info.getRuNumber();
    }

}
