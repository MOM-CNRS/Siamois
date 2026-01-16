package fr.siamois.domain.services.recordingunit.identifier;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdInfo;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
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

    /**
     * Returns true if the format contains the code of this resolver.
     * @param baseFormatString The format string from the Action Unit
     * @return True if the format contains the code of this resolver.
     */
    default boolean formatUsesThisResolver(@NonNull String baseFormatString) {
        final Pattern pattern = Pattern.compile("\\{[^}]*\\}-\\{[^}]*\\}-\\{[^}]*\\}", Pattern.CASE_INSENSITIVE);
        final Matcher matcher = pattern.matcher(baseFormatString);

        while (matcher.find()) {
            String match = matcher.group();
            if (match.startsWith(getCode())) {
                return true;
            }
        }

        return false;
    }

    @NonNull
    String resolve(@NonNull String baseFormatString,
                   @NonNull RecordingUnitIdInfo ruInfo);
}
