package fr.siamois.domain.services.recordingunit.identifier;

import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdInfo;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.services.vocabulary.LabelService;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RuTypeResolver implements RuIdentifierResolver {

    private static final int DEFAULT_NUMBER_OF_CHAR = 3;
    private final LabelService labelService;

    @NonNull
    @Override
    public String getCode() {
        return "TYPE_UE";
    }

    @Nullable
    @Override
    public String getDescriptionLanguageCode() {
        return "ru.identifier.description.type";
    }

    @NonNull
    @Override
    public String resolve(@NonNull String baseFormatString, @NonNull RecordingUnitIdInfo ruInfo) {
        if (!baseFormatString.contains("{" + getCode())
                || ruInfo.getActionUnit().getRecordingUnitIdentifierLang() == null
                || ruInfo.getRuType() == null) {
            return "";
        }

        Pattern pattern = Pattern.compile("\\{" + getCode() + "([^}]*)\\}");
        Matcher matcher = pattern.matcher(baseFormatString);
        StringBuilder result = new StringBuilder();

        ConceptLabel label = labelService.findLabelOf(ruInfo.getRuType(), ruInfo.getActionUnit().getRecordingUnitIdentifierLang());

        while (matcher.find()) {
            String replacement = computeType(matcher, label);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);

        return result.toString();
    }

    @NonNull
    private static String computeType(@NonNull Matcher matcher, @NonNull ConceptLabel label) {
        String replacement = "";
        String formatSpecifierPart = matcher.group(1);

        if (formatSpecifierPart != null && formatSpecifierPart.startsWith(":") && formatSpecifierPart.substring(1).matches("[X]+")) {
            int width = formatSpecifierPart.length() - 1;
            if (label.getLabel().length() > width) {
                replacement = label.getLabel().substring(0, width);
            } else {
                replacement = label.getLabel();
            }
        } else {
            replacement = label.getLabel().substring(0, DEFAULT_NUMBER_OF_CHAR);
        }
        replacement = replacement.toUpperCase();
        return replacement;
    }
}
