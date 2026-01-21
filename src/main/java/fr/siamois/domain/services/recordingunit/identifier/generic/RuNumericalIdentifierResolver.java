package fr.siamois.domain.services.recordingunit.identifier.generic;

import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdInfo;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class RuNumericalIdentifierResolver implements RuIdentifierResolver {

    protected final String code;
    protected final String descriptionLanguageCode;
    protected final String titleCode;

    protected RuNumericalIdentifierResolver(String code, String descriptionLanguageCode, String titleCode) {
        this.code = code;
        this.descriptionLanguageCode = descriptionLanguageCode;
        this.titleCode = titleCode;
    }

    @NonNull
    @Override
    public String getCode() {
        return code;
    }

    @Nullable
    @Override
    public String getDescriptionLanguageCode() {
        return descriptionLanguageCode;
    }

    @NonNull
    @Override
    public String getTitleCode() {
        return titleCode;
    }

    protected abstract int numericalValue(@NonNull RecordingUnitIdInfo info);

    @NonNull
    @Override
    public String resolve(@NonNull String baseFormatString, @NonNull RecordingUnitIdInfo ruInfo) {
        if (!baseFormatString.contains("{" + getCode())) {
            return String.valueOf(numericalValue(ruInfo));
        }

        Pattern pattern = Pattern.compile("\\{" + getCode() + "([^}]*)}");
        Matcher matcher = pattern.matcher(baseFormatString);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String replacement;
            String formatSpecifierPart = matcher.group(1);

            if (formatSpecifierPart != null && formatSpecifierPart.startsWith(":") && formatSpecifierPart.substring(1).matches("0+")) {
                int width = formatSpecifierPart.length() - 1;
                replacement = String.format("%0" + width + "d", numericalValue(ruInfo));
            } else {
                replacement = String.valueOf(numericalValue(ruInfo));
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);

        return result.toString();
    }

}

