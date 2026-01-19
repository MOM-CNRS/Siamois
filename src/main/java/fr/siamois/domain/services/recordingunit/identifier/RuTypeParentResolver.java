package fr.siamois.domain.services.recordingunit.identifier;

import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdInfo;
import fr.siamois.domain.services.recordingunit.identifier.generic.RuTextIdentifierResolver;
import fr.siamois.domain.services.vocabulary.LabelService;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuTypeParentResolver extends RuTextIdentifierResolver {

    private final LabelService labelService;

    @NonNull
    @Override
    public String getCode() {
        return "TYPE_PARENT";
    }

    @Nullable
    @Override
    public String getDescriptionLanguageCode() {
        return "ru.identifier.description.type_parent";
    }

    @NonNull
    @Override
    protected String textValue(@NonNull RecordingUnitIdInfo info) {
        if (info.getRuParentType() == null
                || info.getActionUnit() == null
                || info.getActionUnit().getRecordingUnitIdentifierLang() == null) {
            return "";
        }
        return labelService
                .findLabelOf(info.getRuParentType(), info.getActionUnit().getRecordingUnitIdentifierLang())
                .getLabel();
    }

    @Override
    protected boolean infoAreNotValid(@NonNull RecordingUnitIdInfo info) {
        return info.getRuParentType() == null
                || info.getActionUnit() == null
                || info.getActionUnit().getRecordingUnitIdentifierLang() == null;
    }


}
