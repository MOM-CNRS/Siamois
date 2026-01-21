package fr.siamois.domain.services.recordingunit.identifier.generic;

import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdInfo;
import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdLabel;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitIdLabelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public abstract class RuTextIdentifierResolver implements RuIdentifierResolver {

    private static final int DEFAULT_NUMBER_OF_CHAR = 3;
    protected final RecordingUnitIdLabelRepository repository;

    protected final String code;
    protected final String descriptionLanguageCode;
    protected final String titleCode;

    protected RuTextIdentifierResolver(RecordingUnitIdLabelRepository repository,
                                       String code, String descriptionLanguageCode, String titleCode) {
        this.repository = repository;
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

    @NonNull
    protected abstract String textValue(@NonNull RecordingUnitIdInfo info);

    protected abstract boolean infoAreNotValid(@NonNull RecordingUnitIdInfo info);

    @NonNull
    @Override
    public String resolve(@NonNull String baseFormatString, @NonNull RecordingUnitIdInfo ruInfo) {
        if (infoAreNotValid(ruInfo)) {
            log.error("Could not resolve identifier for {} with info {}", baseFormatString, ruInfo);
            return baseFormatString;
        }

        Pattern pattern = Pattern.compile("\\{" + getCode() + "([^}]*)\\}");
        Matcher matcher = pattern.matcher(baseFormatString);
        StringBuilder result = new StringBuilder();

        String label = textValue(ruInfo);

        while (matcher.find()) {
            String replacement = computeType(matcher, label);
            replacement = addNumberIfNecessaryAndSaveExisting(ruInfo, replacement);

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);

        return result.toString();
    }

    private String addNumberIfNecessaryAndSaveExisting(RecordingUnitIdInfo ruInfo, String replacement) {
        Optional<RecordingUnitIdLabel> existing = repository.findByExistingAndActionUnit(replacement, ruInfo.getActionUnit());
        if (existing.isPresent() && !existing.get().getType().equals(ruInfo.getRuType())) {
            int counter = 1;
            String originalReplacement = replacement;
            Pattern numberPattern = Pattern.compile("^([^\\d\\r\\n]+)(\\d+)$");
            Matcher numberMatcher = numberPattern.matcher(replacement);
            if (numberMatcher.find()) {
                originalReplacement = numberMatcher.group(1);
                counter = Integer.parseInt(numberMatcher.group(2)) + 1;
            }

            do {
                replacement = originalReplacement + counter;
                existing = repository.findByExistingAndActionUnit(replacement, ruInfo.getActionUnit());
                counter++;
            } while (existing.isPresent() && !existing.get().getType().equals(ruInfo.getRuType()));

            repository.save(new RecordingUnitIdLabel(ruInfo.getRuType(), ruInfo.getActionUnit(), replacement));
        } else if (existing.isEmpty() && ruInfo.getRuType() != null) {
            repository.save(new RecordingUnitIdLabel(ruInfo.getRuType(), ruInfo.getActionUnit(), replacement));
        }
        return replacement;
    }

    @NonNull
    protected static String computeType(@NonNull Matcher matcher, @NonNull String label) {
        String replacement = "";
        String formatSpecifierPart = matcher.group(1);

        if (formatSpecifierPart != null && formatSpecifierPart.startsWith(":") && formatSpecifierPart.substring(1).matches("X+")) {
            int width = formatSpecifierPart.length() - 1;
            if (label.length() > width) {
                replacement = label.substring(0, width);
            } else {
                replacement = label;
            }
        } else if (label.length() > DEFAULT_NUMBER_OF_CHAR){
            replacement = label.substring(0, DEFAULT_NUMBER_OF_CHAR);
        } else {
            replacement = label;
        }
        replacement = replacement.toUpperCase();
        return replacement;
    }

}
