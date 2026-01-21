package fr.siamois.domain.services.recordingunit.identifier.generic;

import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdInfo;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface RuIdentifierResolver {

    /**
     * Returns the code of the current resolver without the brackets.
     * @return The code of the current resolver without the brackets.
     */
    @NonNull
    String getCode();

    @Nullable
    String getDescriptionLanguageCode();

    @NonNull
    String getTitleCode();

    @NonNull
    @SuppressWarnings("unused")
    default String getButtonStyleClass() {
        return "rounded-button";
    }

    /**
     * Returns true if the format contains the code of this resolver.
     * @param baseFormatString The format string from the Action Unit
     * @return True if the format contains the code of this resolver.
     */
    default boolean formatUsesThisResolver(@NonNull String baseFormatString) {
        final Pattern pattern = Pattern.compile("\\{([^:}]+)(:[^}]*)?\\}");
        final Matcher matcher = pattern.matcher(baseFormatString);

        while (matcher.find()) {
            final String elementCode = matcher.group(1);
            if (getCode().equals(elementCode)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    String resolve(@NonNull String baseFormatString, @NonNull RecordingUnitIdInfo ruInfo);

}
