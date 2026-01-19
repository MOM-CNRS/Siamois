package fr.siamois.domain.services.recordingunit.identifier;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdInfo;
import fr.siamois.domain.services.recordingunit.identifier.generic.RuIdentifierResolver;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
public class RuFullIdActionUnitResolver implements RuIdentifierResolver {

    @NonNull
    @Override
    public String getCode() {
        return "ID_UA";
    }

    @Nullable
    @Override
    public String getDescriptionLanguageCode() {
        return "ru.identifier.description.id_ua";
    }

    @Override
    public boolean formatUsesThisResolver(@NonNull String baseFormatString) {
        return baseFormatString.contains("{ID_UA}");
    }

    @NonNull
    @Override
    public String resolve(@NonNull String baseFormatString, @NonNull RecordingUnitIdInfo ruInfo) {
        if (ruInfo.getActionUnit() == null) {
            return "";
        }
        return baseFormatString.replace("{ID_UA}", ruInfo.getActionUnit().getFullIdentifier());
    }
}
