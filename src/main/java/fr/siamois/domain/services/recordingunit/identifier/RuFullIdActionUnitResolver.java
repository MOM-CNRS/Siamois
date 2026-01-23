package fr.siamois.domain.services.recordingunit.identifier;

import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdInfo;
import fr.siamois.domain.services.recordingunit.identifier.generic.RuIdentifierResolver;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RuFullIdActionUnitResolver implements RuIdentifierResolver {

    private static final String DEFAULT_PATTERN = "*";
    public static final String ID_UA = "{ID_UA}";

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

    @NonNull
    @Override
    public String getTitleCode() {
        return "ru.identifier.title.id_ua";
    }

    @Override
    public boolean formatUsesThisResolver(@NonNull String baseFormatString) {
        return baseFormatString.contains(ID_UA);
    }

    @NonNull
    @Override
    public String resolve(@NonNull String baseFormatString, @NonNull RecordingUnitIdInfo ruInfo) {
        if (ruInfo.getActionUnit() == null) {
            return baseFormatString.replace(ID_UA, DEFAULT_PATTERN);
        }
        String replacement = ruInfo.getActionUnit().getFullIdentifier();
        if (replacement == null || replacement.isEmpty()) {
            replacement = DEFAULT_PATTERN;
        }

        Pattern pattern = Pattern.compile("\\{ID_UA}");
        Matcher matcher = pattern.matcher(baseFormatString);

        if (matcher.find()) {
            baseFormatString = matcher.replaceAll(replacement);
        }

        return baseFormatString;
    }

    @NonNull
    @Override
    public String getButtonStyleClass() {
        return "rounded-button action-unit";
    }
}
