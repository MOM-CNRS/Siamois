package fr.siamois.domain.services.recordingunit.identifier;

import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdInfo;
import fr.siamois.domain.services.recordingunit.identifier.generic.RuNumericalIdentifierResolver;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitIdInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RuNumParentResolver extends RuNumericalIdentifierResolver {

    private final RecordingUnitIdInfoRepository recordingUnitIdInfoRepository;

    @NonNull
    @Override
    public String getCode() {
        return "NUM_PARENT";
    }

    @Nullable
    @Override
    public String getDescriptionLanguageCode() {
        return "ru.identifier.description.number_parent";
    }

    @NonNull
    @Override
    public String getTitleCode() {
        return "ru.identifier.title.number_parent";
    }

    @Override
    protected int numericalValue(@NonNull RecordingUnitIdInfo info) {
        if (info.getParent() == null) {
            return 0;
        }
        Optional<RecordingUnitIdInfo> opt = recordingUnitIdInfoRepository.findById(info.getParent().getId());
        return opt.map(RecordingUnitIdInfo::getRuNumber).orElse(0);
    }

    @NonNull
    @Override
    public String getButtonStyleClass() {
        return "rounded-button ui-button-warning";
    }
}
