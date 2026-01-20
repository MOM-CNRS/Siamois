package fr.siamois.domain.services.recordingunit.identifier;

import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdInfo;
import fr.siamois.domain.services.recordingunit.identifier.generic.RuTextIdentifierResolver;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitIdLabelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
public class RuTypeResolver extends RuTextIdentifierResolver {

    private final LabelService labelService;

    public RuTypeResolver(LabelService labelService, RecordingUnitIdLabelRepository repository) {
        super(repository);
        this.labelService = labelService;
    }

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
    public String getTitleCode() {
        return "ru.identifier.title.type";
    }

    @NonNull
    @Override
    protected String textValue(@NonNull RecordingUnitIdInfo info) {
        if (info.getRuType() == null
                || info.getActionUnit() == null
                || info.getActionUnit().getRecordingUnitIdentifierLang() == null) {
            return "";
        }
        return labelService
                .findLabelOf(info.getRuType(), info.getActionUnit().getRecordingUnitIdentifierLang())
                .getLabel();
    }

    @Override
    protected boolean infoAreNotValid(@NonNull RecordingUnitIdInfo ruInfo) {
        return ruInfo.getActionUnit() == null
                || ruInfo.getActionUnit().getRecordingUnitIdentifierLang() == null
                || ruInfo.getRuType() == null;
    }

    @NonNull
    @Override
    public String getButtonStyleClass() {
        return "rounded-button ui-button-warning";
    }
}
