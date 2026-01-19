package fr.siamois.domain.services.recordingunit.identifier.generic;

import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdInfo;
import org.springframework.lang.NonNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class RuTextIdentifierResolver implements RuIdentifierResolver {

    private static final int DEFAULT_NUMBER_OF_CHAR = 3;

    @NonNull
    protected abstract String textValue(@NonNull RecordingUnitIdInfo info);

    @NonNull
    @Override
    public String resolve(@NonNull String baseFormatString, @NonNull RecordingUnitIdInfo ruInfo) {
        if (!baseFormatString.contains("{" + getCode())
                || ruInfo.getActionUnit().getRecordingUnitIdentifierLang() == null
                || ruInfo.getRuType() == null) {
            return baseFormatString;
        }

        Pattern pattern = Pattern.compile("\\{" + getCode() + "([^}]*)\\}");
        Matcher matcher = pattern.matcher(baseFormatString);
        StringBuilder result = new StringBuilder();

        String label = textValue(ruInfo);

        while (matcher.find()) {
            String replacement = computeType(matcher, label);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);

        return result.toString();
    }

    @NonNull
    protected static String computeType(@NonNull Matcher matcher, @NonNull String label) {
        String replacement = "";
        String formatSpecifierPart = matcher.group(1);

        if (formatSpecifierPart != null && formatSpecifierPart.startsWith(":") && formatSpecifierPart.substring(1).matches("[X]+")) {
            int width = formatSpecifierPart.length() - 1;
            if (label.length() > width) {
                replacement = label.substring(0, width);
            } else {
                replacement = label;
            }
        } else {
            replacement = label.substring(0, DEFAULT_NUMBER_OF_CHAR);
        }
        replacement = replacement.toUpperCase();
        return replacement;
    }

}
