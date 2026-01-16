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
public class RuNumberParentResolver extends RuNumericalIdentifierResolver {

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

    @Override
    protected int numericalValue(@NonNull RecordingUnitIdInfo info) {
        if (info.getParent() == null) {
            return 0;
        }
        Optional<RecordingUnitIdInfo> opt = recordingUnitIdInfoRepository.findByRecordingUnit(info.getParent());
        return opt.map(RecordingUnitIdInfo::getRuNumber).orElse(0);
    }
}
