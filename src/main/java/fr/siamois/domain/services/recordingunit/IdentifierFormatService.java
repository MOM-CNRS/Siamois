package fr.siamois.domain.services.recordingunit;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.actionunit.ActionUnitParent;
import fr.siamois.domain.models.exceptions.recordingunit.MaxRecordingUnitIdentifierReached;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IdentifierFormatService {

    private final ActionUnitRepository actionUnitRepository;

    private static final String RECORDING_UNIT_NUMBER_CODE = "{NUM_UE}";
    private static final String RECORDING_UNIT_TYPE_CODE = "{TYPE_UE}";
    private static final String RECORDING_UNIT_PARENT_NUMBER_CODE = "{NUM_PARENT}";
    private static final String RECORDING_UNIT_PARENT_TYPE_CODE = "{TYPE_PARENT}";
    private static final String SPATIAL_UNIT_NUMBER_CODE = "{NUM_USPATIAL}";
    private static final String ACTION_UNIT_IDENTIFIER_CODE = "{ID_UA}";

    private final LabelService labelService;
    private final RecordingUnitRepository recordingUnitRepository;

    private @NonNull String numberToStringWithLeadingZeroes(int number) {
        return String.format("%0" + ActionUnitParent.CODE_NUMBER_OF_DIGIT + "d", number);
    }

    private @NonNull String first3LettersConceptInLang(@NonNull Concept concept, @Nullable String lang) {
        if (lang == null) {
            lang = "en";
        }

        String result = labelService.findLabelOf(concept, lang).getLabel();
        if (result.startsWith("[")) {
            result = "";
        }

        return result.substring(0, 3).toUpperCase();
    }

    private @NonNull String applyFormat(@NonNull RecordingUnit recordingUnit, int number, @Nullable RecordingUnit ruParent) {
        ActionUnit parentActionUnit = recordingUnit.getActionUnit();
        String format = parentActionUnit.getRecordingUnitIdentifierFormat();
        if (format == null || format.isEmpty()) {
            return String.valueOf(number);
        }

        if (!format.contains(RECORDING_UNIT_NUMBER_CODE)) {
            throw new IllegalStateException("The " + RECORDING_UNIT_NUMBER_CODE + " is mandatory in the format");
        }
        format = format.replace(RECORDING_UNIT_NUMBER_CODE, numberToStringWithLeadingZeroes(number));

        if (format.contains(RECORDING_UNIT_TYPE_CODE)) {
            format = format.replace(RECORDING_UNIT_TYPE_CODE, first3LettersConceptInLang(recordingUnit.getType(), recordingUnit.getLocalIdentifierLang()));
        }

        if (format.contains(RECORDING_UNIT_PARENT_NUMBER_CODE)) {
            if (ruParent == null) {
                format = format.replace(RECORDING_UNIT_PARENT_NUMBER_CODE, numberToStringWithLeadingZeroes(0));
            } else {
                format = format.replace(RECORDING_UNIT_PARENT_NUMBER_CODE, numberToStringWithLeadingZeroes(ruParent.getLocalIdentifierCode()));
            }
        }

        if (format.contains(RECORDING_UNIT_PARENT_TYPE_CODE)) {
            if (ruParent == null) {
                format = format.replace(RECORDING_UNIT_PARENT_TYPE_CODE, "");
            } else {
                format = format.replace(RECORDING_UNIT_PARENT_TYPE_CODE, first3LettersConceptInLang(parentActionUnit.getType(), recordingUnit.getLocalIdentifierLang()));
            }
        }

        if (format.contains(SPATIAL_UNIT_NUMBER_CODE)) {
            SpatialUnit spatialUnitId = parentActionUnit.getSpatialContext()
                    .stream()
                    .findFirst()
                    .orElse(new SpatialUnit());
            format = format.replace(SPATIAL_UNIT_NUMBER_CODE, numberToStringWithLeadingZeroes(Math.toIntExact(spatialUnitId.getId())));
        }

        if (format.contains(ACTION_UNIT_IDENTIFIER_CODE)) {
            format = format.replace(ACTION_UNIT_IDENTIFIER_CODE, parentActionUnit.getIdentifier());
        }

        return format;
    }

    public @NonNull String generateIdentifier(@NonNull RecordingUnit recordingUnit, @Nullable RecordingUnit recordingUnitParent) {
        ActionUnit parentActionUnit = recordingUnit.getActionUnit();
        int minValue = parentActionUnit.getMinRecordingUnitCode();
        int maxValue = parentActionUnit.getMaxRecordingUnitCode();
        int nextValue = actionUnitRepository.incrementRecordingUnitCodeNextValue(parentActionUnit.getId());

        if (nextValue < minValue || nextValue > maxValue) {
            throw new MaxRecordingUnitIdentifierReached("Max recording unit code reached; Please ask administrator to increase the range");
        }

        if (recordingUnitParent == null) {
            List<RecordingUnit> parents = recordingUnitRepository.findDirectParentsOf(recordingUnit.getId());
            if (!parents.isEmpty()) {
                recordingUnitParent = parents.get(0);
            }
        }

        return applyFormat(recordingUnit, nextValue, recordingUnitParent);
    }

    public @NonNull String generateIdentifier(@NonNull RecordingUnit recordingUnit) {
        return generateIdentifier(recordingUnit, null);
    }

}
