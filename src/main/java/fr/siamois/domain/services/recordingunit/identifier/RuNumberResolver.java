package fr.siamois.domain.services.recordingunit.identifier;

import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdInfo;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RuNumberResolver implements RuIdentifierResolver {

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
    public String resolve(@NonNull String baseFormatString, @NonNull RecordingUnitIdInfo ruInfo) {
        if (!baseFormatString.contains("{" + CODE)) {
            return String.valueOf(ruInfo.getRuNumber());
        }

        Pattern pattern = Pattern.compile("\\{" + CODE + "([^}]*)\\}");
        Matcher matcher = pattern.matcher(baseFormatString);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String replacement;
            String formatSpecifierPart = matcher.group(1);

            if (formatSpecifierPart != null && formatSpecifierPart.startsWith(":") && formatSpecifierPart.substring(1).matches("[0]+")) {
                int width = formatSpecifierPart.length() - 1;
                replacement = String.format("%0" + width + "d", ruInfo.getRuNumber());
            } else {
                replacement = String.valueOf(ruInfo.getRuNumber());
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);

        return result.toString();
    }
}
