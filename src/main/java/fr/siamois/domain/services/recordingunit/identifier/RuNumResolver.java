package fr.siamois.domain.services.recordingunit.identifier;

import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdInfo;
import fr.siamois.domain.services.recordingunit.identifier.generic.RuNumericalIdentifierResolver;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
public class RuNumResolver extends RuNumericalIdentifierResolver {

    private static final String CODE = "NUM_UE";

    @NonNull
    @Override
    public String getCode() {
        return CODE;
    }

    @Nullable
    @Override
    public String getDescriptionLanguageCode() {
        return "ru.identifier.description.number";
    }

    @NonNull
    @Override
    public String getButtonStyleClass() {
        return "rounded-button ui-button-danger";
    }

    @NonNull
    @Override
    public String getTitleCode() {
        return "ru.identifier.title.number";
    }

    @Override
    protected int numericalValue(@NonNull RecordingUnitIdInfo info) {
        return info.getRuNumber();
    }

}
